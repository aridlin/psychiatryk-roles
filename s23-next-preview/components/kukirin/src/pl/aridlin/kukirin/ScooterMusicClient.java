package pl.aridlin.kukirin;
/** Only invoked for a clientbound packet on the physical client. No Screen references in common registration. */
public final class ScooterMusicClient {
 public static void receive(ScooterMusicMenu.Catalog data,net.neoforged.neoforge.network.handling.IPayloadContext ctx){ctx.enqueueWork(()->ScooterMenus.showMusic(data));}
 private ScooterMusicClient(){}
}
