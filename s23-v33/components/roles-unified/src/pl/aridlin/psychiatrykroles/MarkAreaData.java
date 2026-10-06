package pl.aridlin.psychiatrykroles;
import java.util.*;
import net.minecraft.core.*;
import net.minecraft.nbt.*;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;
final class MarkAreaData extends SavedData {
 record Area(UUID id,UUID owner,String dimension,BlockPos a,BlockPos b){}
 final List<Area> areas=new ArrayList<>();
 static MarkAreaData get(MinecraftServer s){return s.overworld().getDataStorage().computeIfAbsent(new Factory<>(MarkAreaData::new,MarkAreaData::load),"psychiatryk_marking_areas");}
 static MarkAreaData load(CompoundTag t,HolderLookup.Provider lookup){var d=new MarkAreaData();var list=t.getList("areas",10);for(int i=0;i<list.size();i++){var n=list.getCompound(i);d.areas.add(new Area(n.getUUID("id"),n.getUUID("owner"),n.getString("dimension"),BlockPos.of(n.getLong("a")),n.contains("b")?BlockPos.of(n.getLong("b")):null));}return d;}
 Area place(UUID owner,String dimension,BlockPos p){for(int i=0;i<areas.size();i++){var a=areas.get(i);if(a.owner().equals(owner)&&a.dimension().equals(dimension)&&a.b()==null&&Math.abs(a.a().getX()-p.getX())<=128&&Math.abs(a.a().getY()-p.getY())<=128&&Math.abs(a.a().getZ()-p.getZ())<=128){var done=new Area(a.id(),owner,dimension,a.a(),p.immutable());areas.set(i,done);setDirty();return done;}}var a=new Area(UUID.randomUUID(),owner,dimension,p.immutable(),null);areas.add(a);setDirty();return a;}
 void remove(String dimension,BlockPos pos){if(areas.removeIf(a->a.dimension().equals(dimension)&&(a.a().equals(pos)||pos.equals(a.b()))))setDirty();}
 @Override public CompoundTag save(CompoundTag t,HolderLookup.Provider lookup){var list=new ListTag();for(var a:areas){var n=new CompoundTag();n.putUUID("id",a.id());n.putUUID("owner",a.owner());n.putString("dimension",a.dimension());n.putLong("a",a.a().asLong());if(a.b()!=null)n.putLong("b",a.b().asLong());list.add(n);}t.put("areas",list);return t;}
}
