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
 public static void receive(ScooterAudioPacket p){
  if(p.offset()==0)org.slf4j.LoggerFactory.getLogger("ScooterAudio").info("Audio transfer {} total={}",p.session(),p.total());if(p.total()==0){var t=transfers.get(p.scooter());if(t!=null&&t.session.equals(p.session()))transfers.remove(p.scooter());if(p.session().equals(decoding.get(p.scooter())))decoding.remove(p.scooter());var sound=sounds.get(p.scooter());if(sound!=null&&p.session().equals(sound.session))remove(p.scooter());return;}
  if(p.total()<44||p.total()>ScooterMusic.MAX_BYTES||p.offset()<0||p.bytes().length==0||p.bytes().length>32768||(long)p.offset()+p.bytes().length>p.total())return;
  var t=transfers.get(p.scooter());if(p.offset()==0){if(transfers.size()+decoding.size()>=4&&!transfers.containsKey(p.scooter()))return;t=new Transfer(p);transfers.put(p.scooter(),t);}if(t==null||!t.session.equals(p.session())||t.offset!=p.offset()||t.bytes.length!=p.total())return;System.arraycopy(p.bytes(),0,t.bytes,t.offset,p.bytes().length);t.offset+=p.bytes().length;t.touched=System.nanoTime();if(t.offset==t.bytes.length){org.slf4j.LoggerFactory.getLogger("ScooterAudio").info("Audio transfer complete {}",p.session());transfers.remove(p.scooter());decoding.put(p.scooter(),p.session());byte[] wav=t.bytes;CompletableFuture.supplyAsync(()->{try{return ScooterWav.decode(wav);}catch(Exception ex){throw new java.util.concurrent.CompletionException(ex);}}).whenComplete((pcm,error)->Minecraft.getInstance().execute(()->{if(!p.session().equals(decoding.get(p.scooter())))return;decoding.remove(p.scooter());if(error!=null){org.slf4j.LoggerFactory.getLogger("ScooterAudio").error("WAV decoding failed",error);var player=Minecraft.getInstance().player;if(player!=null)player.displayClientMessage(net.minecraft.network.chat.Component.literal("Scooter WAV could not be decoded: "+error.getCause().getMessage()),false);return;}var s=find(p.scooter());if(s!=null){org.slf4j.LoggerFactory.getLogger("ScooterAudio").info("Playing decoded mono WAV frames={} entity={}",pcm.samples().length/2,s.getId());remove(p.scooter());var sound=new Moving(s,p.session(),pcm);sounds.put(p.scooter(),sound);Minecraft.getInstance().getSoundManager().play(sound);}}));}
 }
 private static Scooter find(UUID id){var level=Minecraft.getInstance().level;if(level!=null)for(var e:level.entitiesForRendering())if(e instanceof Scooter s&&s.getUUID().equals(id))return s;return null;}
 private static void remove(UUID id){var old=sounds.remove(id);if(old!=null)Minecraft.getInstance().getSoundManager().stop(old);}
 @SubscribeEvent public static void tick(net.neoforged.neoforge.client.event.ClientTickEvent.Post e){var mc=Minecraft.getInstance();if(mc.level==null){for(var id:new ArrayList<>(sounds.keySet()))remove(id);transfers.clear();decoding.clear();discs.clear();return;}transfers.entrySet().removeIf(entry->System.nanoTime()-entry.getValue().touched>30_000_000_000L);sounds.entrySet().removeIf(entry->entry.getValue().isStopped());for(var entity:mc.level.entitiesForRendering())if(entity instanceof Scooter s){int serial=s.discSerial();if(discs.getOrDefault(s.getUUID(),0)!=serial){discs.put(s.getUUID(),serial);remove(s.getUUID());if(s.discPlaying()){var stack=ScooterMusic.disc(s.getItemBySlot(net.minecraft.world.entity.EquipmentSlot.FEET),s);var playable=stack.get(net.minecraft.core.component.DataComponents.JUKEBOX_PLAYABLE);if(playable!=null)playable.song().unwrap(s.registryAccess()).ifPresent(holder->{var sound=new Moving(s,holder.value().soundEvent().value(),holder.value().lengthInTicks());sounds.put(s.getUUID(),sound);mc.getSoundManager().play(sound);});}}}}
 /** BLOCKS intentionally: Sound Physics can exclude RECORDS when moving-sound processing is disabled. */
 public static final class Moving extends AbstractTickableSoundInstance {
  final Scooter scooter;final UUID session;final ScooterWav.Pcm pcm;final int duration;int age;
  Moving(Scooter s,UUID session,ScooterWav.Pcm pcm){super(Kukirin.WAV.get(),SoundSource.BLOCKS,net.minecraft.util.RandomSource.create());scooter=s;this.session=session;this.pcm=pcm;duration=(int)Math.ceil(pcm.samples().length/pcm.format().getFrameRate()/pcm.format().getFrameSize()*20);relative=false;looping=false;position();volume=ScooterClientOptions.get().musicVolume;}
  Moving(Scooter s,net.minecraft.sounds.SoundEvent event,int duration){super(event,SoundSource.BLOCKS,net.minecraft.util.RandomSource.create());scooter=s;session=null;pcm=null;this.duration=duration;relative=false;looping=false;position();volume=ScooterClientOptions.get().musicVolume;}
  private void position(){x=scooter.getX();y=scooter.getY()+.7;z=scooter.getZ();}
  @Override public void tick(){if(scooter.isRemoved()||Minecraft.getInstance().level!=scooter.level()){stop();return;}if(++age>=duration){stop();if(pcm!=null&&ScooterMusic.looping(scooter)){var next=new Moving(scooter,session,pcm);sounds.put(scooter.getUUID(),next);// SoundEngine is iterating tickingSounds here. Queue the next loop for its next tick.
Minecraft.getInstance().getSoundManager().queueTickingSound(next);}return;}position();volume=ScooterClientOptions.get().audioEnabled?ScooterClientOptions.get().musicVolume:0;}
  @Override public CompletableFuture<AudioStream> getStream(net.minecraft.client.sounds.SoundBufferLibrary buffers,net.minecraft.client.resources.sounds.Sound sound,boolean looping){return pcm==null?super.getStream(buffers,sound,looping):CompletableFuture.completedFuture(new PcmStream(pcm));}
 }
 static final class PcmStream implements AudioStream {private final ScooterWav.Pcm pcm;private int offset;PcmStream(ScooterWav.Pcm pcm){this.pcm=pcm;}public javax.sound.sampled.AudioFormat getFormat(){return pcm.format();}public java.nio.ByteBuffer read(int size){int length=Math.min(size,pcm.samples().length-offset);var buffer=java.nio.ByteBuffer.allocateDirect(length);buffer.put(pcm.samples(),offset,length).flip();offset+=length;return buffer;}public void close(){offset=pcm.samples().length;}}
}
