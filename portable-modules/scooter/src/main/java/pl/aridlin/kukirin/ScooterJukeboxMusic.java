package pl.aridlin.kukirin;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.network.PacketDistributor;
/** The same server catalog, controlled only while within reach of the jukebox that opened it. */
@EventBusSubscriber(modid="goplanska_kukirin")
public final class ScooterJukeboxMusic {
 private record Open(ScooterMusic.Source source,long expires){}
 private static final Map<UUID,Open> opened=new HashMap<>();
 @SubscribeEvent public static void use(PlayerInteractEvent.RightClickBlock e){
  if(!e.getEntity().isShiftKeyDown()||e.getHand()!=net.minecraft.world.InteractionHand.MAIN_HAND||!(e.getLevel().getBlockEntity(e.getPos()) instanceof net.minecraft.world.level.block.entity.JukeboxBlockEntity))return;
  e.setCancellationResult(net.minecraft.world.InteractionResult.SUCCESS);e.setCanceled(true);
  if(e.getEntity() instanceof ServerPlayer p){var source=ScooterMusic.openJukebox(p,e.getPos());opened.put(p.getUUID(),new Open(source,p.serverLevel().getGameTime()+20*60*5));PacketDistributor.sendToPlayer(p,new ScooterMusicMenu.Catalog(ScooterMusic.songs(),true,false,source.looping()));}
 }
 static boolean handle(ServerPlayer p,String action,String song){
  Open open=opened.get(p.getUUID());if(open==null)return false;
  var source=open.source;if(p.level()!=source.level||source.distance(p)>8*8||!source.valid()||source.level.getGameTime()>open.expires){opened.remove(p.getUUID());return true;}
  switch(action){case "play"->{if(source.level.getBlockEntity(source.block) instanceof net.minecraft.world.level.block.entity.JukeboxBlockEntity j)j.getSongPlayer().stop(source.level,source.level.getBlockState(source.block));ScooterMusic.play(p,source,song);}case "loop"->ScooterMusic.jukeboxLoop(source,song.equals("true"));case "stop"->ScooterMusic.stop(source.id);default->{}}
  return true;
 }
 static void forget(ServerPlayer p){opened.remove(p.getUUID());}
 static void clear(){opened.clear();}
 @SubscribeEvent public static void logout(net.neoforged.neoforge.event.entity.player.PlayerEvent.PlayerLoggedOutEvent e){opened.remove(e.getEntity().getUUID());}
 private ScooterJukeboxMusic(){}
}
