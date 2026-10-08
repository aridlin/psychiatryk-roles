package pl.aridlin.psychiatrykroles.compat;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.lang.reflect.*;
import java.nio.file.*;
import java.util.*;
import java.util.zip.*;
import org.objectweb.asm.*;
import org.objectweb.asm.tree.*;
import org.objectweb.asm.util.*;
import org.spongepowered.asm.launch.MixinBootstrap;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.transformer.IMixinTransformer;

/** Executes the real legacy control factory and actual Sodium constructor without a window. */
public final class EmfNativeMixinTest {
 private static int checks;
 private static final String TARGET=EmfOptionsCompatPlugin.TARGET;
 private static final String ENUM=EmfOptionsCompatPlugin.ENUM;
 private static final String MIXIN=EmfOptionsCompatPlugin.MIXIN;
 private static final String DESC="(Lnet/caffeinemc/mods/sodium/client/gui/options/OptionImpl;)Lnet/caffeinemc/mods/sodium/client/gui/options/control/Control;";
 private static void check(boolean ok){checks++;if(!ok)throw new AssertionError("check"+checks);}
 private static ClassNode node(byte[] b){var n=new ClassNode();new ClassReader(b).accept(n,0);return n;}
 private static byte[] nativeBytes(String jar,String name)throws Exception{try(var z=new ZipFile(jar)){return z.getInputStream(z.getEntry(name.replace('.','/')+".class")).readAllBytes();}}
 private static String trace(MethodNode m){var t=new Textifier();m.accept(new TraceMethodVisitor(t));var s=new StringWriter();t.print(new PrintWriter(s));return s.toString().replaceAll("(?m)^   FRAME.*\\n","");}
 private static MethodNode method(ClassNode n,String name,String desc){return n.methods.stream().filter(m->m.name.equals(name)&&m.desc.equals(desc)).findFirst().orElseThrow();}
 private static final class Defined extends ClassLoader {
  Defined(){super(EmfNativeMixinTest.class.getClassLoader());}
  Class<?> define(byte[] b){return defineClass(TARGET,b,0,b.length);}
 }
 private static Object actualControl(byte[] b,boolean repaired)throws Exception{
  Class<?> target=new Defined().define(b);
  Class<?> option=Class.forName("net.caffeinemc.mods.sodium.client.gui.options.OptionImpl",false,EmfNativeMixinTest.class.getClassLoader());
  Method factory=target.getDeclaredMethod("lambda$create$23",option);factory.setAccessible(true);
  try {
   Object value=factory.invoke(null,new Object[]{null});check(repaired);
   Class<?> control=value.getClass();check(control.getName().equals("net.caffeinemc.mods.sodium.client.gui.options.control.CyclingControl"));
   Field values=control.getDeclaredField("allowedValues"),names=control.getDeclaredField("names");values.setAccessible(true);names.setAccessible(true);
   Object[] enums=(Object[])values.get(value),labels=(Object[])names.get(value);
   check(enums.length==5&&labels.length==5);
   var expectedEnums=List.of("NORMAL","LINES_AND_TEXTURE","LINES_AND_TEXTURE_FLASH","LINES","NONE");
   var expectedKeys=List.of("normal","lines_texture","lines_texture_flash","lines","none");
   for(int i=0;i<5;i++){
    check(((Enum<?>)enums[i]).name().equals(expectedEnums.get(i)));
    Object contents=labels[i].getClass().getMethod("getContents").invoke(labels[i]);
    String key=(String)contents.getClass().getMethod("getKey").invoke(contents);
    check(key.equals("entity_model_features.config.render."+expectedKeys.get(i)));
   }
   return value;
  }catch(InvocationTargetException e){check(!repaired&&e.getCause() instanceof IllegalArgumentException&&e.getCause().getMessage().contains("Mismatch between universe length and names array length"));return null;}
 }
 private static void writeVariant(byte[] original,String mode,Path out)throws Exception{
  ClassNode n=node(original);MethodNode m=method(n,"lambda$create$23",DESC);
  if(mode.equals("make-fixed")){
   m.instructions.clear();m.tryCatchBlocks.clear();m.localVariables.clear();
   String c="net/caffeinemc/mods/sodium/client/gui/options/control/CyclingControl";
   m.instructions.add(new TypeInsnNode(Opcodes.NEW,c));m.instructions.add(new InsnNode(Opcodes.DUP));m.instructions.add(new VarInsnNode(Opcodes.ALOAD,0));
   m.instructions.add(new LdcInsnNode(org.objectweb.asm.Type.getObjectType(ENUM.replace('.','/'))));
   m.instructions.add(new InsnNode(Opcodes.ICONST_5));m.instructions.add(new TypeInsnNode(Opcodes.ANEWARRAY,"net/minecraft/network/chat/Component"));
   String[] keys={"normal","lines_texture","lines_texture_flash","lines","none"};
   for(int i=0;i<5;i++){
    m.instructions.add(new InsnNode(Opcodes.DUP));m.instructions.add(new InsnNode(Opcodes.ICONST_0+i));m.instructions.add(new LdcInsnNode("entity_model_features.config.render."+keys[i]));
    m.instructions.add(new MethodInsnNode(Opcodes.INVOKESTATIC,"net/minecraft/network/chat/Component","translatable","(Ljava/lang/String;)Lnet/minecraft/network/chat/MutableComponent;",true));m.instructions.add(new InsnNode(Opcodes.AASTORE));
   }
   m.instructions.add(new MethodInsnNode(Opcodes.INVOKESPECIAL,c,"<init>","(Lnet/caffeinemc/mods/sodium/client/gui/options/Option;Ljava/lang/Class;[Lnet/minecraft/network/chat/Component;)V",false));m.instructions.add(new InsnNode(Opcodes.ARETURN));m.maxStack=8;m.maxLocals=1;
  }else for(var i:m.instructions)if(i instanceof LdcInsnNode l&&l.cst.equals("entity_model_features.config.render.green"))l.cst="entity_model_features.config.render.unrecognized";
  var w=new ClassWriter(0);n.accept(w);Files.write(out,w.toByteArray());
 }
 public static void main(String[] args)throws Exception{
  String mode=args[0];MixinBootstrap.init();MixinEnvironment.getDefaultEnvironment().setSide(mode.equals("server")?MixinEnvironment.Side.SERVER:MixinEnvironment.Side.CLIENT);
  var plugin=new EmfOptionsCompatPlugin();
  check(!plugin.shouldApplyMixin("different.Target",MIXIN));check(!plugin.shouldApplyMixin(TARGET,"different.Mixin"));check(!EmfOptionsCompatPlugin.legacyMismatch(null,null));
  if(mode.equals("absent")){check(!plugin.shouldApplyMixin(TARGET,MIXIN));System.out.println("{\"success\":true,\"mode\":\"absent\",\"checks\":"+checks+",\"no_optional_dependency\":true}");return;}
  byte[] original=nativeBytes(System.getProperty("fixture.target.jar"),TARGET);
  if(mode.startsWith("make-")){writeVariant(original,mode,Path.of(args[1]));return;}
  boolean apply=mode.equals("old");
  check(plugin.shouldApplyMixin(TARGET,MIXIN)==(apply||mode.equals("server")));
  Mixins.addConfiguration("psychiatryk-emf-options-compat.mixins.json");
  var type=Class.forName("org.spongepowered.asm.mixin.transformer.MixinTransformer");var ctor=type.getDeclaredConstructor();ctor.setAccessible(true);
  byte[] transformed=((IMixinTransformer)ctor.newInstance()).transformClassBytes(TARGET,TARGET,original);
  if(apply){
   check(!Arrays.equals(original,transformed));var before=node(original);var after=node(transformed);check(before.fields.size()==after.fields.size());
   for(var m:before.methods)if(!m.name.equals("lambda$create$23"))check(trace(m).equals(trace(method(after,m.name,m.desc))));
   actualControl(original,false);actualControl(transformed,true);
   var helper=after.methods.stream().filter(m->m.name.contains("correctRenderModeLabels")).findFirst().orElseThrow();
   check(helper.desc.equals("([Lnet/minecraft/network/chat/Component;)[Lnet/minecraft/network/chat/Component;"));
   Files.write(Path.of(args[1]),transformed);
  }else{check(Arrays.equals(original,transformed));if(mode.equals("fixed"))actualControl(transformed,true);}
  System.out.println("{\"success\":true,\"mode\":\""+mode+"\",\"checks\":"+checks+",\"actual_sponge_transformer\":true,\"actual_control_factory\":"+(apply||mode.equals("fixed"))+",\"game_launched\":false}");
 }
}
