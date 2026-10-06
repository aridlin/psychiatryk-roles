package pl.aridlin.kukirin;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.level.Level;
/** Anvil names are read from the preserved scooter item, including after pickup. */
public final class ScooterEasterEggs {
 private ScooterEasterEggs(){}
 public static String name(Scooter scooter){if(scooter.hasCustomName())return scooter.getCustomName().getString();var item=scooter.getItemBySlot(EquipmentSlot.FEET);var name=item.get(DataComponents.CUSTOM_NAME);return name==null?"":name.getString();}
 public static boolean rainbow(Scooter scooter){return name(scooter).equals("jeb_");}
 public static boolean inverted(Scooter scooter){String name=name(scooter);return PortableOptions.get().invertedGravity()&&(name.equals("Dinnerbone")||name.equals("Grumm"));}
 private static final int[] RAINBOW={14,1,4,5,13,9,3,11,10,2,6};
 public static int variant(Scooter scooter,float partial){if(!rainbow(scooter))return ScooterDyeRecipe.variant(scooter.getItemBySlot(EquipmentSlot.FEET));int phase=Math.floorMod((int)((scooter.level().getGameTime()+partial)/12),RAINBOW.length);return 1+RAINBOW[phase];}
 public static boolean tick(Scooter scooter){
  if(scooter.level().isClientSide)return false;
  if(PortableOptions.get().rentalExplosions()&&ScooterRental.isRental(scooter)&&scooter.level().dimension()==Level.NETHER){
   // Remove first so the explosion cannot trigger this rental twice.
   double x=scooter.getX(),y=scooter.getY()+.3,z=scooter.getZ();scooter.ejectPassengers();scooter.discard();
   scooter.level().explode(null,scooter.damageSources().badRespawnPointExplosion(new net.minecraft.world.phys.Vec3(x,y,z)),null,x,y,z,5,true,Level.ExplosionInteraction.BLOCK);return true;
  }
  if(inverted(scooter)&&scooter.getY()+scooter.getBbHeight()>=scooter.level().getMaxBuildHeight()){
   for(var rider:java.util.List.copyOf(scooter.getPassengers()))if(rider instanceof net.minecraft.server.level.ServerPlayer player){player.sendSystemMessage(net.minecraft.network.chat.Component.literal("You melted in the sun."));player.hurt(scooter.damageSources().genericKill(),Float.MAX_VALUE);}
   scooter.ejectPassengers();scooter.setDeltaMovement(net.minecraft.world.phys.Vec3.ZERO);
  }
  return false;
 }
}
