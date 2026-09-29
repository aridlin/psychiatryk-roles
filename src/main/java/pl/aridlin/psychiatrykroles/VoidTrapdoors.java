package pl.aridlin.psychiatrykroles;

import com.mojang.math.Transformation;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.TrapDoorBlock;
import net.minecraft.world.level.block.state.properties.Half;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.common.world.ForgeChunkManager;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.nio.charset.StandardCharsets;

public final class VoidTrapdoors {
    private static final String MARKER = "psychiatrykVoidTrapdoor";
    private static final String PAIR = "psychiatrykVoidTrapdoorPair";
    private static final String PLANE = "psychiatrykVoidTrapdoorPlane";
    private final Map<UUID, Pending> pending = new HashMap<>();
    private final List<Placement> placements = new ArrayList<>();
    private final Map<UUID, Sample> previous = new HashMap<>();
    private final Map<VoidTrapdoorData.DoorPosition, List<Display.BlockDisplay>> planes = new HashMap<>();
    private final Map<VoidTrapdoorData.DoorPosition, Integer> frameMasks = new HashMap<>();
    private final Set<VoidTrapdoorData.DoorPosition> ticketed = new HashSet<>();
    private final Map<UUID, Boolean> lastOpen = new HashMap<>();

    private record Pending(UUID pair, int tick, VoidTrapdoorData.DoorPosition position) {}
    private record Placement(Pending pending, BlockEvent.EntityPlaceEvent event) {}
    private record Sample(String dimension, Vec3 position) {}

    public static boolean isVoidTrapdoor(ItemStack stack) {
        return stack.is(Items.DARK_OAK_TRAPDOOR) && stack.hasTag() && stack.getTag().getBoolean(MARKER);
    }

    public static void assignCraftedPair(ItemStack stack) {
        if (isVoidTrapdoor(stack)) stack.getOrCreateTag().putString(PAIR, UUID.randomUUID().toString());
    }

    static UUID pair(ItemStack stack) {
        if (!isVoidTrapdoor(stack)) return null;
        try { return UUID.fromString(stack.getTag().getString(PAIR)); }
        catch (IllegalArgumentException ignored) { return null; }
    }

