import java.nio.file.*;
import java.util.*;
import java.io.*;
import java.security.*;
import org.objectweb.asm.*;
import org.objectweb.asm.tree.*;
import org.objectweb.asm.util.*;
/** Preserve the deployed family and modify only handling functions named explicitly here. */
public final class MethodOverlay {
 static ClassNode node(byte[] data){ClassNode n=new ClassNode();new ClassReader(data).accept(n,0);return n;}
 static String trace(MethodNode method){StringWriter s=new StringWriter();PrintWriter p=new PrintWriter(s);Textifier t=new Textifier();method.accept(new TraceMethodVisitor(t));t.print(p);p.flush();return s.toString();}
 static String sha(byte[] data)throws Exception{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(data));}
 public static void main(String[] args)throws Exception{
  if(args.length!=4)throw new IllegalArgumentException("original donor output Scooter|ScooterHandling");
  boolean scooter=args[3].equals("Scooter");
  if(!scooter&&!args[3].equals("ScooterHandling"))throw new IllegalArgumentException("Unexpected class family");
  Set<String> methods=scooter?Set.of("getRiddenRotation"):Set.of("turnRate","steering","steeringYawStep");
  byte[] original=Files.readAllBytes(Path.of(args[0])),compiled=Files.readAllBytes(Path.of(args[1]));
  ClassNode authored=node(compiled),old=node(original);
  if(!old.name.equals("pl/aridlin/kukirin/"+args[3])||!authored.name.equals(old.name))throw new AssertionError("Wrong target class");
  List<MethodNode> replacements=authored.methods.stream().filter(m->methods.contains(m.name)).toList();
  if(replacements.size()!=(scooter?1:6))throw new AssertionError("Wrong transplant scope");
  ClassReader reader=new ClassReader(original);ClassWriter writer=new ClassWriter(reader,0);
  reader.accept(new ClassVisitor(Opcodes.ASM9,writer){
   public MethodVisitor visitMethod(int access,String name,String descriptor,String signature,String[] exceptions){if(replacements.stream().anyMatch(m->m.name.equals(name)&&m.desc.equals(descriptor)))return null;return super.visitMethod(access,name,descriptor,signature,exceptions);}
   public void visitEnd(){for(MethodNode m:replacements)m.accept(writer);super.visitEnd();}
  },0);
  byte[] output=writer.toByteArray();ClassNode next=node(output);int unchanged=0;
  for(MethodNode method:old.methods){MethodNode after=next.methods.stream().filter(m->m.name.equals(method.name)&&m.desc.equals(method.desc)).findFirst().orElseThrow();if(!methods.contains(method.name)){if(!trace(method).equals(trace(after)))throw new AssertionError("Unrequested method modified: "+method.name);unchanged++;}}
  if(next.methods.size()!=old.methods.size()+(scooter?0:3)||next.fields.size()!=old.fields.size())throw new AssertionError("Unexpected method/field set changed");
  for(FieldNode field:old.fields)if(next.fields.stream().noneMatch(f->f.name.equals(field.name)&&f.desc.equals(field.desc)&&Objects.equals(f.value,field.value)))throw new AssertionError("Field changed");
  Files.write(Path.of(args[2]),output);
  System.out.println("{\"success\":true,\"unchanged_methods\":"+unchanged+",\"added_methods\":"+(scooter?0:3)+",\"changed_method_names\":\""+String.join(",",methods)+"\",\"original_sha256\":\""+sha(original)+"\",\"authored_sha256\":\""+sha(compiled)+"\",\"class_sha256\":\""+sha(output)+"\"}");
 }
}
