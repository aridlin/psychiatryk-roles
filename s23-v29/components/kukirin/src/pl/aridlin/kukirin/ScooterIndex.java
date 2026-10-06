package pl.aridlin.kukirin;
import java.util.*;
import net.minecraft.core.*;
import net.minecraft.nbt.*;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;
public final class ScooterIndex extends SavedData {
 public record Entry(UUID id,UUID owner,String dimension,BlockPos pos,boolean loyalty){}
 public final Map<UUID,Entry> entries=new HashMap<>();
 public static ScooterIndex get(MinecraftServer s){return s.overworld().getDataStorage().computeIfAbsent(new Factory<>(ScooterIndex::new,ScooterIndex::load),"goplanska_bound_scooters");}
 static ScooterIndex load(CompoundTag t,HolderLookup.Provider registry){var d=new ScooterIndex();for(var n:t.getList("scooters",10)){var c=(CompoundTag)n;if(c.hasUUID("id")&&c.hasUUID("owner")){var e=new Entry(c.getUUID("id"),c.getUUID("owner"),c.getString("dimension"),BlockPos.of(c.getLong("pos")),c.getBoolean("loyalty"));d.entries.put(e.id(),e);}}return d;}
 public void track(Scooter s){var owner=ScooterEnchants.owner(s.getItemBySlot(net.minecraft.world.entity.EquipmentSlot.FEET));if(owner==null)return;var e=new Entry(s.getUUID(),owner,s.level().dimension().location().toString(),s.blockPosition(),ScooterEnchants.level(s,"loyalty")>0);if(!e.equals(entries.put(e.id(),e)))setDirty();}
 public void track(net.minecraft.world.entity.item.ItemEntity item){var owner=ScooterEnchants.owner(item.getItem());if(owner==null)return;var lookup=item.level().registryAccess().lookupOrThrow(net.minecraft.core.registries.Registries.ENCHANTMENT);var loyalty=lookup.getOrThrow(net.minecraft.resources.ResourceKey.create(net.minecraft.core.registries.Registries.ENCHANTMENT,net.minecraft.resources.ResourceLocation.withDefaultNamespace("loyalty")));var e=new Entry(item.getUUID(),owner,item.level().dimension().location().toString(),item.blockPosition(),item.getItem().getEnchantmentLevel(loyalty)>0);if(!e.equals(entries.put(e.id(),e)))setDirty();}
 public void remove(UUID id){if(entries.remove(id)!=null)setDirty();}
 @Override public CompoundTag save(CompoundTag t,HolderLookup.Provider registry){var list=new ListTag();for(var e:entries.values()){var n=new CompoundTag();n.putUUID("id",e.id());n.putUUID("owner",e.owner());n.putString("dimension",e.dimension());n.putLong("pos",e.pos().asLong());n.putBoolean("loyalty",e.loyalty());list.add(n);}t.put("scooters",list);return t;}
}
