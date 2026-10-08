import java.nio.file.*;
import java.util.*;
import java.io.*;
import java.security.*;
import java.util.HexFormat;
import org.objectweb.asm.*;
import org.objectweb.asm.tree.*;
import org.objectweb.asm.util.*;
/** Preserve the exact deployed client family; transplant only the requested hook methods. */
public final class MethodOverlay {
 static final Set<String> METHODS=Set.of("predict", "release", "exit");
 static final Set<String> FIELDS=Set.of("previousTugLook", "previousTugAnchor");
 static ClassNode node(byte[] data){ClassNode n=new ClassNode();new ClassReader(data).accept(n,0);return n;}
 static String trace(MethodNode method){StringWriter s=new StringWriter();PrintWriter p=new PrintWriter(s);Textifier t=new Textifier();method.accept(new TraceMethodVisitor(t));t.print(p);p.flush();return s.toString();}
 static String sha(byte[] data)throws Exception{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(data));}
 public static void main(String[] args)throws Exception{
  byte[] original=Files.readAllBytes(Path.of(args[0])),compiled=Files.readAllBytes(Path.of(args[1]));
  ClassNode authored=node(compiled),old=node(original);List<MethodNode> replacements=authored.methods.stream().filter(m->METHODS.contains(m.name)).toList();
  List<FieldNode> additions=authored.fields.stream().filter(f->FIELDS.contains(f.name)).toList();
  if(replacements.size()!=3||additions.size()!=2)throw new AssertionError("Wrong transplant scope");
  for(FieldNode f:additions)if(!f.desc.equals("Lnet/minecraft/world/phys/Vec3;")||f.value!=null||(f.access&Opcodes.ACC_STATIC)==0||old.fields.stream().anyMatch(x->x.name.equals(f.name)))throw new AssertionError("Non-null/default or existing field");
  ClassReader reader=new ClassReader(original);ClassWriter writer=new ClassWriter(reader,0);
  reader.accept(new ClassVisitor(Opcodes.ASM9,writer){
   public MethodVisitor visitMethod(int access,String name,String descriptor,String signature,String[] exceptions){if(replacements.stream().anyMatch(m->m.name.equals(name)&&m.desc.equals(descriptor)))return null;return super.visitMethod(access,name,descriptor,signature,exceptions);}
   public void visitEnd(){for(FieldNode f:additions)f.accept(writer);for(MethodNode m:replacements)m.accept(writer);super.visitEnd();}
  },0);
  byte[] output=writer.toByteArray();ClassNode next=node(output);int unchanged=0;
  for(MethodNode method:old.methods){MethodNode after=next.methods.stream().filter(m->m.name.equals(method.name)&&m.desc.equals(method.desc)).findFirst().orElseThrow();if(!METHODS.contains(method.name)){if(!trace(method).equals(trace(after)))throw new AssertionError("Unrequested method modified: "+method.name);unchanged++;}}
  if(next.methods.size()!=old.methods.size()||next.fields.size()!=old.fields.size()+2)throw new AssertionError("Method/field set changed");
  Files.write(Path.of(args[2]),output);System.out.println("{\"success\":true,\"unchanged_methods\":"+unchanged+",\"changed_methods\":[\"predict\",\"release\",\"exit\"],\"added_null_fields\":[\"previousTugLook\",\"previousTugAnchor\"],\"original_sha256\":\""+sha(original)+"\",\"authored_sha256\":\""+sha(compiled)+"\",\"class_sha256\":\""+sha(output)+"\"}");
 }
}
