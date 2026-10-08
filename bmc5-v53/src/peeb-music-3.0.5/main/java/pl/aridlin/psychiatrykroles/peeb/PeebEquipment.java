package pl.aridlin.psychiatrykroles.peeb;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.attributes.AttributeModifier.Operation;
import net.neoforged.neoforge.event.tick.PlayerTickEvent.Post;

public final class PeebEquipment {
   public static final ResourceLocation ARMOR_ID = ResourceLocation.fromNamespaceAndPath("psychiatryk_peeb", "natural_armor");
   public static final double NATURAL_ARMOR = 7.0;
   private static final AttributeModifier MODIFIER = new AttributeModifier(ARMOR_ID, 7.0, Operation.ADD_VALUE);

   private PeebEquipment() {
   }

   public static void tick(Post var0) {
      if (var0.getEntity() instanceof ServerPlayer var1) {
         AttributeInstance var4 = var1.getAttribute(Attributes.ARMOR);
         if (var4 != null) {
            boolean var3 = var1.isAlive() && !var1.isSpectator() && PeebMode.holding(var1);
            if (var3 && !var4.hasModifier(ARMOR_ID)) {
               var4.addTransientModifier(MODIFIER);
            } else if (!var3 && var4.hasModifier(ARMOR_ID)) {
               var4.removeModifier(ARMOR_ID);
            }
         }
      }
   }
}
