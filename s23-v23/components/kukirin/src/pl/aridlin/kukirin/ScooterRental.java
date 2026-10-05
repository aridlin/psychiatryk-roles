package pl.aridlin.kukirin;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.*;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.network.chat.Component;
/** Rental state is stored on the synced, persisted vehicle item; no pickup path exists. */
public final class ScooterRental {
 public static final RentalConfig CONFIG=new RentalConfig(net.neoforged.fml.loading.FMLPaths.CONFIGDIR.get().resolve("goplanska-scooter/rentals.properties"));
 public static boolean isRental(Scooter scooter){return data(scooter).getBoolean("GoplanskaRental");}
 private static net.minecraft.nbt.CompoundTag data(Scooter scooter){return scooter.getItemBySlot(EquipmentSlot.FEET).getOrDefault(net.minecraft.core.component.DataComponents.CUSTOM_DATA,CustomData.EMPTY).copyTag();}
 public static int battery(Scooter scooter){return Math.max(0,data(scooter).getInt("RentalBattery"));}
 public static java.util.List<ItemStack> presets(){return java.util.stream.IntStream.range(0,3).mapToObj(ScooterRental::preset).toList();}
 public static ItemStack preset(int brand){var item=ScooterDyeRecipe.paint(new ItemStack(Kukirin.ITEM.get()),brand==0?DyeColor.LIME:brand==1?DyeColor.LIGHT_BLUE:DyeColor.ORANGE);CustomData.update(net.minecraft.core.component.DataComponents.CUSTOM_DATA,item,t->{t.putBoolean("GoplanskaRental",true);t.putInt("RentalBattery",RentalPolicy.freshBatteryTicks());t.putInt("RentalBrand",brand);});item.set(net.minecraft.core.component.DataComponents.CUSTOM_NAME,Component.literal((brand==0?"Lime":brand==1?"Bolt":"City")+" rental"));return item;}
 public static void initialize(Scooter scooter,int brand){scooter.setItemSlot(EquipmentSlot.FEET,preset(brand));var persistent=scooter.getPersistentData();persistent.putLong("RentalLastSeen",scooter.level().getGameTime());persistent.putLong("RentalEmptySince",-1);}
 public static InteractionResult interact(Scooter scooter,net.minecraft.world.entity.player.Player player){
  if(scooter.level().isClientSide)return InteractionResult.SUCCESS;
  if(!(player instanceof ServerPlayer serverPlayer))return InteractionResult.FAIL;
  if(player.isShiftKeyDown()){
   if(scooter.isVehicle())return InteractionResult.FAIL;
   ((net.minecraft.server.level.ServerLevel)scooter.level()).sendParticles(ParticleTypes.POOF,scooter.getX(),scooter.getY()+.3,scooter.getZ(),40,.5,.3,.5,.08);ScooterMusic.stop(scooter);scooter.discard();return InteractionResult.CONSUME;
  }
  if(scooter.isVehicle())return InteractionResult.PASS;
  if(battery(scooter)==0){player.displayClientMessage(Component.literal("Rental battery empty."),true);return InteractionResult.FAIL;}
  var state=scooter.getPersistentData();long now=scooter.level().getGameTime();boolean paid=state.hasUUID("RentalRider")&&state.getUUID("RentalRider").equals(player.getUUID())&&state.getLong("RentalPaidUntil")>=now;
  long price=CONFIG.get().price();if(!paid){if(!pl.aridlin.psychiatrykroles.RentalPayment.charge(serverPlayer,price)){player.displayClientMessage(Component.literal("Rental costs "+price+" poker chips. Use /poker to check your wallet."),false);return InteractionResult.FAIL;}state.putUUID("RentalRider",player.getUUID());state.putLong("RentalPaidUntil",now+1200);}
  if(!player.startRiding(scooter)){if(!paid)pl.aridlin.psychiatrykroles.RentalPayment.refund(serverPlayer,price);state.remove("RentalRider");state.remove("RentalPaidUntil");return InteractionResult.FAIL;}return InteractionResult.CONSUME;
 }
 /** Called by bounded render-heartbeat packet after server distance validation. */
 public static void renderedBy(Scooter scooter,ServerPlayer player){if(isRental(scooter)&&player.level()==scooter.level()&&player.distanceToSqr(scooter)<160*160)scooter.getPersistentData().putLong("RentalLastSeen",scooter.level().getGameTime());}
 public static void tick(Scooter scooter){if(scooter.level().isClientSide||!isRental(scooter))return;var state=scooter.getPersistentData();long now=scooter.level().getGameTime();int battery=battery(scooter);if(scooter.isVehicle()){
  if(scooter.getFirstPassenger() instanceof net.minecraft.world.entity.monster.Zombie zombie){if(ScooterEnchants.level(scooter,"knockback")==0){var equipped=scooter.getItemBySlot(EquipmentSlot.FEET).copy();equipped.enchant(scooter.registryAccess().lookupOrThrow(net.minecraft.core.registries.Registries.ENCHANTMENT).getOrThrow(net.minecraft.resources.ResourceKey.create(net.minecraft.core.registries.Registries.ENCHANTMENT,net.minecraft.resources.ResourceLocation.parse("minecraft:knockback"))),1);scooter.setItemSlot(EquipmentSlot.FEET,equipped);}var target=scooter.level().getNearestPlayer(scooter,24);zombie.setNoAi(true);if(target!=null){var d=target.position().subtract(scooter.position());float yaw=(float)Math.toDegrees(Math.atan2(-d.x,d.z));scooter.setYRot(net.minecraft.util.Mth.approachDegrees(scooter.getYRot(),yaw,4));if(target.distanceToSqr(scooter)<3&&now%20==0)target.hurt(scooter.damageSources().mobAttack(zombie),3);}}
  if(battery<=0){for(var passenger:scooter.getPassengers())if(passenger instanceof net.minecraft.world.entity.monster.Zombie z)z.setNoAi(false);scooter.ejectPassengers();}else{state.putLong("RentalPaidUntil",now+1200);if(now%20==0){var item=scooter.getItemBySlot(EquipmentSlot.FEET).copy();int remaining=Math.max(0,battery-20);CustomData.update(net.minecraft.core.component.DataComponents.CUSTOM_DATA,item,t->t.putInt("RentalBattery",remaining));scooter.setItemSlot(EquipmentSlot.FEET,item);}}
 }
 if(battery<=0&&!state.contains("RentalEmptySince"))state.putLong("RentalEmptySince",now);if(battery<=0&&state.getLong("RentalEmptySince")<0)state.putLong("RentalEmptySince",now);
 if(RentalPolicy.cleanup(scooter.getFirstPassenger() instanceof net.minecraft.world.entity.player.Player,now,state.getLong("RentalLastSeen"),state.getLong("RentalEmptySince"))){for(var passenger:scooter.getPassengers())if(passenger instanceof net.minecraft.world.entity.monster.Zombie z)z.setNoAi(false);scooter.ejectPassengers();ScooterMusic.stop(scooter);scooter.discard();return;}
 if(!scooter.isVehicle())for(var player:scooter.level().getEntitiesOfClass(ServerPlayer.class,scooter.getBoundingBox().inflate(.15),p->!p.isSpectator()&&!p.isPassenger()&&p.onGround()))RentalTrip.start(player,scooter);
 }
 private ScooterRental(){}
}
