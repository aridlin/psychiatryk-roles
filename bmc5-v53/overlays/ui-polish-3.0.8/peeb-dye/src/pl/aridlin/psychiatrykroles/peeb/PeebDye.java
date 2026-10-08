package pl.aridlin.psychiatrykroles.peeb;

import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.DyedItemColor;

/** Vanilla item dye data; normal equipment synchronization carries it to other players. */
public final class PeebDye {
   private PeebDye() {}

   public static ItemStack activeStack(LivingEntity entity) {
      if (entity == null) return ItemStack.EMPTY;
      ItemStack worn = entity.getItemBySlot(EquipmentSlot.CHEST);
      if (worn.is(PeebMode.PEEB.get()) && PeebMode.otherArmorEmpty(entity)) return worn;
      ItemStack main = entity.getMainHandItem();
      if (main.is(PeebMode.PEEB.get())) return main;
      ItemStack off = entity.getOffhandItem();
      return off.is(PeebMode.PEEB.get()) ? off : ItemStack.EMPTY;
   }

   public static int color(ItemStack stack) {
      return stack == null ? 0xFFFFFF : DyedItemColor.getOrDefault(stack, 0xFFFFFF) & 0xFFFFFF;
   }

   /** Preserve original alpha and blend toward dyed skin according to non-tusk bone weights. */
   public static int tint(int originalArgb, int dyeRgb, float bodyWeight) {
      if ((dyeRgb & 0xFFFFFF) == 0xFFFFFF || !(bodyWeight > 0)) return originalArgb;
      float weight = Math.min(1.0F, bodyWeight);
      int r = channel(originalArgb >> 16 & 255, dyeRgb >> 16 & 255, weight);
      int g = channel(originalArgb >> 8 & 255, dyeRgb >> 8 & 255, weight);
      int b = channel(originalArgb & 255, dyeRgb & 255, weight);
      return originalArgb & 0xFF000000 | r << 16 | g << 8 | b;
   }

   private static int channel(int original, int dye, float weight) {
      return Math.round(original * (1.0F - weight + weight * dye / 255.0F));
   }
}
