package pl.aridlin.psychiatrykroles.mixin;

import net.minecraft.world.item.trading.MerchantOffer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Only offers marked by the Wieśniuk adapter are unlimited. */
@Mixin(MerchantOffer.class)
public abstract class UnlimitedVillagerOfferMixin {
    @Inject(method="isOutOfStock",at=@At("HEAD"),cancellable=true,require=1)
    private void psychiatryk$unlimitedStock(CallbackInfoReturnable<Boolean> callback) {
        if (((MerchantOffer)(Object)this).getMaxUses()==Integer.MAX_VALUE) callback.setReturnValue(false);
    }

    @Inject(method="increaseUses",at=@At("HEAD"),cancellable=true,require=1)
    private void psychiatryk$preserveOrdinaryStock(CallbackInfo callback) {
        // The villager's stock is shared. Unlimited class purchases must not
        // exhaust the remaining stock for the next ordinary customer.
        if (((MerchantOffer)(Object)this).getMaxUses()==Integer.MAX_VALUE) callback.cancel();
    }

    @Inject(method="updateDemand",at=@At("HEAD"),cancellable=true,require=1)
    private void psychiatryk$preserveUnlimitedDemand(CallbackInfo callback) {
        // Vanilla calculates uses*2-maxUses; an unlimited stock sentinel would
        // otherwise overflow or turn all later normal-player prices into one emerald.
        if (((MerchantOffer)(Object)this).getMaxUses()==Integer.MAX_VALUE) callback.cancel();
    }
}
