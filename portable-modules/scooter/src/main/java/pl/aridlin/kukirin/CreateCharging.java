package pl.aridlin.kukirin;
/** Optional Create charging by semantic block IDs and guarded public kinetic methods, never class-linked. */
public final class CreateCharging {
 public static boolean poweredDepot(Scooter s){if(!net.neoforged.fml.ModList.get().isLoaded("create")||s.isVehicle()||!s.onGround())return false;
  var pos=s.blockPosition().below();var state=s.level().getBlockState(pos);var id=net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(state.getBlock());if(!id.toString().equals("create:depot"))return false;
  for(var d:net.minecraft.core.Direction.values()){var entity=s.level().getBlockEntity(pos.relative(d));if(entity==null)continue;try{Object speed=entity.getClass().getMethod("getSpeed").invoke(entity);boolean stressed=(boolean)entity.getClass().getMethod("isOverStressed").invoke(entity);if(speed instanceof Number n&&Math.abs(n.doubleValue())>=8&&!stressed)return true;}catch(ReflectiveOperationException|LinkageError ignored){}}
  return false;}
 private CreateCharging(){}
}
