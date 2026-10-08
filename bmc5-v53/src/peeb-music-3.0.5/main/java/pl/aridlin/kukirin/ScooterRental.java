package pl.aridlin.kukirin;

import java.util.List;
import java.util.stream.IntStream;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.ItemLike;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.neoforge.event.entity.EntityMountEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;

@EventBusSubscriber(
   modid = "goplanska_kukirin"
)
public final class ScooterRental {
   public static final RentalConfig CONFIG = new RentalConfig(FMLPaths.CONFIGDIR.get().resolve("goplanska-scooter/rentals.properties"));

   public static boolean isRental(Scooter var0) {
      return data(var0).getBoolean("GoplanskaRental");
   }

   private static CompoundTag data(Scooter var0) {
      return ((CustomData)var0.getItemBySlot(EquipmentSlot.FEET).getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY)).getUnsafe();
   }

   public static int battery(Scooter var0) {
      return Math.max(0, data(var0).getInt("RentalBattery"));
   }

   public static List<ItemStack> presets() {
      return IntStream.range(0, 3).mapToObj(ScooterRental::preset).toList();
   }

   public static ItemStack preset(int var0) {
      ItemStack var1 = ScooterDyeRecipe.paint(
         new ItemStack((ItemLike)Kukirin.ITEM.get()), var0 == 0 ? DyeColor.LIME : (var0 == 1 ? DyeColor.LIGHT_BLUE : DyeColor.ORANGE)
      );
      CustomData.update(DataComponents.CUSTOM_DATA, var1, var1x -> {
         var1x.putBoolean("GoplanskaRental", true);
         var1x.putInt("RentalBattery", RentalPolicy.freshBatteryTicks());
         var1x.putInt("RentalBrand", var0);
      });
      var1.set(DataComponents.CUSTOM_NAME, Component.literal((var0 == 0 ? "Lime" : (var0 == 1 ? "Bolt" : "City")) + " rental"));
      return var1;
   }

   public static void initialize(Scooter var0, int var1) {
      var0.setItemSlot(EquipmentSlot.FEET, preset(var1));
      CompoundTag var2 = var0.getPersistentData();
      var2.putLong("RentalLastSeen", var0.level().getGameTime());
      var2.putLong("RentalEmptySince", -1L);
   }

   public static InteractionResult interact(Scooter var0, Player var1) {
      if (var0.level().isClientSide) {
         return InteractionResult.SUCCESS;
      } else if (var1 instanceof ServerPlayer var2) {
         if (var1.isShiftKeyDown()) {
            if (var0.isVehicle()) {
               return InteractionResult.FAIL;
            } else {
               ((ServerLevel)var0.level()).sendParticles(ParticleTypes.POOF, var0.getX(), var0.getY() + 0.3, var0.getZ(), 40, 0.5, 0.3, 0.5, 0.08);
               ScooterMusic.stop(var0);
               var0.discard();
               return InteractionResult.CONSUME;
            }
         } else if (var0.isVehicle()) {
            return InteractionResult.PASS;
         } else if (battery(var0) == 0) {
            var1.displayClientMessage(Component.literal("Rental battery empty."), true);
            return InteractionResult.FAIL;
         } else {
            CompoundTag var3 = var0.getPersistentData();
            long var4 = var0.level().getGameTime();
            boolean var6 = var3.hasUUID("RentalRider") && var3.getUUID("RentalRider").equals(var1.getUUID()) && var3.getLong("RentalPaidUntil") >= var4;
            long var7 = CONFIG.get().price();
            if (!var6) {
               if (!RentalPayments.charge(var2, var7)) {
                  var1.displayClientMessage(Component.literal("Rental costs " + var7 + " rental credits. The default currency is iron nuggets."), false);
                  return InteractionResult.FAIL;
               }

               var3.putUUID("RentalRider", var1.getUUID());
               var3.putLong("RentalPaidUntil", var4 + 1200L);
            }

            if (!var1.startRiding(var0)) {
               if (!var6) {
                  RentalPayments.refund(var2, var7);
               }

               var3.remove("RentalRider");
               var3.remove("RentalPaidUntil");
               return InteractionResult.FAIL;
            } else {
               return InteractionResult.CONSUME;
            }
         }
      } else {
         return InteractionResult.FAIL;
      }
   }

   public static void renderedBy(Scooter var0, ServerPlayer var1) {
      if (isRental(var0) && var1.level() == var0.level() && var1.distanceToSqr(var0) < 25600.0) {
         var0.getPersistentData().putLong("RentalLastSeen", var0.level().getGameTime());
      }
   }

   public static void tick(Scooter var0) {
      if (!var0.level().isClientSide && isRental(var0)) {
         CompoundTag var1 = var0.getPersistentData();
         long var2 = var0.level().getGameTime();
         int var4 = battery(var0);
         if (var0.isVehicle()) {
            if (var0.getFirstPassenger() instanceof Zombie var5) {
               if (ScooterEnchants.level(var0, "knockback") == 0) {
                  ItemStack var13 = var0.getItemBySlot(EquipmentSlot.FEET).copy();
                  var13.enchant(
                     var0.registryAccess()
                        .lookupOrThrow(Registries.ENCHANTMENT)
                        .getOrThrow(ResourceKey.create(Registries.ENCHANTMENT, ResourceLocation.parse("minecraft:knockback"))),
                     1
                  );
                  var0.setItemSlot(EquipmentSlot.FEET, var13);
               }

               Player var14 = var0.level().getNearestPlayer(var0, 24.0);
               var5.setNoAi(true);
               if (var14 != null) {
                  Vec3 var7 = var14.position().subtract(var0.position());
                  float var8 = (float)Math.toDegrees(Math.atan2(-var7.x, var7.z));
                  var0.setYRot(Mth.approachDegrees(var0.getYRot(), var8, 4.0F));
                  if (PortableOptions.get().impactDamage() && var14.distanceToSqr(var0) < 3.0 && var2 % 20L == 0L) {
                     var14.hurt(var0.damageSources().mobAttack(var5), 3.0F);
                  }
               }
            }

            if (var4 <= 0) {
               for (Entity var15 : var0.getPassengers()) {
                  if (var15 instanceof Zombie var19) {
                     var19.setNoAi(false);
                  }
               }

               var0.ejectPassengers();
            } else {
               var1.putLong("RentalPaidUntil", var2 + 1200L);
               if (var2 % 20L == 0L) {
                  ItemStack var10 = var0.getItemBySlot(EquipmentSlot.FEET).copy();
                  int var16 = Math.max(0, var4 - 20);
                  CustomData.update(DataComponents.CUSTOM_DATA, var10, var1x -> var1x.putInt("RentalBattery", var16));
                  var0.setItemSlot(EquipmentSlot.FEET, var10);
               }
            }
         }

         if (var4 <= 0 && !var1.contains("RentalEmptySince")) {
            var1.putLong("RentalEmptySince", var2);
         }

         if (var4 <= 0 && var1.getLong("RentalEmptySince") < 0L) {
            var1.putLong("RentalEmptySince", var2);
         }

         if (RentalPolicy.cleanup(var0.isVehicle(), var2, var1.getLong("RentalLastSeen"), var1.getLong("RentalEmptySince"))) {
            for (Entity var18 : var0.getPassengers()) {
               if (var18 instanceof Zombie var20) {
                  var20.setNoAi(false);
               }
            }

            var0.ejectPassengers();
            ScooterMusic.stop(var0);
            var0.discard();
         } else {
            if (!var0.isVehicle()) {
               for (ServerPlayer var17 : var0.level()
                  .getEntitiesOfClass(
                     ServerPlayer.class, var0.getBoundingBox().inflate(0.15), var0x -> !var0x.isSpectator() && !var0x.isPassenger() && var0x.onGround()
                  )) {
                  RentalTrip.start(var17, var0);
               }
            }
         }
      }
   }

   public static void zombieDeparture(Scooter var0, Zombie var1) {
      if (!var0.level().isClientSide && isRental(var0)) {
         CompoundTag var2 = var0.getPersistentData();
         long var3 = var0.level().getGameTime();
         var2.putLong("RentalLastSeen", var3);
         var2.putLong("RentalEmptySince", battery(var0) <= 0 ? var3 : -1L);
         var2.remove("RentalRider");
         var2.remove("RentalPaidUntil");
         var1.setNoAi(false);
      }
   }

   @SubscribeEvent(
      priority = EventPriority.LOWEST
   )
   public static void zombieDeath(LivingDeathEvent var0) {
      if (var0.getEntity() instanceof Zombie var1 && var1.getVehicle() instanceof Scooter var2 && isRental(var2)) {
         zombieDeparture(var2, var1);
         var1.stopRiding();
      }
   }

   @SubscribeEvent(
      priority = EventPriority.LOWEST
   )
   public static void zombieDismount(EntityMountEvent var0) {
      if (var0.isDismounting() && var0.getEntityMounting() instanceof Zombie var1 && var0.getEntityBeingMounted() instanceof Scooter var2) {
         zombieDeparture(var2, var1);
      }
   }

   private ScooterRental() {
   }
}
