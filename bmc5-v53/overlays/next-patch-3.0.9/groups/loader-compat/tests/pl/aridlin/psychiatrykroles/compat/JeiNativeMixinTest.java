package pl.aridlin.psychiatrykroles.compat;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.lang.reflect.Constructor;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
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

public final class JeiNativeMixinTest {
    private static int checks;
    private static final String TARGET="mezz.jei.common.transfer.TransferOperation";
    private static final String MIXIN="pl.aridlin.psychiatrykroles.compat.JeiUnitTransferMixin";
    private static void check(boolean value) { checks++; if(!value)throw new AssertionError("check "+checks); }
    private static ClassNode read(byte[] bytes) {ClassNode n=new ClassNode();new ClassReader(bytes).accept(n,0);return n;}
    private static String trace(MethodNode m) {Textifier t=new Textifier();m.accept(new TraceMethodVisitor(t));StringWriter s=new StringWriter();t.print(new PrintWriter(s));return s.toString();}
    private static MethodNode method(ClassNode n,String name,String descriptor){return n.methods.stream().filter(m->m.name.equals(name)&&m.desc.equals(descriptor)).findFirst().orElseThrow();}
    private static final class Defined extends ClassLoader {
        Defined(){super(JeiNativeMixinTest.class.getClassLoader());}
        Class<?> define(byte[] value){return defineClass(TARGET,value,0,value.length);}
        Class<?> define(String name,byte[] value){return defineClass(name,value,0,value.length);}
    }
    public static void main(String[] args) throws Exception {
        String mode=args[0];
        MixinBootstrap.init();
        MixinEnvironment.getDefaultEnvironment().setSide(MixinEnvironment.Side.CLIENT);
        Mixins.addConfiguration("psychiatryk-jei-compat.mixins.json");
        JeiTransferCompatPlugin plugin=new JeiTransferCompatPlugin();
        check(!plugin.shouldApplyMixin("different.Target",MIXIN));
        check(!plugin.shouldApplyMixin(TARGET,"different.Mixin"));
        check(!JeiTransferCompatPlugin.legacyUnitRecord(null));
        if(mode.equals("absent")){
            check(!plugin.shouldApplyMixin(TARGET,MIXIN));
            System.out.println("{\"success\":true,\"checks\":"+checks+",\"mode\":\"absent\",\"no_jei_dependency\":true}");return;
        }
        byte[] original;
        try(ZipFile z=new ZipFile(System.getProperty("fixture.target.jar"))){original=z.getInputStream(z.getEntry(TARGET.replace('.','/')+".class")).readAllBytes();}
        ClassNode before=read(original);
        check(plugin.shouldApplyMixin(TARGET,MIXIN)==mode.equals("old"));
        Class<?> transformerType=Class.forName("org.spongepowered.asm.mixin.transformer.MixinTransformer");
        Constructor<?> ctor=transformerType.getDeclaredConstructor();ctor.setAccessible(true);
        IMixinTransformer transformer=(IMixinTransformer)ctor.newInstance();
        byte[] transformed=transformer.transformClassBytes(TARGET,TARGET,original);
        ClassNode after=read(transformed);
        if(mode.equals("old")){
            check(!Arrays.equals(original,transformed));
            check(after.methods.size()==before.methods.size()+1);
            check(after.fields.size()==before.fields.size());
            check(after.recordComponents.size()==before.recordComponents.size());
            MethodNode count=method(after,"count","()I");
            int[] instructions=Arrays.stream(count.instructions.toArray()).mapToInt(AbstractInsnNode::getOpcode).filter(n->n>=0).toArray();
            check(Arrays.equals(instructions,new int[]{Opcodes.ICONST_1,Opcodes.IRETURN}));
            check((count.access&Opcodes.ACC_PUBLIC)!=0&&(count.access&Opcodes.ACC_STATIC)==0);
            for(MethodNode old:before.methods)check(trace(old).equals(trace(method(after,old.name,old.desc))));
            Defined loader=new Defined();Class<?> actual=loader.define(transformed);
            check(actual.getRecordComponents().length==2);
            check(actual.getDeclaredMethod("count").getReturnType()==int.class);
            Object value=actual.getConstructor(int.class,int.class).newInstance(7,3);
            check((Integer)actual.getMethod("count").invoke(value)==1);
            check((Integer)actual.getMethod("inventorySlotId").invoke(value)==7);
            check((Integer)actual.getMethod("craftingSlotId").invoke(value)==3);
            String coreName="net.p3pp3rf1y.sophisticatedcore.compat.recipeviewers.jei.JeiCraftingContainerRecipeTransferHandlerBase";
            byte[] coreBytes;
            try(ZipFile z=new ZipFile(System.getProperty("fixture.core.jar"))){coreBytes=z.getInputStream(z.getEntry(coreName.replace('.','/')+".class")).readAllBytes();}
            Class<?> core=loader.define(coreName,coreBytes);
            var conversion=core.getDeclaredMethod("lambda$toSlotTransfers$12",actual);conversion.setAccessible(true);
            Object slotTransfer=conversion.invoke(null,value);
            check((Integer)slotTransfer.getClass().getMethod("count").invoke(slotTransfer)==1);
            check((Integer)slotTransfer.getClass().getMethod("inventorySlotId").invoke(slotTransfer)==7);
            check((Integer)slotTransfer.getClass().getMethod("craftingSlotId").invoke(slotTransfer)==3);
            Files.write(Path.of(args[1]),transformed);
        }else{
            check(Arrays.equals(original,transformed));
            check(after.methods.stream().filter(m->m.name.equals("count")&&m.desc.equals("()I")).count()==1);
            check(after.recordComponents.size()==3);
        }
        System.out.println("{\"success\":true,\"checks\":"+checks+",\"mode\":\""+mode+"\",\"actual_sponge_transformer\":true,\"game_launched\":false}");
    }
}
