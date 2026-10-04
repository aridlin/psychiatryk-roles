import java.nio.file.*;
import org.objectweb.asm.*;
import org.objectweb.asm.tree.*;
public class PatchSleep implements Opcodes {
 public static void main(String[] args)throws Exception{
 var node=new ClassNode();new ClassReader(Files.readAllBytes(Path.of(args[0]))).accept(node,0);boolean patched=false;
 for(var m:node.methods){
  for(var ins:m.instructions)if(ins instanceof MethodInsnNode call && call.owner.equals("net/minecraft/server/level/ServerPlayer")&&call.name.equals("setGameMode")&&call.desc.equals("(Lnet/minecraft/world/level/GameType;)Z")){call.setOpcode(INVOKESTATIC);call.owner="pl/aridlin/psychiatrykroles/PhaseCharm";call.name="roleGameMode";call.desc="(Lnet/minecraft/server/level/ServerPlayer;Lnet/minecraft/world/level/GameType;)Z";}
  if(m.name.equals("<clinit>")){for(var ins:m.instructions)if(ins instanceof FieldInsnNode f&&f.getOpcode()==PUTSTATIC&&f.name.equals("ITEM_PRESETS"))m.instructions.insertBefore(ins,new MethodInsnNode(INVOKESTATIC,"pl/aridlin/psychiatrykroles/CustomPresets","extend","(Ljava/util/List;)Ljava/util/List;",false));}
  if(m.name.equals("presetItem")){var start=new InsnList();var fallback=new LabelNode();start.add(new VarInsnNode(ALOAD,0));start.add(new VarInsnNode(ALOAD,1));start.add(new VarInsnNode(LLOAD,2));start.add(new MethodInsnNode(INVOKESTATIC,"pl/aridlin/psychiatrykroles/CustomPresets","extra","(Ljava/lang/String;Lnet/minecraft/server/level/ServerPlayer;J)Lnet/minecraft/world/item/ItemStack;",false));start.add(new InsnNode(DUP));start.add(new JumpInsnNode(IFNULL,fallback));start.add(new InsnNode(ARETURN));start.add(fallback);start.add(new FrameNode(F_SAME1,0,null,1,new Object[]{"net/minecraft/world/item/ItemStack"}));start.add(new InsnNode(POP));m.instructions.insert(start);}

  if(m.name.equals("onConsultantSleep")||m.name.equals("renderChalkMarker")){m.instructions.clear();m.instructions.add(new InsnNode(RETURN));m.tryCatchBlocks.clear();if(m.localVariables!=null)m.localVariables.clear();}
  if(!m.name.equals("onServerTick"))continue;
  AbstractInsnNode start=null;for(var ins:m.instructions){if(ins instanceof FieldInsnNode f&&f.name.equals("RULE_PLAYERS_SLEEPING_PERCENTAGE")){for(var n=ins;n!=null;n=n.getNext())if(n instanceof VarInsnNode v&&v.getOpcode()==ASTORE){if(v.var!=6)throw new IllegalStateException("Unexpected sleep rule slot");start=n.getNext();break;}break;}}
  if(start==null)throw new IllegalStateException("Sleep override not found");
  for(var n=start;n!=null;){var next=n.getNext();m.instructions.remove(n);n=next;}
  m.instructions.add(new VarInsnNode(ALOAD,6));m.instructions.add(new IntInsnNode(BIPUSH,50));m.instructions.add(new VarInsnNode(ALOAD,2));m.instructions.add(new MethodInsnNode(INVOKEVIRTUAL,"net/minecraft/world/level/GameRules$IntegerValue","set","(ILnet/minecraft/server/MinecraftServer;)V",false));m.instructions.add(new InsnNode(RETURN));if(m.localVariables!=null)m.localVariables.clear();patched=true;
 }
 if(!patched)throw new IllegalStateException("No tick patch");var writer=new ClassWriter(ClassWriter.COMPUTE_MAXS);node.accept(writer);Files.write(Path.of(args[1]),writer.toByteArray());
 }
}
