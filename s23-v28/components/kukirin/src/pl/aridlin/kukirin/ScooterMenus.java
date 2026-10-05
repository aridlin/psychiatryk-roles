package pl.aridlin.kukirin;
import net.minecraft.client.Minecraft;
public final class ScooterMenus {
 private static Runnable musicBack;
 public static void openFor(Scooter scooter){var mc=Minecraft.getInstance();if(mc.player!=null)mc.setScreen(new net.minecraft.client.gui.screens.inventory.InventoryScreen(mc.player));}
 public static void backToStorage(){var mc=Minecraft.getInstance();mc.setScreen(null);net.neoforged.neoforge.network.PacketDistributor.sendToServer(new ScooterStorageOpen());}
 public static void backToScooter(){var mc=Minecraft.getInstance();mc.setScreen(null);if(mc.player!=null&&mc.player.getVehicle() instanceof Scooter s)openFor(s);}
 public static void requestMusic(Runnable back){musicBack=back;ScooterMusicScreen.send("open","");}
 public static void showMusic(ScooterMusicMenu.Catalog catalog){var back=musicBack;musicBack=null;Minecraft.getInstance().setScreen(new ScooterMusicScreen(catalog,back==null?ScooterMenus::backToScooter:back));}
 private ScooterMenus(){}
}
