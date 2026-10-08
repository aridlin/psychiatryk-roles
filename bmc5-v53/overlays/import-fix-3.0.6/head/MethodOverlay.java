import java.nio.file.*;
import java.util.*;
import java.io.*;
import java.security.*;
import java.util.HexFormat;
import org.objectweb.asm.*;
import org.objectweb.asm.tree.*;
import org.objectweb.asm.util.*;
public class MethodOverlay {
 static ClassNode node(byte[] data){ClassNode n=new ClassNode();new ClassReader(data).accept(n,0);return n;}
 static String trace(MethodNode method){StringWriter s=new StringWriter();PrintWriter p=new PrintWriter(s);Textifier t=new Textifier();method.accept(new TraceMethodVisitor(t));t.print(p);p.flush();return s.toString();}
 static String sha(byte[] data)throws Exception{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(data));}
 public static void main(String[] args)throws Exception{
  byte[] original=Files.readAllBytes(Path.of(args[0])),compiled=Files.readAllBytes(Path.of(args[1]));
  ClassNode authored=node(compiled);MethodNode replacement=authored.methods.stream().filter(m->m.name.equals("animate")).findFirst().orElseThrow();
  ClassReader reader=new ClassReader(original);ClassWriter writer=new ClassWriter(reader,0);
  reader.accept(new ClassVisitor(Opcodes.ASM9,writer){public MethodVisitor visitMethod(int access,String name,String descriptor,String signature,String[] exceptions){if(name.equals(replacement.name)&&descriptor.equals(replacement.desc))return null;return super.visitMethod(access,name,descriptor,signature,exceptions);}public void visitEnd(){replacement.accept(writer);super.visitEnd();}},0);
  byte[] output=writer.toByteArray();ClassNode old=node(original),next=node(output);int unchanged=0;
  for(MethodNode method:old.methods){MethodNode after=next.methods.stream().filter(m->m.name.equals(method.name)&&m.desc.equals(method.desc)).findFirst().orElseThrow();if(!method.name.equals("animate")){if(!trace(method).equals(trace(after)))throw new AssertionError("Unrequested method modified: "+method.name);unchanged++;}}
  if(next.methods.size()!=old.methods.size())throw new AssertionError("Method set changed");
  Files.write(Path.of(args[2]),output);System.out.println("{\"success\":true,\"unchanged_methods\":"+unchanged+",\"changed_method\":\"animate\",\"class_sha256\":\""+sha(output)+"\"}");
 }
}
