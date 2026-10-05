package pl.aridlin.kukirin;
import net.minecraft.world.item.ItemStack;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.core.BlockPos;
/** Server-owned personal battery: 72000 charge ticks, two units used per riding tick. */
public final class ScooterBattery {
 public static final int CAPACITY=72000;
 public static final String KEY="GoplanskaScooterBattery";
 public static int stored(ItemStack item){var tag=item.getOrDefault(DataComponents.CUSTOM_DATA,CustomData.EMPTY).getUnsafe();return Math.clamp(tag.contains(KEY)?tag.getInt(KEY):CAPACITY,0,CAPACITY);}
 public static boolean infinite(ItemStack item){return ScooterUpgradeRecipe.has(item,"infinite");}
 public static int percent(int charge){return Math.clamp((int)(100L*charge/CAPACITY),0,100);}
 public static int next(int charge,int ticks,boolean charging,boolean riding){return (int)Math.clamp((long)charge+(riding?-2L*ticks:charging?ticks:0),0,CAPACITY);}
 public static boolean poweredDepot(Scooter s){
  var level=s.level();int x=net.minecraft.util.Mth.floor(s.getX()),z=net.minecraft.util.Mth.floor(s.getZ());
  for(int y=net.minecraft.util.Mth.floor(s.getY());y>=net.minecraft.util.Mth.floor(s.getY())-1;y--){var pad=new BlockPos(x,y,z);if(!level.hasChunkAt(pad)||!level.hasChunkAt(pad.below()))continue;
   if(!net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(level.getBlockState(pad).getBlock()).toString().equals("create:depot"))continue;
   // The wheels must rest on the depot, not hang next to or underneath it.
   double gap=s.getY()-(pad.getY()+.8125);if(gap<-.12||gap>.45||s.getDeltaMovement().horizontalDistance()>.025)return false;
   var shaft=level.getBlockState(pad.below());if(!net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(shaft.getBlock()).toString().equals("create:shaft")||shaft.getValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.AXIS)!=net.minecraft.core.Direction.Axis.Y)return false;
   var blockEntity=level.getBlockEntity(pad.below());return blockEntity instanceof com.simibubi.create.content.kinetics.base.KineticBlockEntity kinetic&&!kinetic.isOverStressed()&&Math.abs(kinetic.getSpeed())>.001f;
  }return false;
 }
 public static void tick(Scooter s){if(s.level().isClientSide||ScooterRental.isRental(s))return;var item=s.getItemBySlot(net.minecraft.world.entity.EquipmentSlot.FEET);boolean infinite=infinite(item);int current=stored(item);boolean charging=!infinite&&!s.isVehicle()&&poweredDepot(s);s.batteryState(infinite?CAPACITY:current,charging,infinite);
  if(s.tickCount%20!=0||infinite)return;
  int remaining=next(current,20,charging,false);int remainder=0;if(s.isVehicle()){int unbreaking=Math.clamp(ScooterEnchants.level(s,"unbreaking"),0,3),denominator=2+unbreaking;var tag=item.getOrDefault(DataComponents.CUSTOM_DATA,CustomData.EMPTY).getUnsafe();int numerator=80+Math.clamp(tag.getInt("GoplanskaBatteryDrainRemainder"),0,4);remaining=Math.max(0,current-numerator/denominator);remainder=numerator%denominator;}
  if(s.isVehicle()&&s.tickCount%100==0&&current<CAPACITY&&ScooterEnchants.level(s,"mending")>0&&s.getControllingPassenger() instanceof net.minecraft.server.level.ServerPlayer p&&(p.experienceLevel>0||p.experienceProgress>0)){p.giveExperiencePoints(-1);remaining=Math.min(CAPACITY,remaining+400);}
if(remaining!=current){item=item.copy();final int value=remaining,credit=remainder;CustomData.update(DataComponents.CUSTOM_DATA,item,t->{t.putInt(KEY,value);t.putInt("GoplanskaBatteryDrainRemainder",credit);});s.setItemSlot(net.minecraft.world.entity.EquipmentSlot.FEET,item);s.batteryState(remaining,charging,false);}
  if(remaining==0&&s.isVehicle()&&s.getControllingPassenger() instanceof net.minecraft.server.level.ServerPlayer p&&s.tickCount%100==0)p.displayClientMessage(net.minecraft.network.chat.Component.translatable("scooter.battery.empty"),true);
 }
 private ScooterBattery(){}
}
