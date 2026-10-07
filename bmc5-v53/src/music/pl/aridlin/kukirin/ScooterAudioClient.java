package pl.aridlin.kukirin;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.sounds.AudioStream;
import net.minecraft.sounds.SoundSource;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
@EventBusSubscriber(modid="goplanska_kukirin",value=Dist.CLIENT)
public final class ScooterAudioClient {
 private static final class Transfer {final UUID session;final byte[] bytes;int offset;long touched;Transfer(ScooterAudioPacket p){session=p.session();bytes=new byte[p.total()];}}
 private static final Map<UUID,Transfer> transfers=new HashMap<>();
 private static final Map<UUID,Moving> sounds=new HashMap<>();
 private static final Map<UUID,UUID> decoding=new HashMap<>();
 private static final Map<UUID,Integer> discs=new HashMap<>();
 private static final Map<UUID,ScooterAudioUrlPacket> urls=new HashMap<>();
 private static net.minecraft.client.multiplayer.ClientLevel lastLevel;
 public static void receive(ScooterAudioPacket p){syncLevel(Minecraft.getInstance());
  if(p.total()==0){var pending=urls.get(p.scooter());if(pending!=null&&pending.session().equals(p.session()))urls.remove(p.scooter());var t=transfers.get(p.scooter());if(t!=null&&t.session.equals(p.session()))transfers.remove(p.scooter());if(p.session().equals(decoding.get(p.scooter())))decoding.remove(p.scooter());var sound=sounds.get(p.scooter());if(sound!=null&&p.session().equals(sound.session))remove(p.scooter());return;}
  if(p.total()<44||p.total()>ScooterMusic.MAX_BYTES||p.offset()<0||p.bytes().length==0||p.bytes().length>32768||(long)p.offset()+p.bytes().length>p.total())return;
  var t=transfers.get(p.scooter());if(p.offset()==0){urls.remove(p.scooter());if(transfers.size()+decoding.size()>=4&&!transfers.containsKey(p.scooter()))return;t=new Transfer(p);transfers.put(p.scooter(),t);}if(t==null||!t.session.equals(p.session())||t.offset!=p.offset()||t.bytes.length!=p.total())return;System.arraycopy(p.bytes(),0,t.bytes,t.offset,p.bytes().length);t.offset+=p.bytes().length;t.touched=System.nanoTime();if(t.offset==t.bytes.length){transfers.remove(p.scooter());decoding.put(p.scooter(),p.session());byte[] wav=t.bytes;CompletableFuture.supplyAsync(()->{try{return ScooterWav.decode(wav);}catch(Exception ex){throw new java.util.concurrent.CompletionException(ex);}}).whenComplete((pcm,error)->Minecraft.getInstance().execute(()->{if(!p.session().equals(decoding.get(p.scooter())))return;decoding.remove(p.scooter());if(error!=null){org.slf4j.LoggerFactory.getLogger("ScooterAudio").warn("WAV decoding failed ({}).",error.getClass().getSimpleName());var player=Minecraft.getInstance().player;if(player!=null)player.displayClientMessage(net.minecraft.network.chat.Component.literal("Scooter WAV could not be decoded."),false);return;}var s=find(p.scooter());if(s!=null){remove(p.scooter());var sound=new Moving(s,p.session(),pcm);sounds.put(p.scooter(),sound);Minecraft.getInstance().getSoundManager().play(sound);}}));}
 }
 private static Scooter find(UUID id){var level=Minecraft.getInstance().level;if(level!=null)for(var e:level.entitiesForRendering())if(e instanceof Scooter s&&s.getUUID().equals(id))return s;return null;}
 private static void remove(UUID id){var old=sounds.remove(id);if(old!=null){old.cancel();Minecraft.getInstance().getSoundManager().stop(old);}}
 @SubscribeEvent public static void tick(net.neoforged.neoforge.client.event.ClientTickEvent.Post e){var mc=Minecraft.getInstance();syncLevel(mc);if(mc.level==null)return;transfers.entrySet().removeIf(entry->System.nanoTime()-entry.getValue().touched>30_000_000_000L);sounds.entrySet().removeIf(entry->entry.getValue().isStopped());for(var entity:mc.level.entitiesForRendering())if(entity instanceof Scooter s){int serial=s.discSerial();if(discs.getOrDefault(s.getUUID(),0)!=serial){discs.put(s.getUUID(),serial);var old=sounds.get(s.getUUID());if(old!=null&&old.pcm==null)remove(s.getUUID());if(s.discPlaying()){urls.remove(s.getUUID());decoding.remove(s.getUUID());remove(s.getUUID());}if(s.discPlaying()){var stack=ScooterMusic.disc(s.getItemBySlot(net.minecraft.world.entity.EquipmentSlot.FEET),s);var playable=stack.get(net.minecraft.core.component.DataComponents.JUKEBOX_PLAYABLE);if(playable!=null)playable.song().unwrap(s.registryAccess()).ifPresent(holder->{var sound=new Moving(s,holder.value().soundEvent().value(),holder.value().lengthInTicks());sounds.put(s.getUUID(),sound);mc.getSoundManager().play(sound);});}}}}
 private static void syncLevel(Minecraft mc){if(mc.level!=lastLevel){for(var id:new ArrayList<>(sounds.keySet()))remove(id);transfers.clear();decoding.clear();discs.clear();urls.clear();lastLevel=mc.level;}}
 /** URL completion is dispatched to the client thread and cannot revive a stopped session or an old level. */
 public static void receive(ScooterAudioUrlPacket p){
  var mc=Minecraft.getInstance();syncLevel(mc);if(mc.level==null)return;
  var old=sounds.get(p.source());if(old!=null&&p.session().equals(old.session)){old.loopOverride=p.loop();return;}
  var pending=urls.get(p.source());if(pending!=null&&pending.session().equals(p.session())){urls.put(p.source(),p);return;}
  var active=new HashSet<UUID>(sounds.keySet());active.addAll(decoding.keySet());active.addAll(transfers.keySet());active.addAll(urls.keySet());if(active.size()>=4&&!active.contains(p.source()))return;
  if(p.block()&&!(mc.level.getBlockEntity(p.position()) instanceof net.minecraft.world.level.block.entity.JukeboxBlockEntity))return;
  remove(p.source());transfers.remove(p.source());decoding.put(p.source(),p.session());urls.put(p.source(),p);
  var level=mc.level;var cache=net.neoforged.fml.loading.FMLPaths.GAMEDIR.get().resolve("cache/goplanska-scooter/music");
  (p.url().startsWith("bundled:")?bundled(p):ScooterMusicHttpCache.fetch(cache,p.url(),p.bearer(),p.sha256(),p.bytes())).thenApplyAsync(bytes->{try{return ScooterWav.decode(bytes);}catch(Exception ex){throw new java.util.concurrent.CompletionException(ex);}}).whenComplete((pcm,error)->mc.execute(()->{
   var current=urls.get(p.source());if(current==null||!current.session().equals(p.session())||!p.session().equals(decoding.get(p.source()))||mc.level!=level)return;
   decoding.remove(p.source());urls.remove(p.source());if(error!=null){if(mc.player!=null)mc.player.displayClientMessage(net.minecraft.network.chat.Component.literal("Song download failed; reopen Music to retry."),false);return;}
   Scooter entity=p.block()?null:find(p.source());if(!p.block()&&entity==null)return;
   if(p.block()&&!(level.getBlockEntity(p.position()) instanceof net.minecraft.world.level.block.entity.JukeboxBlockEntity))return;
   var sound=new Moving(p.source(),entity,level,p.block()?p.position():null,p.session(),pcm,current.loop());sounds.put(p.source(),sound);mc.getSoundManager().play(sound);
  }));
 }
 private static CompletableFuture<byte[]> bundled(ScooterAudioUrlPacket p){return CompletableFuture.supplyAsync(()->{try{String name=p.url().substring(8);if(!name.equals("chiki_ride.wav")&&!name.equals("night_motor.wav"))throw new java.io.IOException();byte[] bytes;try(var stream=ScooterAudioClient.class.getResourceAsStream("/data/goplanska_kukirin/music/"+name)){if(stream==null)throw new java.io.IOException();bytes=stream.readNBytes(ScooterMusic.MAX_BYTES+1);}if(!ScooterMusicHttpCache.valid(bytes,p.sha256(),p.bytes()))throw new java.io.IOException();return bytes;}catch(Exception ex){throw new java.util.concurrent.CompletionException(new java.io.IOException("Bundled song unavailable"));}});}
 /** BLOCKS lets Sound Physics process both moving scooters and fixed jukeboxes. */
 public static final class Moving extends AbstractTickableSoundInstance {
  final UUID source;final Scooter scooter;final net.minecraft.client.multiplayer.ClientLevel level;final net.minecraft.core.BlockPos block;final UUID session;final ScooterWav.Pcm pcm;final int duration;Boolean loopOverride;int age;
  Moving(Scooter s,UUID session,ScooterWav.Pcm pcm){this(s.getUUID(),s,(net.minecraft.client.multiplayer.ClientLevel)s.level(),null,session,pcm,null);}
  Moving(UUID source,Scooter s,net.minecraft.client.multiplayer.ClientLevel level,net.minecraft.core.BlockPos block,UUID session,ScooterWav.Pcm pcm,Boolean loop){super(Kukirin.WAV.get(),SoundSource.BLOCKS,net.minecraft.util.RandomSource.create());this.source=source;scooter=s;this.level=level;this.block=block;this.session=session;this.pcm=pcm;loopOverride=loop;duration=(int)Math.ceil(pcm.samples().length/pcm.format().getFrameRate()/pcm.format().getFrameSize()*20);relative=false;looping=false;position();volume=ScooterClientOptions.get().musicVolume;}
  Moving(Scooter s,net.minecraft.sounds.SoundEvent event,int duration){super(event,SoundSource.BLOCKS,net.minecraft.util.RandomSource.create());source=s.getUUID();scooter=s;level=(net.minecraft.client.multiplayer.ClientLevel)s.level();block=null;session=null;pcm=null;this.duration=duration;relative=false;looping=false;position();volume=ScooterClientOptions.get().musicVolume;}
  private void position(){x=scooter!=null?scooter.getX():block.getX()+.5;y=scooter!=null?scooter.getY()+.7:block.getY()+.5;z=scooter!=null?scooter.getZ():block.getZ()+.5;}
  void cancel(){stop();}
  @Override public void tick(){if(isStopped()||sounds.get(source)!=this){stop();return;}if(Minecraft.getInstance().level!=level||(scooter!=null?scooter.isRemoved():!(level.getBlockEntity(block) instanceof net.minecraft.world.level.block.entity.JukeboxBlockEntity))){stop();return;}if(++age>=duration){stop();boolean again=loopOverride!=null?loopOverride:scooter!=null&&ScooterMusic.looping(scooter);if(pcm!=null&&again){var next=new Moving(source,scooter,level,block,session,pcm,loopOverride);sounds.put(source,next);Minecraft.getInstance().getSoundManager().queueTickingSound(next);}return;}position();volume=ScooterClientOptions.get().audioEnabled?ScooterClientOptions.get().musicVolume:0;}
  @Override public CompletableFuture<AudioStream> getStream(net.minecraft.client.sounds.SoundBufferLibrary buffers,net.minecraft.client.resources.sounds.Sound sound,boolean looping){return pcm==null?super.getStream(buffers,sound,looping):CompletableFuture.completedFuture(new PcmStream(pcm));}
 }
 static final class PcmStream implements AudioStream {private final ScooterWav.Pcm pcm;private int offset;PcmStream(ScooterWav.Pcm pcm){this.pcm=pcm;}public javax.sound.sampled.AudioFormat getFormat(){return pcm.format();}public java.nio.ByteBuffer read(int size){int length=Math.min(size,pcm.samples().length-offset);var buffer=java.nio.ByteBuffer.allocateDirect(length);buffer.put(pcm.samples(),offset,length).flip();offset+=length;return buffer;}public void close(){offset=pcm.samples().length;}}
}
