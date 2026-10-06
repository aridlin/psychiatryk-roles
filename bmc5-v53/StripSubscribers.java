import java.nio.file.*;
import java.util.*;
import org.objectweb.asm.*;
import org.objectweb.asm.tree.*;
public final class StripSubscribers {
 public static void main(String[] args)throws Exception {
  var path=Path.of(args[0]);var node=new ClassNode();new ClassReader(Files.readAllBytes(path)).accept(node,0);
  if(node.visibleAnnotations!=null)node.visibleAnnotations.removeIf(a->a.desc.equals("Lnet/neoforged/fml/common/EventBusSubscriber;"));
  var writer=new ClassWriter(0);node.accept(writer);Files.write(path,writer.toByteArray());
 }
}
