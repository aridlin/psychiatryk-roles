package pl.aridlin.psychiatrykroles;

import com.mojang.math.Transformation;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.TagParser;
import net.minecraft.network.chat.Component;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.AnvilUpdateEvent;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.common.world.ForgeChunkManager;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.HexFormat;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.nio.charset.StandardCharsets;
import org.joml.Quaternionf;
import org.joml.Vector3f;

public final class VoidDoors {
    private static final String MARKER = "psychiatrykVoidDoor";
    private static final String PAIR = "psychiatrykVoidDoorPair";
    private static final String PLANE = "psychiatrykVoidPlane";
    private static final String CODE = "psychiatrykVoidDoorCode";
    private final Map<UUID, PendingPlacement> pending = new HashMap<>();
    private final List<Placement> placements = new ArrayList<>();
    private final Map<UUID, Sample> previous = new HashMap<>();
    private final Map<VoidDoorData.DoorPosition, List<Display>> planes = new HashMap<>();
    private final Set<VoidDoorData.DoorPosition> ticketed = new HashSet<>();
    private final Map<UUID, Boolean> lastOpen = new HashMap<>();
    private final Set<UUID> unlocked = new HashSet<>();

    private record PendingPlacement(UUID pair, String code, int tick, VoidDoorData.DoorPosition position) {}
    private record Placement(PendingPlacement pending, BlockEvent.EntityPlaceEvent event) {}
    private record Sample(String dimension, Vec3 position) {}

    public static boolean isVoidDoor(ItemStack stack) {
        return (stack.is(Items.DARK_OAK_DOOR) || stack.is(Items.OAK_DOOR))
            && stack.hasTag() && stack.getTag().getBoolean(MARKER);
    }

    public static void assignCraftedPair(ItemStack stack) {
        if (isVoidDoor(stack)) stack.getOrCreateTag().putString(PAIR, UUID.randomUUID().toString());
    }

    static UUID pair(ItemStack stack) {
        if (!isVoidDoor(stack)) return null;
        try { return UUID.fromString(stack.getTag().getString(PAIR)); }
        catch (IllegalArgumentException ignored) { return null; }
    }

