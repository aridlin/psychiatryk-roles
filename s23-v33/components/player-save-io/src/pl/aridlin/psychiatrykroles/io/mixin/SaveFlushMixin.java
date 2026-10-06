package pl.aridlin.psychiatrykroles.io.mixin;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.*;
@Mixin(net.minecraft.server.MinecraftServer.class)
public abstract class SaveFlushMixin {
 @Inject(method="saveAllChunks",at=@At("RETURN"))
 private void goplanska$durableFlush(boolean suppressLog,boolean flush,boolean forced,CallbackInfoReturnable<Boolean> cir)throws java.io.IOException{
  if(flush){
   java.io.IOException failure=null;
   try{pl.aridlin.psychiatrykroles.io.SableIoBarrier.flushAll();}catch(java.io.IOException e){failure=e;}
   try{pl.aridlin.psychiatrykroles.io.PlayerSaves.flushAll();}catch(java.io.IOException e){if(failure==null)failure=e;else failure.addSuppressed(e);}
   if(failure!=null)throw failure;
  }
 }
}
