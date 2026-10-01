package pl.aridlin.psychiatrykroles;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterClientCommandsEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.bus.api.SubscribeEvent;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.HashMap;
import java.util.Map;

/** Client-owned schematic files and a non-destructive in-world preview. */
@EventBusSubscriber(modid = PsychiatrykRoles.MOD_ID, value = Dist.CLIENT)
public final class ClientBuildTransfer {
    private static BlockPos first;
    private static BlockPos second;
    private static String sourceDimension;
    private static ListTag preview;
    private static CompoundTag previewRoot;
    private static BlockPos previewAt;
    private static int previewYOffset;
    private static String previewName;
    private static boolean previewCarve;
    private static final int MAX_VOLUME = 65536;

    private ClientBuildTransfer() {}

    @SubscribeEvent
    public static void onMarkerUse(PlayerInteractEvent.RightClickBlock event) {
        if (!event.getLevel().isClientSide() || !event.getEntity().isCreative()) return;
        BlockState state = event.getLevel().getBlockState(event.getPos());
        boolean a = state.is(BuildTransferMarkers.FIRST);
        boolean b = state.is(BuildTransferMarkers.SECOND);
        if (!a && !b) return;
        String dimension = event.getLevel().dimension().location().toString();
        if (!dimension.equals(sourceDimension)) { first = null; second = null; sourceDimension = dimension; }
        if (a) first = event.getPos().immutable();
        if (b) second = event.getPos().immutable();
        event.getEntity().displayClientMessage(Component.literal(
            a ? "First build corner selected" : "Second build corner selected"), true);
    }