    static String codeHash(UUID pair, String code) {
        try {
            byte[] bytes = MessageDigest.getInstance("SHA-256").digest(
                (pair + ":" + code).getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(bytes);
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException(impossible);
        }
    }

    @SubscribeEvent
    public void onAnvilUpdate(AnvilUpdateEvent event) {
        ItemStack left = event.getLeft();
        UUID pair = pair(left);
        if (pair == null || !event.getRight().isEmpty()) return;
        String name = event.getName().strip();
        if (name.length() > 50) return;
        ItemStack output = left.copy();
        if (name.isEmpty() || name.equals("Void Doors") || name.equals("Drzwi Pustki")) {
            output.getOrCreateTag().remove(CODE);
        } else {
            output.getOrCreateTag().putString(CODE, codeHash(pair, name));
        }
        localize(output, PsychiatrykRoles.isEnglish(event.getPlayer()));
        event.setOutput(output);
        event.setCost(1);
    }

    public static void localize(ItemStack stack, boolean english) {
        stack.setHoverName(Component.literal(english ? "Void Doors" : "Drzwi Pustki")
            .withStyle(ChatFormatting.DARK_PURPLE));
        ListTag lore = new ListTag();
        for (String line : english ? new String[] {
            "A linked pair, even across dimensions.", "Open either door and cross its black plane.",
            "Mining and replacing a door keeps its link."
        } : new String[] { "Połączona para, także między wymiarami.",
            "Otwórz dowolne drzwi i przejdź przez czarną płaszczyznę.", "Wykopanie i postawienie zachowuje połączenie." }) {
            lore.add(StringTag.valueOf(Component.Serializer.toJson(Component.literal(line).withStyle(ChatFormatting.GRAY))));
        }
        stack.getOrCreateTagElement("display").put("Lore", lore);
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void onDoorUse(PlayerInteractEvent.RightClickBlock event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        pending.remove(player.getUUID());
        BlockPos clicked = event.getPos();
        BlockState clickedState = player.serverLevel().getBlockState(clicked);
        if (isDoor(clickedState)) {
            BlockPos lower = lowerPos(clicked, clickedState);
            VoidDoorData data = VoidDoorData.get(player.getServer());
            UUID linked = data.pairAt(player.level().dimension().location().toString(), lower);
            if (linked != null && !data.code(linked).isEmpty() && !clickedState.getValue(DoorBlock.OPEN)) {
                event.setCanceled(true);
                player.openMenu(new SimpleMenuProvider((id, inventory, owner) ->
                    new VoidDoorCodeMenu(id, inventory, this, player.serverLevel(), lower, linked),
                    Component.literal(PsychiatrykRoles.isEnglish(player) ? "Void Door code" : "Kod Drzwi Pustki")));
                return;
            }
        }
        ItemStack stack = event.getItemStack();
        if (!isVoidDoor(stack)) return;
        // Repair old unassigned recipe output as a pair before either item is placed.
        if (pair(stack) == null) {
            if (stack.getCount() > 2) {
                event.setCanceled(true);
                return;
            }
            assignCraftedPair(stack);
        }
        BlockPlaceContext context = new BlockPlaceContext(player, event.getHand(), stack, event.getHitVec());
        pending.put(player.getUUID(), new PendingPlacement(pair(stack), stack.getOrCreateTag().getString(CODE),
            player.getServer().getTickCount(),
            new VoidDoorData.DoorPosition(player.level().dimension().location().toString(), context.getClickedPos())));
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void onPlace(BlockEvent.EntityPlaceEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || !(event.getLevel() instanceof ServerLevel level)) return;
        PendingPlacement attempt = pending.remove(player.getUUID());
        if (attempt == null || attempt.tick() != player.getServer().getTickCount()) return;
        BlockPos lower = attempt.position().pos();
        if (!isDoor(level.getBlockState(lower)) || !lowerPos(event.getPos(), event.getPlacedBlock()).equals(lower)) return;
        if (!VoidDoorData.get(player.getServer()).canPlace(attempt.pair(), attempt.code())) {
            event.setCanceled(true); // Forge rolls the blocks and consumed item back.
            return;
        }
        // A later event listener can still cancel. Commit only after Forge has finished the transaction.
        placements.add(new Placement(attempt, event));
    }

    public static void preserveDropLink(BlockState state, LootParams.Builder params, List<ItemStack> drops) {
        if (!isDoor(state)) return;
        Vec3 origin = params.getOptionalParameter(LootContextParams.ORIGIN);
        if (origin == null) return;
        UUID pair = VoidDoorData.get(params.getLevel().getServer()).pairAt(
            params.getLevel().dimension().location().toString(), lowerPos(BlockPos.containing(origin), state));
        if (pair == null) return;
        // Keep vanilla loot conditions (lower half, explosions, doTileDrops); only decorate actual drops.
        for (int i = 0; i < drops.size(); i++) {
            ItemStack drop = drops.get(i);
            if (drop.is(Items.OAK_DOOR) || drop.is(Items.DARK_OAK_DOOR)) {
                ItemStack linked = new ItemStack(Items.DARK_OAK_DOOR, drop.getCount());
                linked.getOrCreateTag().putBoolean(MARKER, true);
                linked.getOrCreateTag().putString(PAIR, pair.toString());
                String code = VoidDoorData.get(params.getLevel().getServer()).code(pair);
                if (!code.isEmpty()) linked.getOrCreateTag().putString(CODE, code);
                drops.set(i, linked);
            }
        }
    }

    @SubscribeEvent
    public void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        var server = event.getServer();
        VoidDoorData data = VoidDoorData.get(server);
        reconcileTickets(server, data.positions());
        // Remove destroyed endpoints only after vanilla has produced their tagged loot.
        for (var position : data.positions()) {
            ServerLevel level = level(server, position.dimension());
            if (level != null && level.hasChunkAt(position.pos()) && !completeDoor(level, position.pos())) {
                data.removeDoor(position.dimension(), position.pos());
            }
        }
        for (Placement placement : placements) {
            var attempt = placement.pending();
            ServerLevel level = level(server, attempt.position().dimension());
            if (!placement.event().isCanceled() && level != null && completeDoor(level, attempt.position().pos())) {
                data.addDoor(attempt.pair(), attempt.position());
                data.setCode(attempt.pair(), attempt.code());
            }
        }
        placements.clear();
        reconcileTickets(server, data.positions());
        synchronizePairs(server, data);
        pending.entrySet().removeIf(entry -> entry.getValue().tick() < server.getTickCount());
        var active = new java.util.HashSet<VoidDoorData.DoorPosition>();
        for (var position : data.positions()) {
            ServerLevel level = level(server, position.dimension());
            if (level == null || !level.hasChunkAt(position.pos()) || !completeDoor(level, position.pos())) continue;
            migrateOak(level, position.pos());
            // The display sits inside the closed leaf, so keeping it alive avoids a
            // visible spawn delay when the player opens the door.
            active.add(position);
            List<Display> current = planes.get(position);
            if (current == null || current.stream().anyMatch(entity -> entity.isRemoved())) {
                removePlane(position);
                planes.put(position, createPlane(level, position.pos()));
            }
            if (level.getBlockState(position.pos()).getValue(DoorBlock.OPEN) && server.getTickCount() % 5 == 0) {
                Direction facing = level.getBlockState(position.pos()).getValue(DoorBlock.FACING);
                Vec3 center = VoidDoorGeometry.center(position.pos(), facing);
                level.sendParticles(ParticleTypes.REVERSE_PORTAL, center.x, center.y + 1, center.z,
                    2, facing.getStepZ() * .18, .7, facing.getStepX() * .18, 0);
                if (server.getTickCount() % 20 == 0)
                    level.sendParticles(ParticleTypes.END_ROD, center.x, center.y + 1, center.z,
                        1, facing.getStepZ() * .16, .6, facing.getStepX() * .16, 0);
            }
        }
        for (var position : List.copyOf(planes.keySet())) if (!active.contains(position)) removePlane(position);
    }

    @SubscribeEvent
    public void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !(event.player instanceof ServerPlayer player)) return;
        String dimension = player.level().dimension().location().toString();
        Sample from = previous.put(player.getUUID(), new Sample(dimension, player.position()));
        if (from == null || !from.dimension().equals(dimension)
            || player.isSpectator() || player.isPassenger() || player.isSleeping() || !player.isAlive()) return;
        VoidDoorData data = VoidDoorData.get(player.getServer());
        for (var source : data.positions()) {
            if (!source.dimension().equals(dimension) || source.pos().distToCenterSqr(player.position()) > 36) continue;
            UUID sourcePair = data.pairAt(dimension, source.pos());
            if (sourcePair != null && !data.code(sourcePair).isEmpty() && !unlocked.contains(sourcePair)) continue;
            ServerLevel sourceLevel = player.serverLevel();
            if (!completeDoor(sourceLevel, source.pos())) continue;
            BlockState sourceState = sourceLevel.getBlockState(source.pos());
            if (!sourceState.getValue(DoorBlock.OPEN) || !VoidDoorGeometry.touches(source.pos(),
                sourceState.getValue(DoorBlock.FACING), player.getBoundingBox())) continue;
            var destination = data.partner(dimension, source.pos());
            if (destination == null) continue;
            ServerLevel target = level(player.getServer(), destination.dimension());
            if (target == null) continue;
            target.getChunkAt(destination.pos()); // Remote dimension/chunk may have no players.
            if (!completeDoor(target, destination.pos())) continue;
            BlockState targetState = target.getBlockState(destination.pos());
            Direction facing = targetState.getValue(DoorBlock.FACING);
            Vec3 exit = safeExit(target, player, destination.pos(), facing);
            if (exit == null) {
                player.displayClientMessage(Component.literal(PsychiatrykRoles.isEnglish(player)
                    ? "No safe landing beside the linked Void Door." : "Brak bezpiecznego miejsca przy połączonych Drzwiach Pustki."), true);
                continue;
            }
            double side = (exit.x - destination.pos().getX() - .5) * facing.getStepX()
                + (exit.z - destination.pos().getZ() - .5) * facing.getStepZ();
            Direction exitSide = side > 0 ? facing : facing.getOpposite();
            Vec3 movement = player.position().subtract(from.position());
            boolean backwards = player.getLookAngle().x * movement.x + player.getLookAngle().z * movement.z < 0;
            Direction look = backwards ? exitSide.getOpposite() : exitSide;
            player.teleportTo(target, exit.x, exit.y, exit.z, look.toYRot(), player.getXRot());
            previous.put(player.getUUID(), new Sample(destination.dimension(), exit));
            return;
        }
    }

    @SubscribeEvent
    public void onLevelTick(TickEvent.LevelTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !(event.level instanceof ServerLevel sourceLevel)) return;
        String dimension = sourceLevel.dimension().location().toString();
        VoidDoorData data = VoidDoorData.get(sourceLevel.getServer());
        for (var source : data.positions()) {
            if (!source.dimension().equals(dimension) || !completeDoor(sourceLevel, source.pos())) continue;
            UUID sourcePair = data.pairAt(dimension, source.pos());
            if (sourcePair != null && !data.code(sourcePair).isEmpty() && !unlocked.contains(sourcePair)) continue;
            BlockState state = sourceLevel.getBlockState(source.pos());
            if (!state.getValue(DoorBlock.OPEN)) continue;
            var destination = data.partner(dimension, source.pos());
            if (destination == null) continue;
            ServerLevel target = level(sourceLevel.getServer(), destination.dimension());
            if (target == null) continue;
            target.getChunkAt(destination.pos());
            if (!completeDoor(target, destination.pos())) continue;
            Direction facing = target.getBlockState(destination.pos()).getValue(DoorBlock.FACING);
            Vec3 center = VoidDoorGeometry.center(source.pos(), state.getValue(DoorBlock.FACING));
            for (ItemEntity item : sourceLevel.getEntitiesOfClass(ItemEntity.class,
                new AABB(center.x - 1, center.y, center.z - 1, center.x + 1, center.y + 2, center.z + 1))) {
                if (!VoidDoorGeometry.touches(source.pos(), state.getValue(DoorBlock.FACING), item.getBoundingBox())) continue;
                Vec3 exit = safeExit(target, item, destination.pos(), facing);
                if (exit != null) item.teleportTo(target, exit.x, exit.y, exit.z, Set.of(), item.getYRot(), item.getXRot());
            }
        }
    }

    private static ServerLevel level(net.minecraft.server.MinecraftServer server, String dimension) {
        ResourceLocation id = ResourceLocation.tryParse(dimension);
        return id == null ? null : server.getLevel(ResourceKey.create(Registries.DIMENSION, id));
    }

    private void reconcileTickets(net.minecraft.server.MinecraftServer server, List<VoidDoorData.DoorPosition> positions) {
        Set<VoidDoorData.DoorPosition> wanted = new HashSet<>(positions);
        for (var position : wanted) {
            if (ticketed.add(position)) changeTicket(server, position, true);
        }
        for (var position : List.copyOf(ticketed)) {
            if (!wanted.contains(position)) {
                changeTicket(server, position, false);
                ticketed.remove(position);
            }
        }
    }

    private static void changeTicket(net.minecraft.server.MinecraftServer server,
                                     VoidDoorData.DoorPosition position, boolean add) {
        ServerLevel level = level(server, position.dimension());
        if (level != null) ForgeChunkManager.forceChunk(level, PsychiatrykRoles.MOD_ID, position.pos(),
            position.pos().getX() >> 4, position.pos().getZ() >> 4, add, false);
    }

    private void synchronizePairs(net.minecraft.server.MinecraftServer server, VoidDoorData data) {
        var pairs = data.pairs();
        lastOpen.keySet().retainAll(pairs.keySet());
        unlocked.retainAll(pairs.keySet());
        for (var entry : pairs.entrySet()) {
            List<VoidDoorData.DoorPosition> positions = entry.getValue();
            if (positions.size() == 1) {
                var only = positions.get(0);
                ServerLevel onlyLevel = level(server, only.dimension());
                if (onlyLevel != null && completeDoor(onlyLevel, only.pos())
                    && !data.code(entry.getKey()).isEmpty() && !unlocked.contains(entry.getKey())
                    && onlyLevel.getBlockState(only.pos()).getValue(DoorBlock.OPEN))
                    setOpen(onlyLevel, only.pos(), false);
                continue;
            }
            if (positions.size() != 2) continue;
            ServerLevel aLevel = level(server, positions.get(0).dimension());
            ServerLevel bLevel = level(server, positions.get(1).dimension());
            if (aLevel == null || bLevel == null || !completeDoor(aLevel, positions.get(0).pos())
                || !completeDoor(bLevel, positions.get(1).pos())) continue;
            BlockState a = aLevel.getBlockState(positions.get(0).pos());
            BlockState b = bLevel.getBlockState(positions.get(1).pos());
            boolean aOpen = a.getValue(DoorBlock.OPEN), bOpen = b.getValue(DoorBlock.OPEN);
            if (!data.code(entry.getKey()).isEmpty() && !unlocked.contains(entry.getKey())) {
                if (aOpen) setOpen(aLevel, positions.get(0).pos(), false);
                if (bOpen) setOpen(bLevel, positions.get(1).pos(), false);
                lastOpen.put(entry.getKey(), false);
                continue;
            }
            Boolean previous = lastOpen.get(entry.getKey());
            boolean desired = aOpen == bOpen ? aOpen : previous != null && aOpen == previous ? bOpen : aOpen;
            if (aOpen != desired) setOpen(aLevel, positions.get(0).pos(), desired);
            if (bOpen != desired) setOpen(bLevel, positions.get(1).pos(), desired);
            lastOpen.put(entry.getKey(), desired);
            if (!desired) unlocked.remove(entry.getKey());
        }
    }

    void unlock(ServerPlayer player, ServerLevel level, BlockPos lower, UUID pair, String attempt) {
        VoidDoorData data = VoidDoorData.get(player.getServer());
        if (!pair.equals(data.pairAt(level.dimension().location().toString(), lower))
            || !codeHash(pair, attempt).equals(data.code(pair))) {
            player.displayClientMessage(Component.literal(PsychiatrykRoles.isEnglish(player)
                ? "Incorrect Void Door code." : "Nieprawidłowy kod Drzwi Pustki."), true);
            return;
        }
        unlocked.add(pair);
        setOpen(level, lower, true);
        VoidDoorData.DoorPosition partner = data.partner(level.dimension().location().toString(), lower);
        if (partner != null) {
            ServerLevel other = level(player.getServer(), partner.dimension());
            if (other != null && completeDoor(other, partner.pos())) setOpen(other, partner.pos(), true);
        }
        lastOpen.put(pair, true);
    }

    private static void setOpen(ServerLevel level, BlockPos lower, boolean open) {
        for (BlockPos pos : List.of(lower, lower.above())) {
            BlockState state = level.getBlockState(pos);
            if (state.getValue(DoorBlock.OPEN) != open) level.setBlock(pos, state.setValue(DoorBlock.OPEN, open), 2);
        }
        level.playSound(null, lower, open ? net.minecraft.sounds.SoundEvents.WOODEN_DOOR_OPEN
            : net.minecraft.sounds.SoundEvents.WOODEN_DOOR_CLOSE, net.minecraft.sounds.SoundSource.BLOCKS, 1, 1);
    }

    private static boolean isDoor(BlockState state) {
        return state.is(Blocks.DARK_OAK_DOOR) || state.is(Blocks.OAK_DOOR);
    }

    private static BlockPos lowerPos(BlockPos pos, BlockState state) {
        return isDoor(state) && state.getValue(DoorBlock.HALF) == DoubleBlockHalf.UPPER ? pos.below() : pos;
    }

    private static boolean completeDoor(ServerLevel level, BlockPos pos) {
        BlockState lower = level.getBlockState(pos), upper = level.getBlockState(pos.above());
        return isDoor(lower) && upper.is(lower.getBlock()) && lower.getValue(DoorBlock.HALF) == DoubleBlockHalf.LOWER
            && upper.getValue(DoorBlock.HALF) == DoubleBlockHalf.UPPER;
    }

    static Vec3 safeExit(ServerLevel target, Entity traveler, BlockPos door, Direction facing) {
        return VoidDoorGeometry.firstClearExit(door, facing, exit -> {
            BlockPos floor = BlockPos.containing(exit.x, exit.y - 0.1, exit.z);
            if (target.getBlockState(floor).getCollisionShape(target, floor).isEmpty()) return false;
            return target.noCollision(traveler, traveler.getBoundingBox().move(exit.subtract(traveler.position())));
        });
    }

    private static void migrateOak(ServerLevel level, BlockPos lower) {
        for (BlockPos pos : List.of(lower, lower.above())) {
            BlockState old = level.getBlockState(pos);
            if (old.is(Blocks.OAK_DOOR)) level.setBlock(pos, Blocks.DARK_OAK_DOOR.withPropertiesOf(old), 2);
        }
    }

    private static List<Display> createPlane(ServerLevel level, BlockPos lower) {
        Direction facing = level.getBlockState(lower).getValue(DoorBlock.FACING);
        Vec3 center = VoidDoorGeometry.center(lower, facing);
        List<Display> result = new ArrayList<>();
        // Opaque black text backgrounds are untextured, unlike black concrete. Back-to-back
        // quads make a pure black plane from both sides, hidden inside the closed leaf.
        for (int side = 0; side < 2; side++) {
            Display.TextDisplay display = new Display.TextDisplay(EntityType.TEXT_DISPLAY, level);
            CompoundTag tag;
            try {
                tag = TagParser.parseTag("{text:'{\"text\":\" \"}',background:-16777216,text_opacity:0b,"
                    + "billboard:\"fixed\",see_through:0b,default_background:0b,shadow:0b,"
                    + "width:2f,height:4f,view_range:1f}");
            } catch (com.mojang.brigadier.exceptions.CommandSyntaxException impossible) {
                throw new IllegalStateException(impossible);
            }
            // Let Mojang's codec produce the exact NBT representation expected by Display.
            // Handwritten quaternion lists were rejected during real Forge startup.
            tag.put("transformation", Transformation.EXTENDED_CODEC.encodeStart(NbtOps.INSTANCE,
                new Transformation(new Vector3f(-.1f, 0, 0), new Quaternionf(),
                    new Vector3f(8f, 7.2727275f, 1f), new Quaternionf())).result().orElseThrow());
            display.load(tag);
            display.setPos(center.x, center.y, center.z);
            display.setYRot(facing.toYRot() + side * 180);
            display.setInvulnerable(true);
            display.setNoGravity(true);
            display.addTag(PLANE);
            level.addFreshEntity(display);
            result.add(display);
        }
        // Three narrow beams sit in the same thin slice as the plane. The closed
        // dark-oak leaf occludes them; opening reveals the full frame at once.
        addFrameBeam(level, center, facing, new Vector3f(-.49f, .03f, -.035f),
            new Vector3f(.075f, 1.94f, .07f), result);
        addFrameBeam(level, center, facing, new Vector3f(.415f, .03f, -.035f),
            new Vector3f(.075f, 1.94f, .07f), result);
        addFrameBeam(level, center, facing, new Vector3f(-.49f, 1.89f, -.035f),
            new Vector3f(.98f, .08f, .07f), result);
        return result;
    }

    private static void addFrameBeam(ServerLevel level, Vec3 center, Direction facing,
                                     Vector3f offset, Vector3f size, List<Display> result) {
        Display.BlockDisplay beam = new Display.BlockDisplay(EntityType.BLOCK_DISPLAY, level);
        CompoundTag tag = new CompoundTag();
        tag.put("block_state", NbtUtils.writeBlockState(Blocks.DARK_OAK_PLANKS.defaultBlockState()));
        tag.put("transformation", Transformation.EXTENDED_CODEC.encodeStart(NbtOps.INSTANCE,
            new Transformation(offset, new Quaternionf(), size, new Quaternionf())).result().orElseThrow());
        beam.load(tag);
        beam.setPos(center.x, center.y, center.z);
        beam.setYRot(facing.toYRot());
        beam.setInvulnerable(true);
        beam.setNoGravity(true);
        beam.addTag(PLANE);
        level.addFreshEntity(beam);
        result.add(beam);
    }

    @SubscribeEvent
    public void onEntityLoad(EntityJoinLevelEvent event) {
        // Displays are derived from saved door data, never restore stale/duplicate visuals.
        if (event.loadedFromDisk() && event.getEntity().getTags().contains(PLANE)) event.setCanceled(true);
    }

    private void removePlane(VoidDoorData.DoorPosition position) {
        var old = planes.remove(position);
        if (old != null) old.forEach(Display::discard);
    }

    @SubscribeEvent
    public void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        UUID id = event.getEntity().getUUID();
        pending.remove(id);
        previous.remove(id);
    }

    @SubscribeEvent
    public void onStop(ServerStoppedEvent event) {
        pending.clear(); placements.clear(); previous.clear(); planes.clear(); ticketed.clear(); lastOpen.clear(); unlocked.clear();
    }
}
