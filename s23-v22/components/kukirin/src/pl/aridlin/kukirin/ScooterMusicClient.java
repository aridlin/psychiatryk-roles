package pl.aridlin.kukirin;

import net.minecraft.client.Minecraft;

/** Keep GUI classes out of the common payload registration path. */
public final class ScooterMusicClient {
 private ScooterMusicClient() {}

 public static void receive(ScooterMusicMenu.Catalog catalog) {
  Minecraft.getInstance().setScreen(new ScooterMusicScreen(catalog));
 }
}
