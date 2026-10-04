package pl.aridlin.kukirin.mixin;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.network.chat.contents.TranslatableContents;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
@Mixin(Enchantment.class)
public abstract class ScooterEnchantMixin {
 private static final java.util.Set<String> SUPPORTED=java.util.Set.of("frost_walker","wind_burst","piercing","loyalty","feather_falling","depth_strider","soul_speed","efficiency","quick_charge","sweeping_edge","density","knockback","enchantment.goplanska_kukirin.lunge");
 @Inject(method={"isSupportedItem","isPrimaryItem"},at=@At("HEAD"),cancellable=true)
 private void scooterEnchantment(ItemStack stack,CallbackInfoReturnable<Boolean> ci){if(!stack.is(pl.aridlin.kukirin.Kukirin.ITEM.get()))return;var desc=((Enchantment)(Object)this).description();boolean supported=desc.getContents() instanceof TranslatableContents t&&SUPPORTED.contains(t.getKey().replace("enchantment.minecraft.",""));ci.setReturnValue(supported);}
}
