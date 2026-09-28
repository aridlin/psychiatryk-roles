package pl.aridlin.psychiatrykroles;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

final class VoidDoors {
    private static final String MARKER = "psychiatrykVoidDoor";
    private static final String PAIR = "psychiatrykVoidDoorPair";
    private static final Map<UUID, PendingPlacement> PENDING = new ConcurrentHashMap<>();
    private static final Map<UUID, Long> COOLDOWN_UNTIL = new ConcurrentHashMap<>();

    private record PendingPlacement(UUID pair, long tick) {}

    @SubscribeEvent
    public void onCraft(PlayerEvent.ItemCraftedEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer) || !isVoidDoor(event.getCrafting())) return;
        event.getCrafting().getOrCreateTag().putString(PAIR, UUID.randomUUID().toString());
    }

    @SubscribeEvent
    public void onDoorUse(PlayerInteractEvent.RightClickBlock event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || !isVoidDoor(event.getItemStack())) return;
        try {
            UUID pair = UUID.fromString(event.getItemStack().getTag().getString(PAIR));
            PENDING.put(player.getUUID(), new PendingPlacement(pair, player.getServer().getTickCount()));
        } catch (IllegalArgumentException ignored) {
        }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void onPlace(BlockEvent.EntityPlaceEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)
            || !(event.getLevel() instanceof ServerLevel level)
            || !event.getPlacedBlock().is(Blocks.OAK_DOOR)) return;
        PendingPlacement pending = PENDING.remove(player.getUUID());
        if (pending == null || player.getServer().getTickCount() - pending.tick() > 2) return;
        BlockPos lower = lowerPos(event.getPos(), event.getPlacedBlock());
        VoidDoorData.get(player.getServer()).addDoor(pending.pair(),
            new VoidDoorData.DoorPosition(level.dimension().location().toString(), lower));
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void onBreak(BlockEvent.BreakEvent event) {
        if (!(event.getLevel() instanceof ServerLevel level) || !event.getState().is(Blocks.OAK_DOOR)) return;
        VoidDoorData.get(level.getServer()).removeDoor(level.dimension().location().toString(),
            lowerPos(event.getPos(), event.getState()));
    }

    @SubscribeEvent
    public void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !(event.player instanceof ServerPlayer player)) return;
        long now = player.getServer().getTickCount();
        if (COOLDOWN_UNTIL.getOrDefault(player.getUUID(), 0L) > now) return;
        BlockPos feet = player.blockPosition();
        BlockState state = player.level().getBlockState(feet);
        if (!state.is(Blocks.OAK_DOOR) || !state.getValue(DoorBlock.OPEN)) return;
        VoidDoorData.DoorPosition destination = VoidDoorData.get(player.getServer()).partner(
            player.level().dimension().location().toString(), lowerPos(feet, state));
        if (destination == null) return;
        ResourceLocation id = ResourceLocation.tryParse(destination.dimension());
        if (id == null) return;
        ServerLevel target = player.getServer().getLevel(ResourceKey.create(Registries.DIMENSION, id));
        if (target == null || !target.getBlockState(destination.pos()).is(Blocks.OAK_DOOR)) return;
        var facing = target.getBlockState(destination.pos()).getValue(DoorBlock.FACING);
        COOLDOWN_UNTIL.put(player.getUUID(), now + 60);
        player.teleportTo(target,
            destination.pos().getX() + 0.5D + facing.getStepX() * 1.25D,
            destination.pos().getY() + 0.1D,
            destination.pos().getZ() + 0.5D + facing.getStepZ() * 1.25D,
            player.getYRot(), player.getXRot());
        player.sendSystemMessage(Component.literal("Void Door").withStyle(ChatFormatting.DARK_PURPLE));
    }

    @SubscribeEvent
    public void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        PENDING.remove(event.getEntity().getUUID());
        COOLDOWN_UNTIL.remove(event.getEntity().getUUID());
    }

    private static boolean isVoidDoor(ItemStack stack) {
        return stack.is(Items.OAK_DOOR) && stack.hasTag() && stack.getTag().getBoolean(MARKER);
    }

    private static BlockPos lowerPos(BlockPos pos, BlockState state) {
        return state.getValue(DoorBlock.HALF) == DoubleBlockHalf.UPPER ? pos.below() : pos;
    }
}
