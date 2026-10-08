package pl.aridlin.kukirin;

import net.minecraft.client.Minecraft;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public final class ScooterMusicClient {
   private static UUID transfer;
   private static final List<String> incoming = new ArrayList<>();

   private static void showCatalog(ScooterMusicMenu.Catalog catalog, boolean imported) {
      showCatalog(catalog, imported, "");
   }

   private static void showCatalog(ScooterMusicMenu.Catalog catalog, boolean imported, String focusSong) {
      if (imported || YouTubeMusicScreen.library(Minecraft.getInstance().screen) != null) YouTubeMusicScreen.catalog(catalog, imported, focusSong);
      else ScooterMenus.showMusic(catalog);
   }

   public static void receive(ScooterMusicMenu.CatalogPart part, IPayloadContext context) {
      context.enqueueWork(() -> {
         if (part.offset() == 0) {
            transfer = part.transfer();
            incoming.clear();
         }
         if (!part.transfer().equals(transfer) || part.offset() != incoming.size()) return;
         incoming.addAll(part.songs());
         if (part.last()) {
            ScooterMusicMenu.Catalog complete = new ScooterMusicMenu.Catalog(List.copyOf(incoming), part.wav(), part.disc(), part.loop(), part.paused(), part.volume(), part.currentSong(), part.source());
            transfer = null;
            incoming.clear();
            showCatalog(complete, part.imported(), part.focusSong());
         }
      });
   }
   public static void receive(ScooterMusicMenu.Catalog var0, IPayloadContext var1) {
      var1.enqueueWork(() -> {
         transfer = null;
         incoming.clear();
         showCatalog(var0, false);
      });
   }

   public static void receive(ScooterMusicMenu.Options var0, IPayloadContext var1) {
      var1.enqueueWork(() -> {
         ScooterMusicScreen picker = YouTubeMusicScreen.library(Minecraft.getInstance().screen);
         if (picker != null) picker.options(var0);
      });
   }

   private ScooterMusicClient() {
   }
}
