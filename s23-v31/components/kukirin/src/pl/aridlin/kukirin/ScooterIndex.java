package pl.aridlin.kukirin;
import java.util.*;
import net.minecraft.core.*;
import net.minecraft.nbt.*;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;
public final class ScooterIndex extends SavedData {
 public record Entry(UUID id,UUID owner,String dimension,BlockPos pos,boolean loyalty,int color){}
 public final Map<UUID,Entry> entries=new HashMap<>();
 private final Map<UUID,Long> missingSince=new HashMap<>();
 public static ScooterIndex get(MinecraftServer s){return s.overworld().getDataStorage().computeIfAbsent(new Factory<>(ScooterIndex::new,ScooterIndex::load),"goplanska_bound_scooters");}
 static ScooterIndex load(CompoundTag t,HolderLookup.Provider registry){var d=new ScooterIndex();for(var n:t.getList("scooters",10)){var c=(CompoundTag)n;if(c.hasUUID("id")&&c.hasUUID("owner")){var e=new Entry(c.getUUID("id"),c.getUUID("owner"),c.getString("dimension"),BlockPos.of(c.getLong("pos")),c.getBoolean("loyalty"),c.contains("color")?c.getInt("color"):ScooterMarkerColor.variant(0));d.entries.put(e.id(),e);}}return d;}
 public void track(Scooter s){var owner=ScooterEnchants.owner(s.getItemBySlot(net.minecraft.world.entity.EquipmentSlot.FEET));if(owner==null)return;var e=new Entry(s.getUUID(),owner,s.level().dimension().location().toString(),s.blockPosition(),ScooterEnchants.level(s,"loyalty")>0,ScooterMarkerColor.item(s.getItemBySlot(net.minecraft.world.entity.EquipmentSlot.FEET)));if(!e.equals(entries.put(e.id(),e)))setDirty();}
 public void track(net.minecraft.world.entity.item.ItemEntity item){var owner=ScooterEnchants.owner(item.getItem());if(owner==null)return;var lookup=item.level().registryAccess().lookupOrThrow(net.minecraft.core.registries.Registries.ENCHANTMENT);var loyalty=lookup.getOrThrow(net.minecraft.resources.ResourceKey.create(net.minecraft.core.registries.Registries.ENCHANTMENT,net.minecraft.resources.ResourceLocation.withDefaultNamespace("loyalty")));var e=new Entry(item.getUUID(),owner,item.level().dimension().location().toString(),item.blockPosition(),item.getItem().getEnchantmentLevel(loyalty)>0,ScooterMarkerColor.item(item.getItem()));if(!e.equals(entries.put(e.id(),e)))setDirty();}
 public void remove(UUID id){missingSince.remove(id);if(entries.remove(id)!=null)setDirty();}
 /** A loaded entity chunk is authoritative. Never force-load distant scooters just to draw a dot.
  * Allow two seconds for chunk entity deserialization before repairing legacy pickup records. */
 public void pruneLoaded(net.minecraft.server.level.ServerLevel world){
  String dimension=world.dimension().location().toString();long now=world.getGameTime();
  for(var entry:new ArrayList<>(entries.values())){
   if(!entry.dimension().equals(dimension))continue;
   var entity=world.getEntity(entry.id());
   if(entity!=null){missingSince.remove(entry.id());if(entity instanceof Scooter s)track(s);else if(entity instanceof net.minecraft.world.entity.item.ItemEntity item)track(item);continue;}
   if(!world.areEntitiesLoaded(new net.minecraft.world.level.ChunkPos(entry.pos()).toLong())){missingSince.remove(entry.id());continue;}
   long first=missingSince.computeIfAbsent(entry.id(),id->now);if(now-first>=40)remove(entry.id());
  }
 }
 @Override public CompoundTag save(CompoundTag t,HolderLookup.Provider registry){var list=new ListTag();for(var e:entries.values()){var n=new CompoundTag();n.putUUID("id",e.id());n.putUUID("owner",e.owner());n.putString("dimension",e.dimension());n.putLong("pos",e.pos().asLong());n.putBoolean("loyalty",e.loyalty());n.putInt("color",e.color());list.add(n);}t.put("scooters",list);return t;}
}
