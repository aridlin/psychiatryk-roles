package pl.aridlin.kukirin;

import net.minecraft.client.Minecraft;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public final class ScooterMusicClient {
   public static void receive(ScooterMusicMenu.Catalog var0, IPayloadContext var1) {
      var1.enqueueWork(() -> ScooterMenus.showMusic(var0));
   }

   public static void receive(ScooterMusicMenu.Options var0, IPayloadContext var1) {
      var1.enqueueWork(() -> {
         if (Minecraft.getInstance().screen instanceof ScooterMusicScreen var1x) {
            var1x.options(var0);
         }
      });
   }

   private ScooterMusicClient() {
   }
}
