package pl.aridlin.psychiatrykroles.testing.mixin;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import pl.aridlin.psychiatrykroles.testing.TestingWorlds;

/** Keeps creative testing inventories outside the ordinary-world poker escrow. */
@Mixin(targets = "pl.aridlin.psychiatrykroles.PokerCommands", remap = false)
public abstract class PokerBankMixin {
    @Inject(method = "exchangeInStack(Lnet/minecraft/server/level/ServerPlayer;Lnet/minecraft/world/item/ItemStack;I)I",
        at = @At("HEAD"), cancellable = true, require = 1, remap = false)
    private static void testing$deposit(ServerPlayer player, ItemStack held, int count,
                                        CallbackInfoReturnable<Integer> result) {
        if (testing$blocked(player)) result.setReturnValue(0);
    }

    @Inject(method = "withdrawLot(Lnet/minecraft/server/level/ServerPlayer;JI)I",
        at = @At("HEAD"), cancellable = true, require = 1, remap = false)
    private static void testing$withdraw(ServerPlayer player, long lot, int count,
                                         CallbackInfoReturnable<Integer> result) {
        if (testing$blocked(player)) result.setReturnValue(0);
    }

    private static boolean testing$blocked(ServerPlayer player) {
        if (!TestingWorlds.testing(player.level().dimension())) return false;
        player.sendSystemMessage(Component.literal("Poker item exchange is disabled in testing worlds. Use /testworld back to restore your normal inventory first.")
            .withStyle(ChatFormatting.RED));
        return true;
    }
}
