package pl.aridlin.psychiatrykroles.peeb.client;

import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.blaze3d.platform.InputConstants.Key;
import com.mojang.blaze3d.platform.InputConstants.Type;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.client.settings.KeyModifier;

public final class PeebBindings {
   private static Key unusedFreelook(KeyMapping[] var0, KeyMapping var1) {
      for (int var5 : new int[]{342, 346, 299, 301}) {
         Key var6 = Type.KEYSYM.getOrCreate(var5);
         boolean var7 = false;

         for (KeyMapping var11 : var0) {
            if (var11 != var1 && !var11.isUnbound() && var11.getKey().equals(var6)) {
               var7 = true;
               break;
            }
         }

         if (!var7) {
            return var6;
         }
      }

      return InputConstants.UNKNOWN;
   }

   public static List<PeebBindings.Conflict> resolve(KeyMapping[] var0) {
      ArrayList var1 = new ArrayList();

      for (KeyMapping var3 : List.of(PeebClientBootstrap.INTERACT, PeebClientBootstrap.FREELOOK)) {
         if (!var3.isUnbound()) {
            for (KeyMapping var7 : var0) {
               if (var7 != var3 && !var7.isUnbound() && var7.getKey().equals(var3.getKey())) {
                  var1.add(new PeebBindings.Conflict(var3, var7));
                  var3.setKeyModifierAndCode(KeyModifier.NONE, var3 == PeebClientBootstrap.FREELOOK ? unusedFreelook(var0, var3) : InputConstants.UNKNOWN);
                  var3.setDown(false);

                  while (var3.consumeClick()) {
                  }
                  break;
               }
            }
         }
      }

      if (!var1.isEmpty()) {
         KeyMapping.resetMapping();
      }

      return List.copyOf(var1);
   }

   public static void audit(Minecraft var0) {
      for (PeebBindings.Conflict var2 : resolve(var0.options.keyMappings)) {
         var0.gui
            .getChat()
            .addMessage(
               Component.literal("Peeb: ")
                  .append(Component.translatable(var2.peeb.getName()))
                  .append(var2.peeb.isUnbound() ? " is unbound because that key is used by " : " now uses ")
                  .append((Component)(var2.peeb.isUnbound() ? Component.empty() : var2.peeb.getTranslatedKeyMessage()))
                  .append(var2.peeb.isUnbound() ? "" : " because its previous key is used by ")
                  .append(Component.translatable(var2.existing.getName()))
                  .append(". Choose an unused key in Controls.")
            );
      }
   }

   private PeebBindings() {
   }

   public static record Conflict(KeyMapping peeb, KeyMapping existing) {
   }
}
