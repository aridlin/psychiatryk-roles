package pl.aridlin.psychiatrykroles.io;
@net.neoforged.fml.common.EventBusSubscriber(modid="psychiatryk_roles")
public final class SaveIoLifecycle {
 @net.neoforged.bus.api.SubscribeEvent public static void stopped(net.neoforged.neoforge.event.server.ServerStoppedEvent event){
  java.io.IOException failure=null;
  try{SableIoBarrier.flushAll();}catch(java.io.IOException e){failure=e;}
  try{PlayerSaves.closeAll();}catch(java.io.IOException e){if(failure==null)failure=e;else failure.addSuppressed(e);}
  // Throwing through this event would skip NeoForge's exit latch and config cleanup.
  if(failure!=null)org.slf4j.LoggerFactory.getLogger("GoplanskaPlayerSaves").error("Shutdown save IO failed; last committed files remain available and pending snapshots could not be drained",failure);
 }
}
