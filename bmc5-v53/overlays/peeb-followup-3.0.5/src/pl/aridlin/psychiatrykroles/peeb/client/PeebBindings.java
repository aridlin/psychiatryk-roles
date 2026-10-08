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
   private static final Key LEFT_ALT = Type.KEYSYM.getOrCreate(342);

   /** Physical polling keeps Peeb freelook reliable even with duplicate mappings. */
   public static boolean freelookDown(Minecraft minecraft) {
      return minecraft.screen == null && !minecraft.isPaused()
         && InputConstants.isKeyDown(minecraft.getWindow().getWindow(), 342);
   }

   /** Called only while Peeb owns the gameplay camera; other bindings are not changed. */
   public static void reserveFreelook(Minecraft minecraft) {
      if (minecraft.screen != null || minecraft.isPaused()) return;
      for (KeyMapping mapping : minecraft.options.keyMappings) {
         if (mapping != PeebClientBootstrap.FREELOOK && !mapping.isUnbound()
            && mapping.getKey().equals(LEFT_ALT)) {
            mapping.setDown(false);
            while (mapping.consumeClick()) {}
         }
      }
   }

   public static List<PeebBindings.Conflict> resolve(KeyMapping[] var0) {
      ArrayList var1 = new ArrayList();

      boolean changed = false;
      if (!PeebClientBootstrap.FREELOOK.getKey().equals(LEFT_ALT)
         || PeebClientBootstrap.FREELOOK.getKeyModifier() != KeyModifier.NONE) {
         PeebClientBootstrap.FREELOOK.setKeyModifierAndCode(KeyModifier.NONE, LEFT_ALT);
         changed = true;
      }
      for (KeyMapping var3 : List.of(PeebClientBootstrap.INTERACT)) {
         if (!var3.isUnbound()) {
            for (KeyMapping var7 : var0) {
               if (var7 != var3 && !var7.isUnbound() && var7.getKey().equals(var3.getKey())) {
                  var1.add(new PeebBindings.Conflict(var3, var7));
                  var3.setKeyModifierAndCode(KeyModifier.NONE, InputConstants.UNKNOWN);
                  var3.setDown(false);

                  while (var3.consumeClick()) {
                  }
                  break;
               }
            }
         }
      }

      if (changed || !var1.isEmpty()) {
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