    @SubscribeEvent
    public static void onCommands(RegisterClientCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("buildcopy")
            .then(Commands.literal("save")
                .then(Commands.argument("name", StringArgumentType.word())
                    .executes(ctx -> save(StringArgumentType.getString(ctx, "name")))))
            .then(Commands.literal("preview")
                .then(Commands.argument("name", StringArgumentType.word())
                    .executes(ctx -> preview(StringArgumentType.getString(ctx, "name"), false))
                    .then(Commands.literal("carve")
                        .executes(ctx -> preview(StringArgumentType.getString(ctx, "name"), true)))))
            .then(Commands.literal("compare-pristine")
                .then(Commands.argument("name", StringArgumentType.word())
                    .executes(ctx -> comparePristine(StringArgumentType.getString(ctx, "name")))))
            .then(Commands.literal("place").executes(ctx -> place()))
            .then(Commands.literal("clear").executes(ctx -> {
                preview = null; previewRoot = null; previewAt = null; previewCarve = false; return 1;
            })));
    }

    private static int save(String name) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null || !mc.player.isCreative()) return fail("Creative mode required");
        if (!validName(name) || first == null || second == null ||
            !mc.level.dimension().location().toString().equals(sourceDimension)) return fail("Select both corner blocks in this world first");
        BlockPos min = new BlockPos(Math.min(first.getX(), second.getX()),
            Math.min(first.getY(), second.getY()), Math.min(first.getZ(), second.getZ()));
        BlockPos max = new BlockPos(Math.max(first.getX(), second.getX()),
            Math.max(first.getY(), second.getY()), Math.max(first.getZ(), second.getZ()));
        long volume = (long)(max.getX()-min.getX()+1)*(max.getY()-min.getY()+1)*(max.getZ()-min.getZ()+1);
        if (volume > MAX_VOLUME) return fail("Selection exceeds 65,536 blocks");
        ListTag blocks = new ListTag();
        int uncertain = 0;
        for (BlockPos pos : BlockPos.betweenClosed(min, max)) {
            if (!mc.level.hasChunkAt(pos)) return fail("Load the full selection before saving");
            BlockState state = mc.level.getBlockState(pos);
            if (state.isAir() || state.is(BuildTransferMarkers.FIRST) || state.is(BuildTransferMarkers.SECOND)) continue;
            CompoundTag item = new CompoundTag();
            item.putInt("x", pos.getX()-min.getX());
            item.putInt("y", pos.getY()-min.getY());
            item.putInt("z", pos.getZ()-min.getZ());
            item.put("state", NbtUtils.writeBlockState(state));
            // Terrain and trees may be natural. They remain in the local file
            // for review but default to excluded from a paste.
            byte origin = (byte)(looksNatural(state) ? 0 : 1);
            item.putByte("origin", origin);
            uncertain += origin == 0 ? 1 : 0;
            blocks.add(item);
        }
        CompoundTag root = new CompoundTag();
        root.putInt("version", 1);
        root.putString("sourceDimension", sourceDimension);
        root.putInt("sizeX", max.getX()-min.getX()+1);
        root.putInt("sizeY", max.getY()-min.getY()+1);
        root.putInt("sizeZ", max.getZ()-min.getZ()+1);
        root.putInt("sourceMinY", min.getY());
        root.putInt("sourceMinX", min.getX());
        root.putInt("sourceMinZ", min.getZ());
        root.putString("provenance", "heuristic");
        // A lived-in world's heightmap often lands on the roof. Until a
        // pristine comparison is available, anchor the selected bottom to
        // the target ground instead of burying the entire structure.
        root.putInt("sourceGroundMedian", min.getY() - 1);
        root.put("blocks", blocks);
        try {
            Path path = file(name);
            Files.createDirectories(path.getParent());
            NbtIo.writeCompressed(root, path);
            mc.player.displayClientMessage(Component.literal("Saved " + blocks.size() + " blocks locally as " + name
                + "; " + uncertain + " terrain/unknown blocks excluded by default."), false);
            return 1;
        } catch (IOException error) { return fail("Could not save blueprint: " + error.getMessage()); }
    }

    /** Run in a pristine copy of the old world at the same coordinates. */
    private static int comparePristine(String name) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null || !mc.player.isCreative()) return fail("Creative mode required");
        if (!validName(name)) return fail("Invalid blueprint name");
        try {
            Path path = file(name);
            CompoundTag root = NbtIo.readCompressed(path, NbtAccounter.create(32L * 1024 * 1024));
            if (root.getInt("version") != 1 || !root.contains("sourceMinX") || !root.contains("sourceMinZ"))
                return fail("Blueprint lacks source coordinates; save it again in the old world");
            if (!root.getString("sourceDimension").equals(mc.level.dimension().location().toString()))
                return fail("Open the matching dimension in a pristine copy of the old world");
            int sx = root.getInt("sizeX"), sy = root.getInt("sizeY"), sz = root.getInt("sizeZ");
            if (sx < 1 || sy < 1 || sz < 1 || (long) sx * sy * sz > MAX_VOLUME)
                return fail("Invalid blueprint size");
            int x0 = root.getInt("sourceMinX"), y0 = root.getInt("sourceMinY"), z0 = root.getInt("sourceMinZ");
            List<Integer> pristineGround = new ArrayList<>(sx * sz);
            ListTag saved = root.getList("blocks", Tag.TAG_COMPOUND);
            Map<Long, CompoundTag> current = new HashMap<>();
            for (int i = 0; i < saved.size(); i++) {
                CompoundTag entry = saved.getCompound(i);
                int x = entry.getInt("x"), y = entry.getInt("y"), z = entry.getInt("z");
                if (x < 0 || x >= sx || y < 0 || y >= sy || z < 0 || z >= sz)
                    return fail("Invalid block offset in blueprint");
                current.put(BlockPos.asLong(x, y, z), entry);
            }
            ListTag compared = new ListTag();
            int added = 0, removed = 0;
            for (int x = 0; x < sx; x++) for (int z = 0; z < sz; z++) {
                if (!mc.level.hasChunkAt(new BlockPos(x0 + x, y0, z0 + z)))
                    return fail("Load the entire pristine selection before comparing");
                pristineGround.add(mc.level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                    x0 + x, z0 + z) - 1);
                for (int y = 0; y < sy; y++) {
                    BlockState before = mc.level.getBlockState(new BlockPos(x0 + x, y0 + y, z0 + z));
                    CompoundTag entry = current.get(BlockPos.asLong(x, y, z));
                    BlockState after = entry == null ? Blocks.AIR.defaultBlockState() :
                        NbtUtils.readBlockState(mc.level.registryAccess().lookupOrThrow(net.minecraft.core.registries.Registries.BLOCK), entry.getCompound("state"));
                    if (before.equals(after)) continue;
                    if (entry == null) {
                        entry = new CompoundTag();
                        entry.putInt("x", x); entry.putInt("y", y); entry.putInt("z", z);
                        entry.put("state", NbtUtils.writeBlockState(Blocks.AIR.defaultBlockState()));
                    }
                    entry.putByte("origin", (byte) (after.isAir() ? 2 : 1));
                    compared.add(entry);
                    if (after.isAir()) removed++; else added++;
                }
            }
            root.put("blocks", compared);
            root.putString("provenance", "pristine-diff");
            Collections.sort(pristineGround);
            root.putInt("sourceGroundMedian", pristineGround.get(pristineGround.size() / 2));
            // The original capture stays intact until every pristine sample is checked.
            Path temporary = path.resolveSibling(path.getFileName() + ".tmp");
            NbtIo.writeCompressed(root, temporary);
            Files.move(temporary, path, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            mc.player.displayClientMessage(Component.literal("Compared with pristine world: " + added
                + " changed solid blocks, " + removed + " removed blocks. Excavated air can be included with /buildcopy preview "
                + name + " carve. "
                + "Use the exact old seed and worldgen mods for this comparison."), false);
            return 1;
        } catch (IOException error) { return fail("Could not compare blueprint: " + error.getMessage()); }
    }

    private static int preview(String name, boolean carve) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null || !mc.player.isCreative()) return fail("Creative mode required");
        if (!validName(name)) return fail("Invalid blueprint name");
        try {
            CompoundTag root = NbtIo.readCompressed(file(name), NbtAccounter.create(8L * 1024 * 1024));
            if (root.getInt("version") != 1) return fail("Unsupported blueprint version");
            if (carve && !root.getString("provenance").equals("pristine-diff"))
                return fail("Carving requires a blueprint compared with a pristine world first");
            ListTag blocks = root.getList("blocks", Tag.TAG_COMPOUND);
            if (blocks.size() > MAX_VOLUME) return fail("Blueprint too large");
            int sx = root.getInt("sizeX"), sy = root.getInt("sizeY"), sz = root.getInt("sizeZ");
            if (sx < 1 || sy < 1 || sz < 1 || sx > 96 || sy > 96 || sz > 96
                || (long) sx * sy * sz > MAX_VOLUME) return fail("Invalid blueprint size");
            BlockPos anchor;
            if (mc.hitResult instanceof BlockHitResult hit) {
                BlockPos lookedAt = hit.getBlockPos();
                anchor = mc.level.getBlockState(lookedAt).is(BuildTransferMarkers.FIRST)
                    ? lookedAt : lookedAt.above();
            } else anchor = mc.player.blockPosition();
            List<Integer> destinationGround = new ArrayList<>();
            for (int x = 0; x < sx; x++) {
                for (int z = 0; z < sz; z++) {
                    int worldX = anchor.getX()+x, worldZ = anchor.getZ()+z;
                    if (!mc.level.hasChunkAt(new BlockPos(worldX, anchor.getY(), worldZ)))
                        return fail("Load the whole destination before previewing");
                    destinationGround.add(mc.level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                        worldX, worldZ)-1);
                }
            }
            Collections.sort(destinationGround);
            previewYOffset = root.getInt("sourceMinY") +
                destinationGround.get(destinationGround.size()/2) - root.getInt("sourceGroundMedian") - anchor.getY();
            preview = blocks;
            previewRoot = root;
            previewName = name;
            previewAt = anchor;
            previewCarve = carve;
            mc.player.displayClientMessage(Component.literal("Previewing " + name + " at " + previewAt
                + ". Green: placed; amber: uncertain terrain; red: removed terrain "
                + (carve ? "(WILL BE CARVED). " : "(not pasted; add 'carve' to preview command to include). ")
                + (root.getString("provenance").equals("pristine-diff") ? "Compared with supplied pristine world." : "Heuristic classification only.")), false);
            return 1;
        } catch (IOException error) { return fail("Could not read blueprint: " + error.getMessage()); }
    }

    private static int place() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null || !mc.player.isCreative()) return fail("Creative mode required");
        if (previewRoot == null || previewAt == null || !mc.level.getBlockState(previewAt).is(BuildTransferMarkers.FIRST))
            return fail("Place the first corner block at the preview anchor before pasting");
        try {
            BuildTransferNetwork.send(previewRoot, previewAt, previewCarve);
            mc.player.displayClientMessage(Component.literal("Sent " + previewName + " to the server for validation and "
                + (previewCarve ? "carving/placement" : "placement")), false);
            return 1;
        } catch (IOException error) { return fail("Could not send blueprint: " + error.getMessage()); }
    }

    @SubscribeEvent
    public static void onRender(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS || preview == null || previewAt == null) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || !mc.player.isCreative()) return;
        Vec3 camera = event.getCamera().getPosition();
        PoseStack pose = event.getPoseStack();
        pose.pushPose();
        pose.translate(-camera.x, -camera.y, -camera.z);
        var source = mc.renderBuffers().bufferSource();
        VertexConsumer lines = source.getBuffer(RenderType.lines());
        int stride = Math.max(1, preview.size() / 2400);
        for (int i = 0; i < preview.size(); i += stride) {
            CompoundTag block = preview.getCompound(i);
            int x = previewAt.getX() + block.getInt("x");
            int y = previewAt.getY() + block.getInt("y") + previewYOffset;
            int z = previewAt.getZ() + block.getInt("z");
            byte origin = block.getByte("origin");
            boolean included = origin == 1 || origin == 2 && previewCarve;
            LevelRenderer.renderLineBox(pose, lines, x, y, z, x+1, y+1, z+1,
                origin == 2 ? 1.0F : included ? 0.25F : 1.0F,
                origin == 2 ? 0.2F : included ? 1.0F : 0.65F, 0.2F, 0.55F);
        }
        source.endBatch(RenderType.lines());
        pose.popPose();
    }

    private static boolean looksNatural(BlockState state) {
        return state.is(BlockTags.DIRT) || state.is(BlockTags.LOGS) || state.is(BlockTags.LEAVES)
            || state.is(Blocks.STONE) || state.is(Blocks.DEEPSLATE) || state.is(Blocks.GRAVEL)
            || state.is(Blocks.SAND) || state.is(Blocks.RED_SAND) || state.is(Blocks.SNOW_BLOCK)
            || state.is(Blocks.ICE) || state.is(Blocks.PACKED_ICE) || state.is(Blocks.WATER)
            || state.is(Blocks.LAVA) || state.is(Blocks.CALCITE) || state.is(Blocks.TUFF);
    }

    private static Path file(String name) {
        return Minecraft.getInstance().gameDirectory.toPath().resolve("psychiatryk-blueprints").resolve(name + ".nbt");
    }
    private static boolean validName(String name) { return name.matches("[A-Za-z0-9_-]{1,40}"); }
    private static int fail(String message) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null) mc.player.displayClientMessage(Component.literal(message), false);
        return 0;
    }
}
