package pl.aridlin.psychiatrykroles.mixin;
import pl.aridlin.psychiatrykroles.VillagerTradeRebalance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.nbt.CompoundTag;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
@Mixin(Entity.class)
public abstract class VillagerSaveMixin {
 @Inject(method="saveWithoutId",at=@At("HEAD"))
 private void preserveOffers(CompoundTag tag,CallbackInfoReturnable<CompoundTag> ci){if((Object)this instanceof Villager v){VillagerTradeRebalance.restoreSnapshot(v);VillagerTradeRebalance.saveSnapshot(v);}}
}
