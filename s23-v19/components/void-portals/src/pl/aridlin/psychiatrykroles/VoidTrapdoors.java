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
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.TrapDoorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Half;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.common.world.chunk.TicketController;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
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
    static final TicketController TICKET_CONTROLLER = new TicketController(
        ResourceLocation.fromNamespaceAndPath(PsychiatrykRoles.MOD_ID, "void_trapdoors"));
    private static final String MARKER = "psychiatrykVoidTrapdoor";
    private static final String PAIR = "psychiatrykVoidTrapdoorPair";
    private static final String PLANE = "psychiatrykVoidTrapdoorPlane";
    private final ImmersiveVoidPortals immersive = new ImmersiveVoidPortals("trapdoor");
    private final Map<UUID, Pending> pending = new HashMap<>();
    private final List<Placement> placements = new ArrayList<>();
    private final Map<UUID, Sample> previous = new HashMap<>();
    private final Map<UUID, ExitGuard> exitGuards = new HashMap<>();
    private record ExitGuard(String dimension, BlockPos door, int until) {}
    private boolean guarded(Entity e, int tick) {
        ExitGuard guard = exitGuards.get(e.getUUID());
        if (guard == null) return false;
        Vec3 center = Vec3.atCenterOf(guard.door());
        boolean near = guard.dimension().equals(e.level().dimension().location().toString())
            && Math.abs(e.getX()-center.x)<1.5 && Math.abs(e.getZ()-center.z)<1.5;
        if (tick < guard.until() || near) return true;
        exitGuards.remove(e.getUUID()); return false;
    }
    private final Map<VoidTrapdoorData.DoorPosition, List<Display.BlockDisplay>> planes = new HashMap<>();
    private final Map<VoidTrapdoorData.DoorPosition, Integer> frameMasks = new HashMap<>();
    private final Set<VoidTrapdoorData.DoorPosition> ticketed = new HashSet<>();
    private final Map<UUID, Boolean> lastOpen = new HashMap<>();

    private record Pending(UUID pair, int tick, VoidTrapdoorData.DoorPosition position) {}
    private record Placement(Pending pending, BlockEvent.EntityPlaceEvent event) {}
    private record Sample(String dimension, Vec3 position) {}

    public static boolean isVoidTrapdoor(ItemStack stack) {
        return (stack.is(Items.DARK_OAK_TRAPDOOR) || stack.is(Items.WARPED_TRAPDOOR)) && ItemTagCompat.read(stack).getBoolean(MARKER);
    }

    public static void assignCraftedPair(ItemStack stack) {
        if (isVoidTrapdoor(stack)) ItemTagCompat.putString(stack, PAIR, UUID.randomUUID().toString());
    }

    static UUID pair(ItemStack stack) {
        if (!isVoidTrapdoor(stack)) return null;
        try { return UUID.fromString(ItemTagCompat.read(stack).getString(PAIR)); }
        catch (IllegalArgumentException ignored) { return null; }
    }

    public static void localize(ItemStack stack, boolean english) {
        ItemTagCompat.setName(stack, Component.literal(stack.is(Items.WARPED_TRAPDOOR) ? (english ? "Immersive Void Trapdoors" : "Portalowe Klapy Pustki") : (english ? "Void Trapdoors" : "Klapy Pustki")));
        List<Component> lore = new ArrayList<>();
        for (String line : english ? new String[] {
            "A linked pair, even across dimensions.", "Open either trapdoor and cross its dark plane.",
            "Mining and replacing keeps the link."
        } : new String[] {
            "Połączona para, także między wymiarami.", "Otwórz dowolną klapę i przejdź przez ciemną płaszczyznę.",
            "Wykopanie i postawienie zachowuje połączenie."
        }) lore.add(Component.literal(line));
        ItemTagCompat.setLore(stack, lore);
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
        if (!isTrapdoor(level.getBlockState(attempt.position().pos()))
            || !event.getPos().equals(attempt.position().pos())) return;
        if (!VoidTrapdoorData.get(player.getServer()).canPlace(attempt.pair())) {
            event.setCanceled(true);
            return;
        }
        placements.add(new Placement(attempt, event));
    }

    public static void preserveDropLink(net.minecraft.world.level.block.state.BlockState state,
                                        LootParams.Builder params, List<ItemStack> drops) {
        if (!isTrapdoor(state)) return;
        Vec3 origin = params.getOptionalParameter(LootContextParams.ORIGIN);
        if (origin == null) return;
        UUID pair = VoidTrapdoorData.get(params.getLevel().getServer()).pairAt(
            params.getLevel().dimension().location().toString(), BlockPos.containing(origin));
        if (pair == null) return;
        for (int i = 0; i < drops.size(); i++) if ((drops.get(i).is(Items.DARK_OAK_TRAPDOOR) || drops.get(i).is(Items.WARPED_TRAPDOOR))) {
            ItemStack linked = new ItemStack(state.is(Blocks.WARPED_TRAPDOOR) ? Items.WARPED_TRAPDOOR : Items.DARK_OAK_TRAPDOOR, drops.get(i).getCount());
            ItemTagCompat.putBoolean(linked, MARKER, true);
            ItemTagCompat.putString(linked, PAIR, pair.toString());
            drops.set(i, linked);
        }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void onBreak(BlockEvent.BreakEvent event) {
        if (!(event.getPlayer() instanceof ServerPlayer player) || !player.isShiftKeyDown()
            || !(event.getLevel() instanceof ServerLevel sourceLevel)
            || !isTrapdoor(event.getState())) return;
        BlockPos source = event.getPos();
        VoidTrapdoorData data = VoidTrapdoorData.get(player.getServer());
        String dimension = sourceLevel.dimension().location().toString();
        UUID pair = data.pairAt(dimension, source);
        VoidTrapdoorData.DoorPosition partner = data.partner(dimension, source);
        if (pair == null || partner == null) return;
        ServerLevel target = level(player.getServer(), partner.dimension());
        if (target == null) return;
        target.getChunkAt(partner.pos());
        if (!isTrapdoor(target.getBlockState(partner.pos()))) return;
        boolean diamond = sourceLevel.getBlockState(source).is(Blocks.WARPED_TRAPDOOR);
        event.setCanceled(true);
        sourceLevel.setBlock(source, Blocks.AIR.defaultBlockState(), 3);
        target.setBlock(partner.pos(), Blocks.AIR.defaultBlockState(), 3);
        data.removeDoor(dimension, source);
        data.removeDoor(partner.dimension(), partner.pos());
        removePlane(new VoidTrapdoorData.DoorPosition(dimension, source));
        removePlane(partner);
        ItemStack drops = new ItemStack(diamond ? Items.WARPED_TRAPDOOR : Items.DARK_OAK_TRAPDOOR, 2);
        ItemTagCompat.putBoolean(drops, MARKER, true);
        ItemTagCompat.putString(drops, PAIR, pair.toString());
        if (!player.getInventory().add(drops)) player.drop(drops, false);
    }

    @SubscribeEvent
    public void onServerTick(ServerTickEvent.Post event) {
        MinecraftServer server = event.getServer();
        VoidTrapdoorData data = VoidTrapdoorData.get(server);
        reconcileTickets(server, data.positions());
        for (var position : data.positions()) {
            ServerLevel level = level(server, position.dimension());
            if (level != null && level.hasChunkAt(position.pos())
                && !isTrapdoor(level.getBlockState(position.pos())))
                data.removeDoor(position.dimension(), position.pos());
        }
        for (Placement placement : placements) {
            Pending attempt = placement.pending();
            ServerLevel level = level(server, attempt.position().dimension());
            if (!placement.event().isCanceled() && level != null
                && isTrapdoor(level.getBlockState(attempt.position().pos())))
                data.addDoor(attempt.pair(), attempt.position());
        }
        placements.clear();
        pending.entrySet().removeIf(entry -> entry.getValue().tick() < server.getTickCount());
        reconcileTickets(server, data.positions());
        synchronize(server, data);
        immersive.begin();
        for (var entry : data.pairs().entrySet()) {
            if (entry.getValue().size() != 2) continue;
            var a = entry.getValue().get(0); var b = entry.getValue().get(1);
            ServerLevel al = level(server, a.dimension()), bl = level(server, b.dimension());
            if (al == null || bl == null) continue;
            BlockState as = al.getBlockState(a.pos()), bs = bl.getBlockState(b.pos());
            if (!as.is(Blocks.WARPED_TRAPDOOR) || !bs.is(Blocks.WARPED_TRAPDOOR) || !as.getValue(TrapDoorBlock.OPEN) || !bs.getValue(TrapDoorBlock.OPEN)) continue;
            immersive.link(entry.getKey(), ImmersiveVoidPortals.trapdoor(al, a.pos(), as.getValue(TrapDoorBlock.HALF)),
                ImmersiveVoidPortals.trapdoor(bl, b.pos(), bs.getValue(TrapDoorBlock.HALF)));
        }
        immersive.end();
        Set<VoidTrapdoorData.DoorPosition> active = new HashSet<>();
        for (var position : data.positions()) {
            ServerLevel level = level(server, position.dimension());
            if (level == null || !level.hasChunkAt(position.pos())
                || !isTrapdoor(level.getBlockState(position.pos()))) continue;
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
    public void onPlayerTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        String dimension = player.level().dimension().location().toString();
        Sample from = previous.put(player.getUUID(), new Sample(dimension, player.position()));
        if (from == null || !from.dimension().equals(dimension) || player.isSpectator()
            || player.isPassenger() || player.isSleeping() || !player.isAlive()) return;
        if (guarded(player, player.getServer().getTickCount())) return;
        VoidTrapdoorData data = VoidTrapdoorData.get(player.getServer());
        for (var source : data.positions()) {
            if (!source.dimension().equals(dimension) || source.pos().distToCenterSqr(player.position()) > 25) continue;
            ServerLevel sourceLevel = player.serverLevel();
            if (sourceLevel.getBlockState(source.pos()).is(Blocks.WARPED_TRAPDOOR)) continue;
            if (!isTrapdoor(sourceLevel.getBlockState(source.pos()))
                || !sourceLevel.getBlockState(source.pos()).getValue(TrapDoorBlock.OPEN)) continue;
            Vec3 center = planeCenter(sourceLevel, source.pos());
            Contact contact = contact(center, frameMask(sourceLevel, source.pos()),
                player.getBoundingBox(), from.position(), player.position(),
                player.getDeltaMovement());
            if (contact == null) continue;
            var destination = data.partner(dimension, source.pos());
            if (destination == null) continue;
            ServerLevel target = level(player.getServer(), destination.dimension());
            if (target == null) continue;
            target.getChunkAt(destination.pos());
            if (!isTrapdoor(target.getBlockState(destination.pos()))) continue;
            Direction away = target.getBlockState(destination.pos()).getValue(TrapDoorBlock.FACING);
            Vec3 exit = safeExit(target, player, destination.pos(), away);
            if (exit == null) continue;
            player.teleportTo(target, exit.x, exit.y, exit.z, away.toYRot(), player.getXRot());
            if (player.serverLevel() != target || player.position().distanceToSqr(exit) > .25) continue;
            player.setDeltaMovement(popVelocity(away));
            player.hurtMarked = true;
            player.connection.send(new ClientboundSetEntityMotionPacket(player));
            previous.put(player.getUUID(), new Sample(destination.dimension(), exit));
            exitGuards.put(player.getUUID(), new ExitGuard(destination.dimension(), destination.pos(), player.getServer().getTickCount()+20));
            return;
        }
    }

    @SubscribeEvent
    public void onLevelTick(LevelTickEvent.Post event) {
        if (!(event.getLevel() instanceof ServerLevel sourceLevel)) return;
        if (sourceLevel.getServer().getTickCount()%1200==0) exitGuards.entrySet().removeIf(e -> sourceLevel.getServer().getTickCount()-e.getValue().until()>1200 && sourceLevel.getServer().getPlayerList().getPlayer(e.getKey())==null && !java.util.stream.StreamSupport.stream(sourceLevel.getServer().getAllLevels().spliterator(),false).anyMatch(l -> l.getEntity(e.getKey())!=null));
        String dimension = sourceLevel.dimension().location().toString();
        VoidTrapdoorData data = VoidTrapdoorData.get(sourceLevel.getServer());
        for (var source : data.positions()) {
            if (!source.dimension().equals(dimension)) continue;
            var state = sourceLevel.getBlockState(source.pos());
            if (state.is(Blocks.WARPED_TRAPDOOR)) continue;
            if (!isTrapdoor(state) || !state.getValue(TrapDoorBlock.OPEN)) continue;
            var destination = data.partner(dimension, source.pos());
            if (destination == null) continue;
            ServerLevel target = level(sourceLevel.getServer(), destination.dimension());
            if (target == null) continue;
            Vec3 center = planeCenter(sourceLevel, source.pos());
            List<ItemEntity> nearby = sourceLevel.getEntitiesOfClass(ItemEntity.class,
                new AABB(center.x - 4.5, center.y - 4.5, center.z - 4.5,
                    center.x + 4.5, center.y + 4.5, center.z + 4.5));
            if (nearby.isEmpty()) continue;
            int visibleMask = frameMask(sourceLevel, source.pos());
            Direction away = null;
            for (ItemEntity item : nearby) {
                if (guarded(item, sourceLevel.getServer().getTickCount())) continue;
                Contact contact = contact(center, visibleMask, item.getBoundingBox(),
                    item.position().subtract(item.getDeltaMovement()), item.position(), item.getDeltaMovement());
                if (contact == null) continue;
                if (away == null) {
                    // Idle portals should not synchronously load their linked chunk every tick.
                    target.getChunkAt(destination.pos());
                    BlockState targetState = target.getBlockState(destination.pos());
                    if (!isTrapdoor(targetState)) break;
                    away = targetState.getValue(TrapDoorBlock.FACING);
                }
                Vec3 exit = safeExit(target, item, destination.pos(), away);
                if (exit != null && item.teleportTo(target, exit.x, exit.y, exit.z,
                    Set.of(), item.getYRot(), item.getXRot())) {
                    Entity arrived = target.getEntity(item.getUUID());
                    if (arrived != null) { arrived.setDeltaMovement(popVelocity(away));
                        exitGuards.put(arrived.getUUID(), new ExitGuard(destination.dimension(), destination.pos(), sourceLevel.getServer().getTickCount()+20)); }
                }
            }
        }
    }

    record Contact(Vec3 offset, boolean upward) {}

    static Contact contact(Vec3 center, int frameMask, AABB hitbox, Vec3 from, Vec3 to, Vec3 velocity) {
        if (from.distanceToSqr(to) > 16) return null;
        PlaneBounds bounds = planeBounds(frameMask);
        AABB plane = new AABB(center.x + bounds.west(), center.y - .01, center.z + bounds.north(),
            center.x + bounds.east(), center.y + .01, center.z + bounds.south());
        double fraction = PortalSweep.firstContact(plane, hitbox, from, to);
        if (Double.isNaN(fraction)) return null;
        double motion = to.y - from.y;
        if (Math.abs(motion) < .001) motion = velocity.y;
        boolean upward = motion > .001 || Math.abs(motion) < .001 && from.y < center.y;
        Vec3 hit = from.lerp(to, fraction);
        return new Contact(new Vec3(Mth.clamp(hit.x - center.x, -.4, .4), 0,
            Mth.clamp(hit.z - center.z, -.4, .4)), upward);
    }

    static Vec3 safeExit(ServerLevel level, Entity traveler, BlockPos door, Direction away) {
        Vec3 center = Vec3.atBottomCenterOf(door);
        // TrapDoorBlock.FACING points away from the side it was attached to.
        // Stay above the destination, even if the air below it would be clear.
        for (double offset : new double[] {.45, .7, 1.05}) {
            for (double lateral : new double[] {0, -.35, .35}) {
                Vec3 exit = center.add(away.getStepX() * offset - away.getStepZ() * lateral,
                    1.02, away.getStepZ() * offset + away.getStepX() * lateral);
                if (level.noCollision(traveler,
                    traveler.getBoundingBox().move(exit.subtract(traveler.position())))) return exit;
            }
        }
        return null;
    }

    static Vec3 popVelocity(Direction away) {
        return new Vec3(away.getStepX() * .35, .42, away.getStepZ() * .35);
    }

    private static Vec3 planeCenter(ServerLevel level, BlockPos pos) {
        Half half = level.getBlockState(pos).getValue(TrapDoorBlock.HALF);
        return Vec3.atBottomCenterOf(pos).add(0, half == Half.TOP ? .9 : .1, 0);
    }

    private static int frameMask(ServerLevel level, BlockPos pos) {
        if (level.getBlockState(pos).is(Blocks.WARPED_TRAPDOOR)) return 15;
        int mask = 0;
        double y = level.getBlockState(pos).getValue(TrapDoorBlock.HALF) == Half.TOP ? .9 : .1;
        for (Direction side : new Direction[] {Direction.NORTH, Direction.EAST, Direction.SOUTH, Direction.WEST})
            if (!PortalFrameOcclusion.covers(level, pos.relative(side),
                PortalFrameOcclusion.sideStrip(side, .03, .97, y - .035, y + .035)))
                mask |= 1 << side.get2DDataValue();
        return mask;
    }

    record PlaneBounds(double north, double east, double south, double west) {}

    static PlaneBounds planeBounds(int mask) {
        return new PlaneBounds(
            (mask & (1 << Direction.NORTH.get2DDataValue())) != 0 ? -.42 : -.5,
            (mask & (1 << Direction.EAST.get2DDataValue())) != 0 ? .42 : .5,
            (mask & (1 << Direction.SOUTH.get2DDataValue())) != 0 ? .42 : .5,
            (mask & (1 << Direction.WEST.get2DDataValue())) != 0 ? -.42 : -.5);
    }

    private static List<Display.BlockDisplay> createPlane(ServerLevel level, BlockPos pos, int mask) {
        Vec3 center = planeCenter(level, pos);
        List<Display.BlockDisplay> result = new ArrayList<>();
        PlaneBounds bounds = planeBounds(mask);
        float north = (float) bounds.north();
        float east = (float) bounds.east();
        float south = (float) bounds.south();
        float west = (float) bounds.west();
        if (!level.getBlockState(pos).is(Blocks.WARPED_TRAPDOOR)) result.add(addDisplay(level, center, Blocks.BLACK_CONCRETE.defaultBlockState(),
            new Vector3f(west, -.01f, north), new Vector3f(east - west, .02f, south - north)));
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
            result.add(addDisplay(level, center, (level.getBlockState(pos).is(Blocks.WARPED_TRAPDOOR) ? Blocks.WARPED_PLANKS : Blocks.DARK_OAK_PLANKS).defaultBlockState(), offset, size));
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

    private static boolean isTrapdoor(BlockState state) { return state.is(Blocks.DARK_OAK_TRAPDOOR) || state.is(Blocks.WARPED_TRAPDOOR); }

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
        if (level != null) TICKET_CONTROLLER.forceChunk(level,
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
            if (a == null || b == null || !isTrapdoor(a.getBlockState(first.pos()))
                || !isTrapdoor(b.getBlockState(second.pos()))) continue;
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
        if (event.loadedFromDisk() && (event.getEntity().getTags().contains(PLANE) || event.getEntity().getTags().contains(ImmersiveVoidPortals.TAG))) event.setCanceled(true);
    }

    private void removePlane(VoidTrapdoorData.DoorPosition position) {
        List<Display.BlockDisplay> old = planes.remove(position);
        frameMasks.remove(position);
        if (old != null) old.forEach(Display::discard);
    }

    @SubscribeEvent public void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        pending.remove(event.getEntity().getUUID());
        previous.remove(event.getEntity().getUUID());
        exitGuards.remove(event.getEntity().getUUID());
    }

    @SubscribeEvent public void onStop(ServerStoppedEvent event) {
        immersive.clear(); pending.clear(); placements.clear(); previous.clear(); planes.clear(); frameMasks.clear();
        ticketed.clear(); lastOpen.clear(); exitGuards.clear();
    }
}
