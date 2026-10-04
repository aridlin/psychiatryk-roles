package pl.aridlin.psychiatrykroles;
import java.util.*;
import net.minecraft.core.*;
import net.minecraft.nbt.*;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;
/** Saved independently so existing linked-door data and item identifiers stay intact. */
final class VoidExitDirections extends SavedData {
 private final Set<String> reversed=new HashSet<>();
 static VoidExitDirections get(MinecraftServer server){return server.overworld().getDataStorage().computeIfAbsent(new Factory<>(VoidExitDirections::new,VoidExitDirections::load),"psychiatryk_void_exit_directions");}
 static String key(ServerLevel level,BlockPos pos){return level.dimension().location()+":"+pos.asLong();}
 static VoidExitDirections load(CompoundTag tag,HolderLookup.Provider lookup){var d=new VoidExitDirections();var list=tag.getList("reversed",8);for(int i=0;i<list.size();i++)d.reversed.add(list.getString(i));return d;}
 boolean toggle(ServerLevel level,BlockPos pos){String key=key(level,pos);boolean now=reversed.add(key);if(!now)reversed.remove(key);setDirty();return now;}
 boolean flipped(ServerLevel level,BlockPos pos){return reversed.contains(key(level,pos));}
 static Direction exit(ServerLevel level,BlockPos pos,Direction facing){return get(level.getServer()).flipped(level,pos)?facing.getOpposite():facing;}
 @Override public CompoundTag save(CompoundTag tag,HolderLookup.Provider lookup){var list=new ListTag();for(String key:new TreeSet<>(reversed))list.add(StringTag.valueOf(key));tag.put("reversed",list);return tag;}
}