    public static void localize(ItemStack stack, boolean english) {
        stack.setHoverName(Component.literal(english ? "Void Trapdoors" : "Klapy Pustki"));
        ListTag lore = new ListTag();
        for (String line : english ? new String[] {
            "A linked pair, even across dimensions.", "Open either trapdoor and cross its dark plane.",
            "Mining and replacing keeps the link."
        } : new String[] {
            "Połączona para, także między wymiarami.", "Otwórz dowolną klapę i przejdź przez ciemną płaszczyznę.",
            "Wykopanie i postawienie zachowuje połączenie."
        }) lore.add(StringTag.valueOf(Component.Serializer.toJson(Component.literal(line))));
        stack.getOrCreateTagElement("display").put("Lore", lore);
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void onUse(PlayerInteractEvent.RightClickBlock event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        pending.remove(player.getUUID());
        ItemStack stack = event.getItemStack();
        if (!isVoidTrapdoor(stack)) return;
        if (pair(stack) == null) {
            if (stack.getCount() > 2) { event.setCanceled(true); return; }
            assignCraftedPair(stack);
        }
        BlockPlaceContext context = new BlockPlaceContext(player, event.getHand(), stack, event.getHitVec());
        pending.put(player.getUUID(), new Pending(pair(stack), player.getServer().getTickCount(),
            new VoidTrapdoorData.DoorPosition(player.level().dimension().location().toString(), context.getClickedPos())));
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void onPlace(BlockEvent.EntityPlaceEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || !(event.getLevel() instanceof ServerLevel level)) return;
        Pending attempt = pending.remove(player.getUUID());
        if (attempt == null || attempt.tick() != player.getServer().getTickCount()) return;
        if (!level.getBlockState(attempt.position().pos()).is(Blocks.DARK_OAK_TRAPDOOR)
            || !event.getPos().equals(attempt.position().pos())) return;
        if (!VoidTrapdoorData.get(player.getServer()).canPlace(attempt.pair())) {
            event.setCanceled(true);
            return;
        }
        placements.add(new Placement(attempt, event));
    }

    public static void preserveDropLink(net.minecraft.world.level.block.state.BlockState state,
                                        LootParams.Builder params, List<ItemStack> drops) {
        if (!state.is(Blocks.DARK_OAK_TRAPDOOR)) return;
        Vec3 origin = params.getOptionalParameter(LootContextParams.ORIGIN);
        if (origin == null) return;
        UUID pair = VoidTrapdoorData.get(params.getLevel().getServer()).pairAt(
            params.getLevel().dimension().location().toString(), BlockPos.containing(origin));
        if (pair == null) return;
        for (int i = 0; i < drops.size(); i++) if (drops.get(i).is(Items.DARK_OAK_TRAPDOOR)) {
            ItemStack linked = new ItemStack(Items.DARK_OAK_TRAPDOOR, drops.get(i).getCount());
            linked.getOrCreateTag().putBoolean(MARKER, true);
            linked.getOrCreateTag().putString(PAIR, pair.toString());
            drops.set(i, linked);
        }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void onBreak(BlockEvent.BreakEvent event) {
        if (!(event.getPlayer() instanceof ServerPlayer player) || !player.isShiftKeyDown()
            || !(event.getLevel() instanceof ServerLevel sourceLevel)
            || !event.getState().is(Blocks.DARK_OAK_TRAPDOOR)) return;
        BlockPos source = event.getPos();
        VoidTrapdoorData data = VoidTrapdoorData.get(player.getServer());
        String dimension = sourceLevel.dimension().location().toString();
        UUID pair = data.pairAt(dimension, source);
        VoidTrapdoorData.DoorPosition partner = data.partner(dimension, source);
        if (pair == null || partner == null) return;
        ServerLevel target = level(player.getServer(), partner.dimension());
        if (target == null) return;
        target.getChunkAt(partner.pos());
        if (!target.getBlockState(partner.pos()).is(Blocks.DARK_OAK_TRAPDOOR)) return;
        event.setCanceled(true);
        sourceLevel.setBlock(source, Blocks.AIR.defaultBlockState(), 3);
        target.setBlock(partner.pos(), Blocks.AIR.defaultBlockState(), 3);
        data.removeDoor(dimension, source);
        data.removeDoor(partner.dimension(), partner.pos());
        removePlane(new VoidTrapdoorData.DoorPosition(dimension, source));
        removePlane(partner);
        ItemStack drops = new ItemStack(Items.DARK_OAK_TRAPDOOR, 2);
        drops.getOrCreateTag().putBoolean(MARKER, true);
        drops.getOrCreateTag().putString(PAIR, pair.toString());
        if (!player.getInventory().add(drops)) player.drop(drops, false);
    }

    @SubscribeEvent
    public void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        MinecraftServer server = event.getServer();
        VoidTrapdoorData data = VoidTrapdoorData.get(server);
        reconcileTickets(server, data.positions());
        for (var position : data.positions()) {
            ServerLevel level = level(server, position.dimension());
            if (level != null && level.hasChunkAt(position.pos())
                && !level.getBlockState(position.pos()).is(Blocks.DARK_OAK_TRAPDOOR))
                data.removeDoor(position.dimension(), position.pos());
        }
        for (Placement placement : placements) {
            Pending attempt = placement.pending();
            ServerLevel level = level(server, attempt.position().dimension());
            if (!placement.event().isCanceled() && level != null
                && level.getBlockState(attempt.position().pos()).is(Blocks.DARK_OAK_TRAPDOOR))
                data.addDoor(attempt.pair(), attempt.position());
        }
        placements.clear();
        pending.entrySet().removeIf(entry -> entry.getValue().tick() < server.getTickCount());
        reconcileTickets(server, data.positions());
        synchronize(server, data);
        Set<VoidTrapdoorData.DoorPosition> active = new HashSet<>();
        for (var position : data.positions()) {
            ServerLevel level = level(server, position.dimension());
            if (level == null || !level.hasChunkAt(position.pos())
                || !level.getBlockState(position.pos()).is(Blocks.DARK_OAK_TRAPDOOR)) continue;
            active.add(position);
            List<Display.BlockDisplay> plane = planes.get(position);
            int frameMask = frameMask(level, position.pos());
            if (plane == null || plane.stream().anyMatch(Entity::isRemoved)
                || frameMasks.getOrDefault(position, -1) != frameMask) {
                removePlane(position);
                planes.put(position, createPlane(level, position.pos(), frameMask));
                frameMasks.put(position, frameMask);
            }
        }
        for (var position : List.copyOf(planes.keySet())) if (!active.contains(position)) removePlane(position);
    }

    @SubscribeEvent
    public void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !(event.player instanceof ServerPlayer player)) return;
        String dimension = player.level().dimension().location().toString();
        Sample from = previous.put(player.getUUID(), new Sample(dimension, player.position()));
        if (from == null || !from.dimension().equals(dimension) || player.isSpectator()
            || player.isPassenger() || player.isSleeping() || !player.isAlive()) return;
        VoidTrapdoorData data = VoidTrapdoorData.get(player.getServer());
        for (var source : data.positions()) {
            if (!source.dimension().equals(dimension) || source.pos().distToCenterSqr(player.position()) > 25) continue;
            ServerLevel sourceLevel = player.serverLevel();
            if (!sourceLevel.getBlockState(source.pos()).is(Blocks.DARK_OAK_TRAPDOOR)
                || !sourceLevel.getBlockState(source.pos()).getValue(TrapDoorBlock.OPEN)) continue;
            Vec3 center = planeCenter(sourceLevel, source.pos());
            Contact contact = contact(center, player.getBoundingBox(), from.position(), player.position(),
                player.getDeltaMovement());
            if (contact == null) continue;
            var destination = data.partner(dimension, source.pos());
            if (destination == null) continue;
            ServerLevel target = level(player.getServer(), destination.dimension());
            if (target == null) continue;
            target.getChunkAt(destination.pos());
            if (!target.getBlockState(destination.pos()).is(Blocks.DARK_OAK_TRAPDOOR)) continue;
            Direction sourceFacing = sourceLevel.getBlockState(source.pos()).getValue(TrapDoorBlock.FACING);
            Direction targetFacing = target.getBlockState(destination.pos()).getValue(TrapDoorBlock.FACING);
            Vec3 walked = player.position().subtract(from.position());
            Vec3 velocity = player.getDeltaMovement();
            if (walked.lengthSqr() > velocity.lengthSqr()) velocity = walked;
            Vec3 outVelocity = VoidDoorGeometry.rotate(velocity, sourceFacing, targetFacing);
            Vec3 exit = safeExit(target, player, destination.pos(), contact,
                sourceFacing, targetFacing, outVelocity);
            if (exit == null) continue;
            float yaw = Mth.wrapDegrees(player.getYRot()
                + Mth.wrapDegrees(targetFacing.toYRot() - sourceFacing.toYRot()));
            player.teleportTo(target, exit.x, exit.y, exit.z, yaw, player.getXRot());
            player.setDeltaMovement(outVelocity);
            player.hurtMarked = true;
            player.connection.send(new ClientboundSetEntityMotionPacket(player));
            previous.put(player.getUUID(), new Sample(destination.dimension(), exit));
            return;
        }
    }

    @SubscribeEvent
    public void onLevelTick(TickEvent.LevelTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !(event.level instanceof ServerLevel sourceLevel)) return;
        String dimension = sourceLevel.dimension().location().toString();
        VoidTrapdoorData data = VoidTrapdoorData.get(sourceLevel.getServer());
        for (var source : data.positions()) {
            if (!source.dimension().equals(dimension)) continue;
            var state = sourceLevel.getBlockState(source.pos());
            if (!state.is(Blocks.DARK_OAK_TRAPDOOR) || !state.getValue(TrapDoorBlock.OPEN)) continue;
            var destination = data.partner(dimension, source.pos());
            if (destination == null) continue;
            ServerLevel target = level(sourceLevel.getServer(), destination.dimension());
            if (target == null) continue;
            target.getChunkAt(destination.pos());
            if (!target.getBlockState(destination.pos()).is(Blocks.DARK_OAK_TRAPDOOR)) continue;
            Vec3 center = planeCenter(sourceLevel, source.pos());
            for (ItemEntity item : sourceLevel.getEntitiesOfClass(ItemEntity.class,
                new AABB(center.x - 1, center.y - 1, center.z - 1, center.x + 1, center.y + 1, center.z + 1))) {
                Contact contact = contact(center, item.getBoundingBox(),
                    item.position().subtract(item.getDeltaMovement()), item.position(), item.getDeltaMovement());
                if (contact == null) continue;
                Direction sourceFacing = state.getValue(TrapDoorBlock.FACING);
                Direction targetFacing = target.getBlockState(destination.pos()).getValue(TrapDoorBlock.FACING);
                Vec3 outVelocity = VoidDoorGeometry.rotate(item.getDeltaMovement(), sourceFacing, targetFacing);
                Vec3 exit = safeExit(target, item, destination.pos(), contact,
                    sourceFacing, targetFacing, outVelocity);
                if (exit != null && item.teleportTo(target, exit.x, exit.y, exit.z,
                    Set.of(), item.getYRot(), item.getXRot())) {
                    Entity arrived = target.getEntity(item.getUUID());
                    if (arrived != null) arrived.setDeltaMovement(outVelocity);
                }
            }
        }
    }

    static boolean crossed(Vec3 center, Vec3 from, Vec3 to) {
        if (from.distanceToSqr(to) > 16) return false;
        double a = from.y - center.y, b = to.y - center.y;
        if (a == b || !((a < 0 && b >= 0) || (a > 0 && b <= 0))) return false;
        Vec3 hit = from.lerp(to, a / (a - b));
        return Math.abs(hit.x - center.x) < .46 && Math.abs(hit.z - center.z) < .46;
    }

    static boolean touches(Vec3 center, AABB hitbox) {
        return new AABB(center.x - .46, center.y - .025, center.z - .46,
            center.x + .46, center.y + .025, center.z + .46).intersects(hitbox);
    }

    record Contact(Vec3 offset, boolean upward) {}

    static Contact contact(Vec3 center, AABB hitbox, Vec3 from, Vec3 to, Vec3 velocity) {
        if (!touches(center, hitbox) || from.distanceToSqr(to) > 16) return null;
        double motion = to.y - from.y;
        if (Math.abs(motion) < .001) motion = velocity.y;
        boolean upward = motion > .001 || Math.abs(motion) < .001 && from.y < center.y;
        double fraction = Math.abs(to.y - from.y) < .001 ? 1
            : Mth.clamp((center.y - from.y) / (to.y - from.y), 0, 1);
        Vec3 hit = from.lerp(to, fraction);
        return new Contact(new Vec3(Mth.clamp(hit.x - center.x, -.4, .4), 0,
            Mth.clamp(hit.z - center.z, -.4, .4)), upward);
    }

    static Vec3 safeExit(ServerLevel level, Entity traveler, BlockPos door, Contact contact,
                         Direction sourceFacing, Direction targetFacing, Vec3 outVelocity) {
        Vec3 offset = VoidDoorGeometry.rotate(contact.offset(), sourceFacing, targetFacing);
        Vec3 best = null;
        double bestScore = Double.POSITIVE_INFINITY;
        for (boolean requireSupport : new boolean[] { true, false }) {
            for (boolean upward : new boolean[] {contact.upward(), !contact.upward()}) {
                for (Direction side : new Direction[] {Direction.NORTH, Direction.EAST, Direction.SOUTH, Direction.WEST}) {
                    double sideways = -side.getStepZ() * offset.x + side.getStepX() * offset.z;
                    for (int height = 0; height < 2; height++) {
                        double feet = upward ? (height == 0 ? 1.01 : .01)
                            : (height == 0 ? -1.99 : -.99);
                        Vec3 exit = Vec3.atBottomCenterOf(door).add(
                            side.getStepX() - side.getStepZ() * sideways, feet,
                            side.getStepZ() + side.getStepX() * sideways);
                        BlockPos floor = BlockPos.containing(exit.x, exit.y - .1, exit.z);
                        boolean supported = !level.getBlockState(floor).getCollisionShape(level, floor).isEmpty();
                        if (requireSupport && !supported) continue;
                        if (!level.noCollision(traveler,
                            traveler.getBoundingBox().move(exit.subtract(traveler.position())))) continue;
                        double score = (upward == contact.upward() ? 0 : 8) + height * .8
                            - (side.getStepX() * offset.x + side.getStepZ() * offset.z) * 2
                            - (side.getStepX() * outVelocity.x + side.getStepZ() * outVelocity.z) * 3
                            + (side == targetFacing ? 0 : .1);
                        if (score < bestScore) { bestScore = score; best = exit; }
                    }
                }
            }
            if (best != null) return best;
        }
        return best;
    }

    private static Vec3 planeCenter(ServerLevel level, BlockPos pos) {
        Half half = level.getBlockState(pos).getValue(TrapDoorBlock.HALF);
        return Vec3.atBottomCenterOf(pos).add(0, half == Half.TOP ? .9 : .1, 0);
    }

    private static int frameMask(ServerLevel level, BlockPos pos) {
        int mask = 0;
        for (Direction side : new Direction[] {Direction.NORTH, Direction.EAST, Direction.SOUTH, Direction.WEST})
            if (level.getBlockState(pos.relative(side)).isAir()) mask |= 1 << side.get2DDataValue();
        return mask;
    }

    private static List<Display.BlockDisplay> createPlane(ServerLevel level, BlockPos pos, int mask) {
        Vec3 center = planeCenter(level, pos);
        List<Display.BlockDisplay> result = new ArrayList<>();
        result.add(addDisplay(level, center, Blocks.BLACK_CONCRETE.defaultBlockState(),
            new Vector3f(-.43f, -.01f, -.43f), new Vector3f(.86f, .02f, .86f)));
        for (Direction side : new Direction[] {Direction.NORTH, Direction.EAST, Direction.SOUTH, Direction.WEST}) {
            if ((mask & (1 << side.get2DDataValue())) == 0) continue;
            Vector3f offset = switch (side) {
                case NORTH -> new Vector3f(-.47f, -.035f, -.49f);
                case SOUTH -> new Vector3f(-.47f, -.035f, .42f);
                case WEST -> new Vector3f(-.49f, -.035f, -.47f);
                case EAST -> new Vector3f(.42f, -.035f, -.47f);
                default -> throw new IllegalStateException();
            };
            Vector3f size = side.getAxis() == Direction.Axis.Z
                ? new Vector3f(.94f, .07f, .07f) : new Vector3f(.07f, .07f, .94f);
            result.add(addDisplay(level, center, Blocks.DARK_OAK_PLANKS.defaultBlockState(), offset, size));
        }
        return result;
    }

    private static Display.BlockDisplay addDisplay(ServerLevel level, Vec3 center,
                                                   net.minecraft.world.level.block.state.BlockState block,
                                                   Vector3f offset, Vector3f size) {
        Display.BlockDisplay display = new Display.BlockDisplay(EntityType.BLOCK_DISPLAY, level);
        CompoundTag tag = new CompoundTag();
        tag.put("block_state", NbtUtils.writeBlockState(block));
        tag.put("transformation", Transformation.EXTENDED_CODEC.encodeStart(NbtOps.INSTANCE,
            new Transformation(offset, new Quaternionf(), size, new Quaternionf())).result().orElseThrow());
        display.load(tag);
        display.setPos(center.x, center.y, center.z);
        display.setInvulnerable(true);
        display.setNoGravity(true);
        display.addTag(PLANE);
        level.addFreshEntity(display);
        return display;
    }

    private static ServerLevel level(MinecraftServer server, String dimension) {
        ResourceLocation id = ResourceLocation.tryParse(dimension);
        return id == null ? null : server.getLevel(ResourceKey.create(Registries.DIMENSION, id));
    }

    private void reconcileTickets(MinecraftServer server, List<VoidTrapdoorData.DoorPosition> positions) {
        Set<VoidTrapdoorData.DoorPosition> wanted = new HashSet<>(positions);
        for (var position : wanted) if (ticketed.add(position)) changeTicket(server, position, true);
        for (var position : List.copyOf(ticketed)) if (!wanted.contains(position)) {
            changeTicket(server, position, false);
            ticketed.remove(position);
        }
    }

    private static void changeTicket(MinecraftServer server, VoidTrapdoorData.DoorPosition position, boolean add) {
        ServerLevel level = level(server, position.dimension());
        UUID owner = UUID.nameUUIDFromBytes(("void-trapdoor:" + position.dimension() + ":" + position.pos())
            .getBytes(StandardCharsets.UTF_8));
        if (level != null) ForgeChunkManager.forceChunk(level, PsychiatrykRoles.MOD_ID,
            owner, position.pos().getX() >> 4, position.pos().getZ() >> 4, add, false);
    }

    private void synchronize(MinecraftServer server, VoidTrapdoorData data) {
        var pairs = data.pairs();
        lastOpen.keySet().retainAll(pairs.keySet());
        for (var entry : pairs.entrySet()) {
            if (entry.getValue().size() != 2) continue;
            var first = entry.getValue().get(0);
            var second = entry.getValue().get(1);
            ServerLevel a = level(server, first.dimension()), b = level(server, second.dimension());
            if (a == null || b == null || !a.getBlockState(first.pos()).is(Blocks.DARK_OAK_TRAPDOOR)
                || !b.getBlockState(second.pos()).is(Blocks.DARK_OAK_TRAPDOOR)) continue;
            boolean aOpen = a.getBlockState(first.pos()).getValue(TrapDoorBlock.OPEN);
            boolean bOpen = b.getBlockState(second.pos()).getValue(TrapDoorBlock.OPEN);
            Boolean prior = lastOpen.get(entry.getKey());
            boolean desired = aOpen == bOpen ? aOpen : prior != null && aOpen == prior ? bOpen : aOpen;
            if (aOpen != desired) a.setBlock(first.pos(), a.getBlockState(first.pos()).setValue(TrapDoorBlock.OPEN, desired), 2);
            if (bOpen != desired) b.setBlock(second.pos(), b.getBlockState(second.pos()).setValue(TrapDoorBlock.OPEN, desired), 2);
            lastOpen.put(entry.getKey(), desired);
        }
    }

    @SubscribeEvent public void onEntityLoad(EntityJoinLevelEvent event) {
        if (event.loadedFromDisk() && event.getEntity().getTags().contains(PLANE)) event.setCanceled(true);
    }

    private void removePlane(VoidTrapdoorData.DoorPosition position) {
        List<Display.BlockDisplay> old = planes.remove(position);
        frameMasks.remove(position);
        if (old != null) old.forEach(Display::discard);
    }

    @SubscribeEvent public void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        pending.remove(event.getEntity().getUUID());
        previous.remove(event.getEntity().getUUID());
    }

    @SubscribeEvent public void onStop(ServerStoppedEvent event) {
        pending.clear(); placements.clear(); previous.clear(); planes.clear(); frameMasks.clear();
        ticketed.clear(); lastOpen.clear();
    }
}
