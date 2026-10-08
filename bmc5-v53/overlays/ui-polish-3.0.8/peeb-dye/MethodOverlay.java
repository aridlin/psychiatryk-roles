import java.nio.file.*;
import java.util.*;
import java.io.*;
import org.objectweb.asm.*;
import org.objectweb.asm.tree.*;
import org.objectweb.asm.util.*;
/** Keep all deployed animation/physics/render methods outside the dye hook unchanged. */
public final class MethodOverlay {
 static ClassNode node(byte[] data){ClassNode n=new ClassNode();new ClassReader(data).accept(n,0);return n;}
 static String trace(MethodNode method){StringWriter s=new StringWriter();PrintWriter p=new PrintWriter(s);Textifier t=new Textifier();method.accept(new TraceMethodVisitor(t));t.print(p);p.flush();return s.toString();}
 static boolean wanted(MethodNode m,boolean mesh){return mesh?(m.name.equals("<init>")&&m.desc.equals("(Lcom/google/gson/JsonObject;)V")||m.name.equals("draw")||m.name.equals("skin")||m.name.equals("protectEyes")):m.name.equals("player");}
 public static void main(String[] args)throws Exception{
  boolean mesh=args[3].equals("mesh");byte[] original=Files.readAllBytes(Path.of(args[0])),compiled=Files.readAllBytes(Path.of(args[1]));
  ClassNode authored=node(compiled),old=node(original);List<MethodNode> replacements=authored.methods.stream().filter(m->wanted(m,mesh)).toList();
  List<FieldNode> additions=mesh?authored.fields.stream().filter(f->Set.of("skinMaterials","bodyTint","eyeVertices","eyePupils").contains(f.name)).toList():List.of();
  if(replacements.size()!=(mesh?5:1)||additions.size()!=(mesh?4:0))throw new AssertionError("Wrong dye transplant scope");
  for(FieldNode f:additions)if(old.fields.stream().anyMatch(x->x.name.equals(f.name)))throw new AssertionError("Existing field");
  ClassReader reader=new ClassReader(original);ClassWriter writer=new ClassWriter(reader,0);
  reader.accept(new ClassVisitor(Opcodes.ASM9,writer){
   public MethodVisitor visitMethod(int access,String name,String descriptor,String signature,String[] exceptions){if(replacements.stream().anyMatch(m->m.name.equals(name)&&m.desc.equals(descriptor)))return null;return super.visitMethod(access,name,descriptor,signature,exceptions);}
   public void visitEnd(){for(FieldNode f:additions)f.accept(writer);for(MethodNode m:replacements)m.accept(writer);super.visitEnd();}
  },0);
  byte[] output=writer.toByteArray();ClassNode next=node(output);int unchanged=0;
  for(MethodNode method:old.methods){MethodNode after=next.methods.stream().filter(m->m.name.equals(method.name)&&m.desc.equals(method.desc)).findFirst().orElseThrow();if(!wanted(method,mesh)){if(!trace(method).equals(trace(after)))throw new AssertionError("Unrequested method modified: "+method.name);unchanged++;}}
  if(next.methods.size()!=old.methods.size()+(mesh?2:0)||next.fields.size()!=old.fields.size()+additions.size())throw new AssertionError("Unexpected method/field set");
  Path out=Path.of(args[2]);Files.createDirectories(out.getParent());Files.write(out,output);System.out.println("{\"success\":true,\"unchanged_methods\":"+unchanged+",\"added_methods\":"+(mesh?2:0)+",\"added_fields\":"+additions.size()+"}");
 }
}
