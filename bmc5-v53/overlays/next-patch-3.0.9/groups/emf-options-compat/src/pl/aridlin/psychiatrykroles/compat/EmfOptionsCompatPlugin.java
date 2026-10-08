package pl.aridlin.psychiatrykroles.compat;
import java.util.*;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.*;
import org.spongepowered.asm.mixin.extensibility.*;
import org.spongepowered.asm.service.MixinService;
/** Fixes only the obsolete GREEN render-mode entry in the bundled EMF options integration. */
public final class EmfOptionsCompatPlugin implements IMixinConfigPlugin {
 static final String TARGET="toni.sodiumoptionsmodcompat.integration.emf.EmfModelsOptionPage";
 static final String MIXIN="pl.aridlin.psychiatrykroles.compat.EmfRenderModeLabelsMixin";
 static final String ENUM="traben.entity_model_features.config.EMFConfig$RenderModeChoice";
 static boolean legacyMismatch(ClassNode target,ClassNode choices) {
  if(target==null||choices==null||!TARGET.replace('.','/').equals(target.name)||!ENUM.replace('.','/').equals(choices.name))return false;
  var constants=new ArrayList<String>();
  for(var f:choices.fields)if((f.access&Opcodes.ACC_ENUM)!=0)constants.add(f.name);
  if(!constants.equals(List.of("NORMAL","LINES_AND_TEXTURE","LINES_AND_TEXTURE_FLASH","LINES","NONE")))return false;
  var m=target.methods.stream().filter(v->v.name.equals("lambda$create$23")&&v.desc.equals("(Lnet/caffeinemc/mods/sodium/client/gui/options/OptionImpl;)Lnet/caffeinemc/mods/sodium/client/gui/options/control/Control;")).findFirst().orElse(null);
  if(m==null)return false;
  var labels=new ArrayList<String>();boolean six=false,constructor=false;
  for(var i:m.instructions){
   if(i instanceof LdcInsnNode l&&l.cst instanceof String s&&s.startsWith("entity_model_features.config.render."))labels.add(s);
   if(i instanceof IntInsnNode n&&n.getOpcode()==Opcodes.BIPUSH&&n.operand==6&&i.getNext() instanceof TypeInsnNode t&&t.getOpcode()==Opcodes.ANEWARRAY&&t.desc.equals("net/minecraft/network/chat/Component"))six=true;
   if(i instanceof MethodInsnNode n&&n.owner.equals("net/caffeinemc/mods/sodium/client/gui/options/control/CyclingControl")&&n.name.equals("<init>")&&n.desc.equals("(Lnet/caffeinemc/mods/sodium/client/gui/options/Option;Ljava/lang/Class;[Lnet/minecraft/network/chat/Component;)V"))constructor=true;
  }
  return six&&constructor&&labels.equals(List.of("entity_model_features.config.render.normal","entity_model_features.config.render.green","entity_model_features.config.render.lines_texture","entity_model_features.config.render.lines_texture_flash","entity_model_features.config.render.lines","entity_model_features.config.render.none"));
 }
 public boolean shouldApplyMixin(String target,String mixin){
  if(!TARGET.equals(target)||!MIXIN.equals(mixin))return false;
  try{var p=MixinService.getService().getBytecodeProvider();return legacyMismatch(p.getClassNode(TARGET,false),p.getClassNode(ENUM,false));}
  catch(Exception|LinkageError unavailable){return false;}
 }
 public void onLoad(String p){} public String getRefMapperConfig(){return null;}
 public void acceptTargets(Set<String>a,Set<String>b){} public List<String> getMixins(){return null;}
 public void preApply(String t,ClassNode n,String m,IMixinInfo i){} public void postApply(String t,ClassNode n,String m,IMixinInfo i){}
}
