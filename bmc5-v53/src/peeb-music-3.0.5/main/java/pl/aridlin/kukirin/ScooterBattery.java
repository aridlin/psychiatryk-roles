package pl.aridlin.kukirin;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.EntityMountEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent.PlayerLoggedInEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent.PlayerLoggedOutEvent;
import pl.aridlin.psychiatrykroles.peeb.mixin.ScooterBatteryAccess;

@EventBusSubscriber(
   modid = "goplanska_kukirin"
)
public final class ScooterBattery {
   public static final int CAPACITY = 72000;
   public static final String KEY = "GoplanskaScooterBattery";
   public static final String FRACTION = "GoplanskaBatteryDrainFraction";
   public static final String PENDING = "GoplanskaBatteryPendingDrain";
   public static final String OFFLINE_AT = "GoplanskaBatteryOfflineAt";
   public static final String OFFLINE_OWNER = "GoplanskaBatteryOfflineOwner";
   private static final String LEGACY_REMAINDER = "GoplanskaBatteryDrainRemainder";

   public static int stored(ItemStack var0) {
      CompoundTag var1 = ((CustomData)var0.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY)).getUnsafe();
      return Math.clamp(var1.contains("GoplanskaScooterBattery") ? (long)var1.getInt("GoplanskaScooterBattery") : 72000L, 0, 72000);
   }

   public static boolean infinite(ItemStack var0) {
      return ScooterUpgradeRecipe.has(var0, "infinite");
   }

   public static int percent(int var0) {
      return Math.clamp((long)((int)(100L * (long)var0 / 72000L)), 0, 100);
   }

   public static int next(int var0, int var1, boolean var2, boolean var3) {
      return Math.clamp((long)var0 + (var3 ? -2L * (long)var1 : (var2 ? (long)var1 : 0L)), 0, 72000);
   }

   public static InteractionResult insertCoal(Scooter var0, Player var1, InteractionHand var2) {
      if (ScooterRental.isRental(var0)) {
         return InteractionResult.FAIL;
      } else {
         if (!var0.level().isClientSide) {
            flush(var0);
         }

         ItemStack var3 = var0.getItemBySlot(EquipmentSlot.FEET);
         ItemStack var4 = var1.getItemInHand(var2);
         if (!var4.is(Items.COAL)) {
            return InteractionResult.PASS;
         } else if (!var0.isVehicle() && var0.onGround() && !(var0.getDeltaMovement().lengthSqr() > 6.250000000000001E-4)) {
            if (!infinite(var3) && stored(var3) < 72000) {
               if (!var0.level().isClientSide) {
                  int var5 = CoalChargePolicy.required(stored(var3), var4.getCount());
                  int var6 = CoalChargePolicy.charged(stored(var3), var5);
                  ItemStack var7 = var3.copy();
                  CustomData.update(DataComponents.CUSTOM_DATA, var7, var1x -> var1x.putInt("GoplanskaScooterBattery", var6));
                  var0.setItemSlot(EquipmentSlot.FEET, var7);
                  var0.batteryState(var6, false, false);
                  if (!var1.isCreative()) {
                     var4.shrink(var5);
                  }

                  var1.getInventory().setChanged();
                  var1.displayClientMessage(Component.translatable("scooter.battery.charged", new Object[]{percent(var6)}), true);
               }

               return InteractionResult.sidedSuccess(var0.level().isClientSide);
            } else {
               if (!var0.level().isClientSide) {
                  var1.displayClientMessage(Component.translatable("scooter.battery.full"), true);
               }

               return InteractionResult.sidedSuccess(var0.level().isClientSide);
            }
         } else {
            if (!var0.level().isClientSide) {
               var1.displayClientMessage(Component.translatable("scooter.battery.park"), true);
            }

            return InteractionResult.FAIL;
         }
      }
   }

   public static boolean poweredDepot(Scooter var0) {
      return CreateCharging.poweredDepot(var0);
   }

   public static void tick(Scooter var0) {
      if (!var0.level().isClientSide && !ScooterRental.isRental(var0)) {
         ItemStack var1 = var0.getItemBySlot(EquipmentSlot.FEET);
         boolean var2 = infinite(var1);
         int var3 = stored(var1);
         boolean var4 = !var2 && !var0.isVehicle() && poweredDepot(var0);
         var0.batteryState(var2 ? 72000 : var3, var4, var2);
         if (!var2) {
            if (var0.isVehicle()) {
               double var5 = var0.getPersistentData().getDouble("GoplanskaBatteryPendingDrain");
               if (!Double.isFinite(var5) || var5 < 0.0) {
                  var5 = 0.0;
               }

               boolean var7 = (Boolean)var0.getEntityData().get(ScooterBatteryAccess.peeb$brakeData());
               var5 += ScooterBatteryPolicy.tickCost(var7 ? 0.0 : (double)var0.throttle(), ScooterEnchants.level(var0, "unbreaking"));
               if (var5 > 0.0) {
                  var0.getPersistentData().putDouble("GoplanskaBatteryPendingDrain", var5);
               }
            }

            if (!var0.isVehicle()) {
               flush(var0);
            }

            if (var0.tickCount % 20 == 0) {
               flush(var0);
               var1 = var0.getItemBySlot(EquipmentSlot.FEET);
               var3 = stored(var1);
               int var6 = var4 ? next(var3, 20, true, false) : var3;
               if (var0.isVehicle()
                  && var0.tickCount % 100 == 0
                  && var3 < 72000
                  && ScooterEnchants.level(var0, "mending") > 0
                  && var0.getControllingPassenger() instanceof ServerPlayer var14
                  && (var14.experienceLevel > 0 || var14.experienceProgress > 0.0F)) {
                  var14.giveExperiencePoints(-1);
                  var6 = Math.min(72000, var6 + 400);
               }

               if (var6 != var3) {
                  var1 = var1.copy();
                  int var15 = var6;
                  CustomData.update(DataComponents.CUSTOM_DATA, var1, var1x -> var1x.putInt("GoplanskaScooterBattery", var15));
                  var0.setItemSlot(EquipmentSlot.FEET, var1);
                  var0.batteryState(var6, var4, false);
               }

               if (var6 == 0 && var0.isVehicle() && var0.getControllingPassenger() instanceof ServerPlayer var16 && var0.tickCount % 100 == 0) {
                  var16.displayClientMessage(Component.translatable("scooter.battery.empty"), true);
               }
            }
         }
      }
   }

   public static void flush(Scooter var0) {
      if (!var0.level().isClientSide && !ScooterRental.isRental(var0)) {
         double var1 = var0.getPersistentData().getDouble("GoplanskaBatteryPendingDrain");
         if (Double.isFinite(var1) && !(var1 <= 0.0)) {
            ItemStack var3 = var0.getItemBySlot(EquipmentSlot.FEET);
            CompoundTag var4 = ((CustomData)var3.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY)).getUnsafe();
            double var5 = 0.0;
            if (var4.contains("GoplanskaBatteryDrainFraction")) {
               var5 = var4.getDouble("GoplanskaBatteryDrainFraction");
            } else {
               var1 += (double)Math.clamp((long)var4.getInt("GoplanskaBatteryDrainRemainder"), 0, 4)
                  / (double)(2 + Math.clamp((long)ScooterEnchants.level(var0, "unbreaking"), 0, 3));
            }

            ScooterBatteryPolicy.Drain var7 = ScooterBatteryPolicy.drain(stored(var3), var5, var1);
            var3 = var3.copy();
            CustomData.update(DataComponents.CUSTOM_DATA, var3, var1x -> {
               var1x.putInt("GoplanskaScooterBattery", var7.charge());
               var1x.putDouble("GoplanskaBatteryDrainFraction", var7.fraction());
               var1x.remove("GoplanskaBatteryDrainRemainder");
            });
            var0.setItemSlot(EquipmentSlot.FEET, var3);
            var0.getPersistentData().remove("GoplanskaBatteryPendingDrain");
            var0.batteryState(var7.charge(), false, infinite(var3));
         } else {
            var0.getPersistentData().remove("GoplanskaBatteryPendingDrain");
         }
      }
   }

   @SubscribeEvent
   public static void dismount(EntityMountEvent var0) {
      if (var0.isDismounting() && var0.getEntityBeingMounted() instanceof Scooter var1) {
         flush(var1);
      }
   }

   private static List<ItemStack> carried(ServerPlayer var0) {
      ArrayList var1 = new ArrayList(37);

      for (int var2 = 0; var2 < 36; var2++) {
         var1.add(var0.getInventory().getItem(var2));
      }

      var1.add(var0.getOffhandItem());
      return var1;
   }

   private static boolean eligible(ItemStack var0, ServerPlayer var1) {
      if (var0.is((Item)Kukirin.ITEM.get())
         && !infinite(var0)
         && !((CustomData)var0.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY)).getUnsafe().getBoolean("GoplanskaRental")) {
         UUID var2 = ScooterEnchants.owner(var0);
         return var2 == null || var2.equals(var1.getUUID());
      } else {
         return false;
      }
   }

   @SubscribeEvent
   public static void logout(PlayerLoggedOutEvent var0) {
      if (var0.getEntity() instanceof ServerPlayer var1) {
         long var6 = System.currentTimeMillis();

         for (ItemStack var5 : carried(var1)) {
            if (eligible(var5, var1)) {
               CustomData.update(DataComponents.CUSTOM_DATA, var5, var3 -> {
                  var3.putLong("GoplanskaBatteryOfflineAt", var6);
                  var3.putUUID("GoplanskaBatteryOfflineOwner", var1.getUUID());
               });
            }
         }

         var1.getInventory().setChanged();
      }
   }

   @SubscribeEvent
   public static void login(PlayerLoggedInEvent var0) {
      if (var0.getEntity() instanceof ServerPlayer var1) {
         long var11 = System.currentTimeMillis();

         for (ItemStack var5 : carried(var1)) {
            if (var5.is((Item)Kukirin.ITEM.get())) {
               CompoundTag var6 = ((CustomData)var5.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY)).getUnsafe();
               if (var6.contains("GoplanskaBatteryOfflineAt") || var6.contains("GoplanskaBatteryOfflineOwner")) {
                  long var7 = var6.getLong("GoplanskaBatteryOfflineAt");
                  boolean var9 = var6.hasUUID("GoplanskaBatteryOfflineOwner") && var1.getUUID().equals(var6.getUUID("GoplanskaBatteryOfflineOwner"));
                  int var10 = var9 && eligible(var5, var1) && var7 > 0L && var7 <= var11
                     ? ScooterBatteryPolicy.offlineCharge(stored(var5), var11 - var7)
                     : stored(var5);
                  CustomData.update(DataComponents.CUSTOM_DATA, var5, var1x -> {
                     var1x.putInt("GoplanskaScooterBattery", var10);
                     var1x.remove("GoplanskaBatteryOfflineAt");
                     var1x.remove("GoplanskaBatteryOfflineOwner");
                  });
               }
            }
         }

         var1.getInventory().setChanged();
      }
   }

   private ScooterBattery() {
   }
}
