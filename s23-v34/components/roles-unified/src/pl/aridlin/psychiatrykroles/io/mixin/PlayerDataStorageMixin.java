package pl.aridlin.psychiatrykroles.io.mixin;
import java.io.File;
import java.util.Optional;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.storage.PlayerDataStorage;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.*;
import pl.aridlin.psychiatrykroles.io.PlayerSaves;
@Mixin(PlayerDataStorage.class)
public abstract class PlayerDataStorageMixin {
 @Shadow @Final private File playerDir;
 @Inject(method="save",at=@At("HEAD"),cancellable=true)
 private void goplanska$asyncSave(Player player,CallbackInfo ci){
  try{PlayerSaves.save((PlayerDataStorage)(Object)this,playerDir,player);}
  catch(Exception e){org.slf4j.LoggerFactory.getLogger("GoplanskaPlayerSaves").error("Failed to capture player save for {}",player.getName().getString(),e);}
  ci.cancel();
 }
 @Inject(method="load(Lnet/minecraft/world/entity/player/Player;)Ljava/util/Optional;",at=@At("HEAD"))
 private void goplanska$readBarrier(Player player,CallbackInfoReturnable<Optional<CompoundTag>> ci)throws java.io.IOException{PlayerSaves.await((PlayerDataStorage)(Object)this);}
}
