package pl.aridlin.spyglasscheck;

import com.mojang.authlib.GameProfile;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.common.util.TriState;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import pl.aridlin.psychiatrykroles.CustomPresets;
import pl.aridlin.psychiatrykroles.PsychiatrykRoles;

/** Isolated dedicated-server check; this class must never ship in the release JAR. */
@Mod("goplanska_spyglass_check")
public final class SpyglassCheck {
    private int ticks;

    public SpyglassCheck() {
        NeoForge.EVENT_BUS.addListener(this::tick);
    }

    private static void require(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }

    private void tick(ServerTickEvent.Post event) {
        if (++ticks != 40) return;
        try {
            var server = event.getServer();
            var level = server.overworld();
            var player = FakePlayerFactory.get(level, new GameProfile(UUID.randomUUID(), "SpyglassQA"));
            var presets = PsychiatrykRoles.class.getDeclaredField("ITEM_PRESETS");
            presets.setAccessible(true);
            require(((List<?>) presets.get(null)).contains("spyglass"), "command preset suggestions include spyglass");
            var maker = PsychiatrykRoles.class.getDeclaredMethod("presetItem", String.class,
                    net.minecraft.server.level.ServerPlayer.class, long.class);
            maker.setAccessible(true);
            ItemStack stack = (ItemStack) maker.invoke(null, "spyglass", player, 0L);
            require(stack.is(Items.SPYGLASS) && stack.getCount() == 1, "preset returns a vanilla spyglass");
            require(CustomPresets.isConsultantSpyglass(stack), "spyglass is recognized consultant equipment");
            require(stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag()
                    .getBoolean("psychiatrykConsultantItem"), "consultant tag is present");
            long expires = System.currentTimeMillis() + 60_000L;
            ItemStack timed = (ItemStack) maker.invoke(null, "spyglass", player, expires);
            require(timed.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag()
                    .getLong("psychiatrykConsultantExpiresAt") == expires,
                    "timed preset retains its expiry");
            require(!CustomPresets.isConsultantSpyglass(new ItemStack(Items.SPYGLASS)),
                    "ordinary spyglass is not granted consultant equipment privileges");
            player.setItemInHand(InteractionHand.MAIN_HAND, stack);
            var item = new PlayerInteractEvent.RightClickItem(player, InteractionHand.MAIN_HAND);
            NeoForge.EVENT_BUS.post(item);
            require(!item.isCanceled(), "consultant right-click item leaves vanilla spyglass use active");
            BlockPos pos = new BlockPos(10, 100, 10);
            var hit = new BlockHitResult(Vec3.atCenterOf(pos), Direction.UP, pos, false);
            var block = new PlayerInteractEvent.RightClickBlock(player, InteractionHand.MAIN_HAND, pos, hit);
            NeoForge.EVENT_BUS.post(block);
            require(!block.isCanceled() && block.getUseBlock() == TriState.FALSE
                    && block.getUseItem() == TriState.TRUE,
                    "pointing at a block uses spyglass without activating the block");
            System.out.println("SPYGLASS_CHECK PASS list + vanilla item + consultant tag + expiry + air/block use");
        } catch (Throwable error) {
            error.printStackTrace();
            System.out.println("SPYGLASS_CHECK FAIL " + error);
        }
    }
}
