package pl.aridlin.psychiatrykroles.testing;

import java.util.Set;
import net.minecraft.nbt.*;

/** Only personal inventory/physical state enters a bank. UUID, roles and parties remain global. */
public final class BankNbt {
 public static final String ATTACHMENTS="neoforge:attachments";
 public static final Set<String> KEYS=Set.of("Inventory","SelectedItemSlot","EnderItems","XpP","XpLevel","XpTotal","XpSeed","Health","AbsorptionAmount","attributes","active_effects","foodLevel","foodTickTimer","foodSaturationLevel","foodExhaustionLevel","ShoulderEntityLeft","ShoulderEntityRight","SpawnX","SpawnY","SpawnZ","SpawnDimension","SpawnForced","SpawnAngle");
 private static final Set<String> FRAMEWORK_ITEMS=Set.of("backpacked:backpack","backpacked:backpacks","backpacked:selected_backpack");
 public static boolean frameworkItem(CompoundTag t){return FRAMEWORK_ITEMS.contains(t.getString("DataKey"));}
 private static boolean globalAttachment(String id){return id.equals("neoforge:persistent_data");}
 public static CompoundTag capture(CompoundTag full){
  var bank=new CompoundTag();for(String k:KEYS)if(full.contains(k))bank.put(k,full.get(k).copy());
  var a=full.getCompound(ATTACHMENTS);var out=new CompoundTag();
  for(String id:a.getAllKeys())if(!globalAttachment(id)){
   if(id.equals("framework:data_holder")){var items=new ListTag();for(Tag value:a.getList(id,Tag.TAG_COMPOUND))if(value instanceof CompoundTag t&&frameworkItem(t))items.add(t.copy());out.put(id,items);}
   else out.put(id,a.get(id).copy());
  }
  bank.put(ATTACHMENTS,out);return bank;
 }
 public static CompoundTag merge(CompoundTag current,CompoundTag bank){
  var out=current.copy();for(String k:KEYS){out.remove(k);if(bank.contains(k))out.put(k,bank.get(k).copy());}
  var a=current.getCompound(ATTACHMENTS).copy();var saved=bank.getCompound(ATTACHMENTS);
  for(String id:Set.copyOf(a.getAllKeys()))if(!globalAttachment(id)&&!id.equals("framework:data_holder"))a.remove(id);
  for(String id:saved.getAllKeys())if(!id.equals("framework:data_holder")&&!globalAttachment(id))a.put(id,saved.get(id).copy());
  // Framework stores backpack inventory beside permanent challenge/unlock progress.
  var framework=new ListTag();for(Tag value:current.getCompound(ATTACHMENTS).getList("framework:data_holder",Tag.TAG_COMPOUND))if(value instanceof CompoundTag t&&!frameworkItem(t))framework.add(t.copy());
  for(Tag value:saved.getList("framework:data_holder",Tag.TAG_COMPOUND))framework.add(value.copy());
  if(!framework.isEmpty()||a.contains("framework:data_holder")||saved.contains("framework:data_holder"))a.put("framework:data_holder",framework);
  out.put(ATTACHMENTS,a);return out;
 }
 private BankNbt(){}
}
