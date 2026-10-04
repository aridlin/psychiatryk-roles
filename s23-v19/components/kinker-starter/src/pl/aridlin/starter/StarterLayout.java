package pl.aridlin.starter;
import java.util.*;
import net.minecraft.core.*;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
public final class StarterLayout {
 public record Piece(BlockPos pos,BlockState state){}
 static BlockState block(String name,Direction facing){var id=ResourceLocation.fromNamespaceAndPath("modernfoundry",name);if(!BuiltInRegistries.BLOCK.containsKey(id))throw new IllegalStateException("Missing starter block "+id);var state=BuiltInRegistries.BLOCK.get(id).defaultBlockState();if(state.hasProperty(BlockStateProperties.HORIZONTAL_FACING))state=state.setValue(BlockStateProperties.HORIZONTAL_FACING,facing);else if(state.hasProperty(BlockStateProperties.FACING))state=state.setValue(BlockStateProperties.FACING,facing);return state;}
 public static List<Piece> create(BlockPos origin,Rotation rotation){
  var map=new LinkedHashMap<BlockPos,BlockState>();var bricks=block("seared_bricks",Direction.NORTH);
  for(int x=0;x<5;x++)for(int z=0;z<5;z++){map.put(new BlockPos(x,0,z),bricks);if(x==0||x==4||z==0||z==4)for(int y=1;y<=3;y++)map.put(new BlockPos(x,y,z),bricks);}
  map.put(new BlockPos(2,1,0),block("smeltery_controller",Direction.NORTH));map.put(new BlockPos(1,1,0),block("seared_fuel_tank",Direction.NORTH));
  map.put(new BlockPos(3,1,0),block("seared_drain",Direction.NORTH));map.put(new BlockPos(3,1,-1),block("seared_faucet",Direction.NORTH));map.put(new BlockPos(3,0,-1),block("seared_table",Direction.NORTH));
  map.put(new BlockPos(4,1,2),block("seared_drain",Direction.EAST));map.put(new BlockPos(5,1,2),block("seared_faucet",Direction.EAST));map.put(new BlockPos(5,0,2),block("seared_basin",Direction.EAST));
  String[] stations={"crafting_station","part_builder","tinker_station","tinkers_anvil","cast_chest"};
  for(int i=0;i<stations.length;i++)map.put(new BlockPos(7,0,i),block(stations[i],Direction.WEST));
  return map.entrySet().stream().map(e->new Piece(origin.offset(e.getKey().rotate(rotation)),e.getValue().rotate(rotation))).toList();
 }
 public static List<BlockPos> emptyInterior(BlockPos origin,Rotation rotation){var list=new ArrayList<BlockPos>();for(int x=1;x<4;x++)for(int z=1;z<4;z++)for(int y=1;y<=3;y++)list.add(origin.offset(new BlockPos(x,y,z).rotate(rotation)));return list;}
}
