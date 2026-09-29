package pl.aridlin.psychiatrykroles;

import com.mojang.math.Transformation;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.TagParser;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
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
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.joml.Quaternionf;
import org.joml.Vector3f;

public final class VoidDoors {
    private static final String MARKER = "psychiatrykVoidDoor";
    private static final String PAIR = "psychiatrykVoidDoorPair";
    private static final String PLANE = "psychiatrykVoidPlane";
    private final Map<UUID, PendingPlacement> pending = new HashMap<>();
    private final List<Placement> placements = new ArrayList<>();
    private final Map<UUID, Long> cooldown = new HashMap<>();
    private final Map<UUID, Sample> previous = new HashMap<>();
    private final Map<VoidDoorData.DoorPosition, List<Display.TextDisplay>> planes = new HashMap<>();

    private record PendingPlacement(UUID pair, int tick, VoidDoorData.DoorPosition position) {}
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

    public static void localize(ItemStack stack, boolean english) {
        stack.setHoverName(Component.literal(english ? "Void Doors" : "Drzwi Pustki")
            .withStyle(ChatFormatting.DARK_PURPLE));
        ListTag lore = new ListTag();
        for (String line : english ? new String[] {
            "A linked pair, even across dimensions.", "Open both doors and cross the black plane.",
            "Mining and replacing a door keeps its link."
        } : new String[] { "Połączona para, także między wymiarami.",
            "Otwórz oboje drzwi i przejdź przez czarną płaszczyznę.", "Wykopanie i postawienie zachowuje połączenie." }) {
            lore.add(StringTag.valueOf(Component.Serializer.toJson(Component.literal(line).withStyle(ChatFormatting.GRAY))));
        }
        stack.getOrCreateTagElement("display").put("Lore", lore);
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void onDoorUse(PlayerInteractEvent.RightClickBlock event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        pending.remove(player.getUUID());
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
        pending.put(player.getUUID(), new PendingPlacement(pair(stack), player.getServer().getTickCount(),
            new VoidDoorData.DoorPosition(player.level().dimension().location().toString(), context.getClickedPos())));
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void onPlace(BlockEvent.EntityPlaceEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || !(event.getLevel() instanceof ServerLevel level)) return;
        PendingPlacement attempt = pending.remove(player.getUUID());
        if (attempt == null || attempt.tick() != player.getServer().getTickCount()) return;
        BlockPos lower = attempt.position().pos();
        if (!isDoor(level.getBlockState(lower)) || !lowerPos(event.getPos(), event.getPlacedBlock()).equals(lower)) return;
        if (!VoidDoorData.get(player.getServer()).canPlace(attempt.pair())) {
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
                drops.set(i, linked);
            }
        }
    }

    @SubscribeEvent
    public void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        var server = event.getServer();
        VoidDoorData data = VoidDoorData.get(server);
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
            }
        }
        placements.clear();
        pending.entrySet().removeIf(entry -> entry.getValue().tick() < server.getTickCount());
        var active = new java.util.HashSet<VoidDoorData.DoorPosition>();
        for (var position : data.positions()) {
            ServerLevel level = level(server, position.dimension());
            if (level == null || !level.hasChunkAt(position.pos()) || !completeDoor(level, position.pos())) continue;
            migrateOak(level, position.pos());
            // The display sits inside the closed leaf, so keeping it alive avoids a
            // visible spawn delay when the player opens the door.
            active.add(position);
            List<Display.TextDisplay> current = planes.get(position);
            if (current == null || current.stream().anyMatch(entity -> entity.isRemoved())) {
                removePlane(position);
                planes.put(position, createPlane(level, position.pos()));
            }
        }
        for (var position : List.copyOf(planes.keySet())) if (!active.contains(position)) removePlane(position);
    }

    @SubscribeEvent
    public void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !(event.player instanceof ServerPlayer player)) return;
        String dimension = player.level().dimension().location().toString();
        Sample from = previous.put(player.getUUID(), new Sample(dimension, player.position()));
        long now = player.getServer().getTickCount();
        if (from == null || !from.dimension().equals(dimension) || cooldown.getOrDefault(player.getUUID(), 0L) > now
            || player.isSpectator() || player.isPassenger() || player.isSleeping() || !player.isAlive()) return;
        VoidDoorData data = VoidDoorData.get(player.getServer());
        for (var source : data.positions()) {
            if (!source.dimension().equals(dimension) || source.pos().distToCenterSqr(player.position()) > 36) continue;
            ServerLevel sourceLevel = player.serverLevel();
            if (!completeDoor(sourceLevel, source.pos())) continue;
            BlockState sourceState = sourceLevel.getBlockState(source.pos());
            if (!sourceState.getValue(DoorBlock.OPEN) || !VoidDoorGeometry.crossed(source.pos(),
                sourceState.getValue(DoorBlock.FACING), from.position(), player.position(), player.getBbHeight())) continue;
            var destination = data.partner(dimension, source.pos());
            if (destination == null) continue;
            ServerLevel target = level(player.getServer(), destination.dimension());
            if (target == null) continue;
            target.getChunkAt(destination.pos()); // Remote dimension/chunk may have no players.
            if (!completeDoor(target, destination.pos())) continue;
            BlockState targetState = target.getBlockState(destination.pos());
            if (!targetState.getValue(DoorBlock.OPEN)) {
                player.displayClientMessage(Component.literal(PsychiatrykRoles.isEnglish(player)
                    ? "Open the linked Void Door first." : "Najpierw otwórz połączone Drzwi Pustki."), true);
                continue;
            }
            Direction facing = targetState.getValue(DoorBlock.FACING);
            Vec3 exit = safeExit(target, player, destination.pos(), facing);
            if (exit == null) {
                player.displayClientMessage(Component.literal(PsychiatrykRoles.isEnglish(player)
                    ? "No safe landing beside the linked Void Door." : "Brak bezpiecznego miejsca przy połączonych Drzwiach Pustki."), true);
                continue;
            }
            cooldown.put(player.getUUID(), now + 40);
            player.teleportTo(target, exit.x, exit.y, exit.z, facing.toYRot(), player.getXRot());
            previous.put(player.getUUID(), new Sample(destination.dimension(), exit));
            return;
        }
    }

    private static ServerLevel level(net.minecraft.server.MinecraftServer server, String dimension) {
        ResourceLocation id = ResourceLocation.tryParse(dimension);
        return id == null ? null : server.getLevel(ResourceKey.create(Registries.DIMENSION, id));
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

    private static List<Display.TextDisplay> createPlane(ServerLevel level, BlockPos lower) {
        Direction facing = level.getBlockState(lower).getValue(DoorBlock.FACING);
        Vec3 center = VoidDoorGeometry.center(lower, facing);
        List<Display.TextDisplay> result = new ArrayList<>();
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
        return result;
    }

    @SubscribeEvent
    public void onEntityLoad(EntityJoinLevelEvent event) {
        // Displays are derived from saved door data, never restore stale/duplicate visuals.
        if (event.loadedFromDisk() && event.getEntity().getTags().contains(PLANE)) event.setCanceled(true);
    }

    private void removePlane(VoidDoorData.DoorPosition position) {
        var old = planes.remove(position);
        if (old != null) old.forEach(Display.TextDisplay::discard);
    }

    @SubscribeEvent
    public void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        UUID id = event.getEntity().getUUID();
        pending.remove(id);
        cooldown.remove(id);
        previous.remove(id);
    }

    @SubscribeEvent
    public void onStop(ServerStoppedEvent event) {
        pending.clear(); placements.clear(); cooldown.clear(); previous.clear(); planes.clear();
    }
}
