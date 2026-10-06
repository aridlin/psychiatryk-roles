import java.nio.file.*;
import net.minecraft.nbt.*;
/** Change only the copied disposable metadata; no production/player data. */
public final class PrepareWorld {
 public static void main(String[] args)throws Exception{
  Path path=Path.of(args[0]);var root=NbtIo.readCompressed(path,NbtAccounter.unlimitedHeap());var data=root.getCompound("Data");
  var generator=new CompoundTag();generator.putString("type","minecraft:flat");var settings=new CompoundTag();settings.putString("biome","minecraft:plains");settings.putBoolean("features",false);settings.putBoolean("lakes",false);settings.put("structure_overrides",new ListTag());
  var layer=new CompoundTag();layer.putString("block","minecraft:bedrock");layer.putInt("height",1);var layers=new ListTag();layers.add(layer);settings.put("layers",layers);generator.put("settings",settings);
  data.getCompound("WorldGenSettings").getCompound("dimensions").getCompound("minecraft:overworld").put("generator",generator);
  data.putString("LevelName","Disposable Scooter Steering QA");data.putBoolean("allowCommands",true);data.putInt("GameType",1);data.remove("Player");
  NbtIo.writeCompressed(root,path);
 }
}
