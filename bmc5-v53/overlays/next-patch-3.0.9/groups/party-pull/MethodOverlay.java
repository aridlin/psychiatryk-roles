import java.nio.file.*;
import java.util.*;
import java.io.*;
import java.security.*;
import org.objectweb.asm.*;
import org.objectweb.asm.tree.*;
import org.objectweb.asm.util.*;

/** Keep the deployed camera/input/bootstrap bytecode exact. */
public final class MethodOverlay {
 static final Set<String> REPLACE=Set.of("grapple", "predict", "entityTarget");
 static final Set<String> ADD=Set.of("candidateEntity");
 static ClassNode node(byte[] data){ClassNode n=new ClassNode();new ClassReader(data).accept(n,0);return n;}
 static String trace(MethodNode method){StringWriter s=new StringWriter();PrintWriter p=new PrintWriter(s);Textifier t=new Textifier();method.accept(new TraceMethodVisitor(t));t.print(p);p.flush();return s.toString();}
 static String sha(byte[] data)throws Exception{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(data));}
 public static void main(String[] args)throws Exception{
  byte[] original=Files.readAllBytes(Path.of(args[0])),compiled=Files.readAllBytes(Path.of(args[1]));
  ClassNode authored=node(compiled),old=node(original);List<MethodNode> replacements=authored.methods.stream().filter(m->REPLACE.contains(m.name)||ADD.contains(m.name)).toList();
  if(replacements.size()!=4)throw new AssertionError("Wrong transplant scope");
  for(String n:ADD)if(old.methods.stream().anyMatch(m->m.name.equals(n)))throw new AssertionError("Existing helper "+n);
  ClassReader reader=new ClassReader(original);ClassWriter writer=new ClassWriter(reader,0);
  reader.accept(new ClassVisitor(Opcodes.ASM9,writer){
   public MethodVisitor visitMethod(int access,String name,String descriptor,String signature,String[] exceptions){if(REPLACE.contains(name))return null;return super.visitMethod(access,name,descriptor,signature,exceptions);}
   public void visitEnd(){for(MethodNode m:replacements)m.accept(writer);super.visitEnd();}
  },0);
  byte[] output=writer.toByteArray();ClassNode next=node(output);int unchanged=0;
  for(MethodNode method:old.methods){MethodNode after=next.methods.stream().filter(m->m.name.equals(method.name)&&m.desc.equals(method.desc)).findFirst().orElseThrow();if(!REPLACE.contains(method.name)){if(!trace(method).equals(trace(after)))throw new AssertionError("Unrequested method modified: "+method.name);unchanged++;}}
  if(next.methods.size()!=old.methods.size()+ADD.size()||next.fields.size()!=old.fields.size())throw new AssertionError("Unexpected member change");
  Files.write(Path.of(args[2]),output);
  System.out.println("{\"success\":true,\"unchanged_methods\":"+unchanged+",\"changed_methods\":[\"grapple\",\"predict\",\"entityTarget\"],\"added_methods\":[\"candidateEntity\"],\"original_sha256\":\""+sha(original)+"\",\"authored_sha256\":\""+sha(compiled)+"\",\"class_sha256\":\""+sha(output)+"\"}");
 }
}
