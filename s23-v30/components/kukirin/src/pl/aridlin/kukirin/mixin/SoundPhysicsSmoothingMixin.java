package pl.aridlin.kukirin.mixin;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.invoke.arg.Args;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import pl.aridlin.kukirin.SoundEnvironmentSmoothing;
@Pseudo
@Mixin(targets="com.sonicether.soundphysics.SoundPhysics",remap=false)
public abstract class SoundPhysicsSmoothingMixin {
 @Inject(method="onPlaySound",at=@At("HEAD"),require=0,remap=false)
 private static void goplanska$newSource(double x,double y,double z,int source,CallbackInfo ci){SoundEnvironmentSmoothing.reset(source);}
 @ModifyArgs(method={"evaluateEnvironment","setDefaultEnvironment"},at=@At(value="INVOKE",target="Lcom/sonicether/soundphysics/SoundPhysics;setEnvironment(IFFFFFFFFFF)V"),require=0,remap=false)
 private static void goplanska$smooth(Args args){
  float[] target=new float[10];for(int i=0;i<10;i++)target[i]=args.get(i+1);
  float[] values=SoundEnvironmentSmoothing.apply(args.get(0),target,System.nanoTime());
  for(int i=0;i<10;i++)args.set(i+1,values[i]);
 }
}
