package pl.aridlin.psychiatrykroles.music;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.lang.reflect.Constructor;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.ZipFile;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.*;
import org.objectweb.asm.util.Textifier;
import org.objectweb.asm.util.TraceMethodVisitor;
import org.spongepowered.asm.launch.MixinBootstrap;
import org.spongepowered.asm.mixin.MixinEnvironment;
import org.spongepowered.asm.mixin.Mixins;
import org.spongepowered.asm.mixin.transformer.IMixinTransformer;
public final class NativeMusicMixinTest {
    static int checks;
    static void check(boolean value,String why){checks++;if(!value)throw new AssertionError(why);}
    static ClassNode read(byte[] bytes){ClassNode n=new ClassNode();new ClassReader(bytes).accept(n,ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);return n;}
    static String trace(MethodNode m){Textifier t=new Textifier();m.accept(new TraceMethodVisitor(t));StringWriter s=new StringWriter();t.print(new PrintWriter(s));return s.toString();}
    static MethodNode method(ClassNode n,String name,String desc){return n.methods.stream().filter(m->m.name.equals(name)&&m.desc.equals(desc)).findFirst().orElseThrow();}
    static byte[] entry(Path jar,String name)throws Exception{try(ZipFile z=new ZipFile(jar.toFile())){return z.getInputStream(z.getEntry(name.replace('.','/')+".class")).readAllBytes();}}
    public static void main(String[] args)throws Exception{
        MixinBootstrap.init();MixinEnvironment.getDefaultEnvironment().setSide(MixinEnvironment.Side.CLIENT);
        Mixins.addConfiguration("psychiatryk-music-pause.mixins.json");
        Class<?> type=Class.forName("org.spongepowered.asm.mixin.transformer.MixinTransformer");
        Constructor<?> ctor=type.getDeclaredConstructor();ctor.setAccessible(true);IMixinTransformer transformer=(IMixinTransformer)ctor.newInstance();
        Path nativeJar=Path.of(args[0]),out=Path.of(args[1]);Files.createDirectories(out);
        for(String target:List.of("net.minecraft.client.sounds.SoundManager","net.minecraft.client.sounds.SoundEngine","net.minecraft.client.sounds.MusicManager")){
            byte[] original=entry(nativeJar,target);byte[] transformed=transformer.transformClassBytes(target,target,original);ClassNode before=read(original),after=read(transformed);
            check(!java.util.Arrays.equals(original,transformed),"native target transformed "+target);
            if(target.endsWith("MusicManager")){
                check(after.fields.size()==before.fields.size()+1,"only owner pause state added");
                MethodNode tick=method(after,"tick","()V");
                check(!trace(tick).equals(trace(method(before,"tick","()V"))),"actual tick HEAD injection applied");
                check(after.methods.stream().anyMatch(m->m.name.contains("pauseBehindJukebox")),"callback merged");
                List<String> calls=new ArrayList<>();for(MethodNode m:after.methods)for(AbstractInsnNode ins:m.instructions)if(ins instanceof MethodInsnNode call)calls.add(call.owner+"."+call.name);
                check(calls.contains("pl/aridlin/kukirin/ScooterAudioClient.hasAudibleMusic"),"prepared/audible client gate linked");
                check(calls.contains("pl/aridlin/psychiatrykroles/music/VanillaJukeboxAudio.audible"),"ordinary records gate linked");
                check(calls.contains("pl/aridlin/psychiatrykroles/music/BackgroundMusicPauseState.update"),"lifecycle controller linked");
                for(MethodNode prior:before.methods)if(!prior.name.equals("tick")&&!prior.name.equals("<init>"))check(trace(prior).equals(trace(method(after,prior.name,prior.desc))),"native method preserved "+prior.name);
            }else{
                String accessor=target.endsWith("SoundManager")?"pl/aridlin/psychiatrykroles/music/SoundManagerMusicAccessor":"pl/aridlin/psychiatrykroles/music/SoundEngineMusicAccessor";
                check(after.interfaces.contains(accessor),"native accessor actually merged");
                check(after.fields.size()==before.fields.size(),"native fields preserved");
                for(MethodNode prior:before.methods)check(trace(prior).equals(trace(method(after,prior.name,prior.desc))),"native sound method preserved "+prior.name);
            }
            Files.write(out.resolve(target.substring(target.lastIndexOf('.')+1)+".class"),transformed);
        }
        byte[] prior=entry(Path.of(args[2]),"pl.aridlin.kukirin.ScooterAudioClient");byte[] next=Files.readAllBytes(Path.of(args[3]).resolve("pl/aridlin/kukirin/ScooterAudioClient.class"));ClassNode before=read(prior),after=read(next);
        check(after.fields.size()==before.fields.size(),"audio fields unchanged");
        for(MethodNode m:before.methods)check(trace(m).equals(trace(method(after,m.name,m.desc))),"existing transport/client method bytecode unchanged "+m.name);
        check(after.methods.size()==before.methods.size()+2,"only two read-only audio observer methods added");
        System.out.println("{\"success\":true,\"checks\":"+checks+",\"actual_sponge_native_transformation\":true,\"all_prior_audio_client_methods_preserved\":true,\"game_launched\":false}");
    }
}
