package pl.aridlin.kukirin;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.*;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.component.CustomData;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;
@EventBusSubscriber(modid="goplanska_kukirin")
public final class ScooterMusic {
 public static final int MAX_BYTES=16*1024*1024;
 static final class Source {
  final UUID id;final Scooter scooter;final net.minecraft.server.level.ServerLevel level;final net.minecraft.core.BlockPos block;boolean blockLoop;
  Source(Scooter s){id=s.getUUID();scooter=s;level=(net.minecraft.server.level.ServerLevel)s.level();block=null;}
  Source(net.minecraft.server.level.ServerLevel l,net.minecraft.core.BlockPos p){level=l;block=p.immutable();scooter=null;id=UUID.nameUUIDFromBytes(("goplanska-jukebox:"+l.dimension().location()+":"+p.asLong()).getBytes(java.nio.charset.StandardCharsets.UTF_8));}
  boolean valid(){return scooter!=null?!scooter.isRemoved():level.hasChunkAt(block)&&level.getBlockEntity(block) instanceof net.minecraft.world.level.block.entity.JukeboxBlockEntity;}
  net.minecraft.core.BlockPos position(){return scooter!=null?scooter.blockPosition():block;}
  double distance(ServerPlayer p){return scooter!=null?p.distanceToSqr(scooter):p.distanceToSqr(block.getX()+.5,block.getY()+.5,block.getZ()+.5);}
  boolean looping(){return scooter!=null?ScooterMusic.looping(scooter):blockLoop;}
 }
 private record Playback(Source source,UUID session,byte[] data,ScooterMusicSources.Song song,long until,Map<UUID,Integer> sent){}
 private static final Map<UUID,Playback> playing=new HashMap<>();
 private static final Map<UUID,UUID> loading=new HashMap<>();
 private static final Map<UUID,Source> jukeboxes=new HashMap<>();
 public static boolean looping(Scooter s){return s.getItemBySlot(EquipmentSlot.FEET).getOrDefault(DataComponents.CUSTOM_DATA,CustomData.EMPTY).getUnsafe().getBoolean("ScooterMusicLoop");}
 public static void looping(Scooter s,boolean enabled){var item=s.getItemBySlot(EquipmentSlot.FEET).copy();CustomData.update(DataComponents.CUSTOM_DATA,item,t->t.putBoolean("ScooterMusicLoop",enabled));s.setItemSlot(EquipmentSlot.FEET,item);updateLoop(s.getUUID());}
 public static boolean permitted(Scooter s,ServerPlayer p){return s!=null&&(!ScooterEnchants.bound(s.getItemBySlot(EquipmentSlot.FEET))||ScooterEnchants.owned(s,p));}
 public static boolean insertDisc(Scooter s,ServerPlayer p,net.minecraft.world.InteractionHand hand){var disc=p.getItemInHand(hand);if(!disc.has(DataComponents.JUKEBOX_PLAYABLE)||!ScooterUpgradeRecipe.has(s.getItemBySlot(EquipmentSlot.FEET),"jukebox"))return false;stop(s);var item=s.getItemBySlot(EquipmentSlot.FEET).copy();var old=disc(item,s);if(!old.isEmpty())p.getInventory().placeItemBackInInventory(old);var copy=disc.copyWithCount(1);CustomData.update(DataComponents.CUSTOM_DATA,item,t->t.put("ScooterDisc",copy.save(s.registryAccess())));s.setItemSlot(EquipmentSlot.FEET,item);if(!p.isCreative())disc.shrink(1);s.startDisc();return true;}
 public static ItemStack disc(ItemStack item,Scooter s){var data=item.get(DataComponents.CUSTOM_DATA);return data==null?ItemStack.EMPTY:ItemStack.parseOptional(s.registryAccess(),data.copyTag().getCompound("ScooterDisc"));}
 public static void stop(Scooter s){stop(s.getUUID());s.stopDisc();}
 static void stop(UUID id){loading.remove(id);Playback old=playing.remove(id);if(old!=null)for(var p:old.source.level.getServer().getPlayerList().getPlayers())if(old.sent.containsKey(p.getUUID()))PacketDistributor.sendToPlayer(p,new ScooterAudioPacket(id,old.session,0,0,new byte[0]));}
 public static List<String> songs(){var names=new TreeSet<String>(ScooterMusicSources.catalog().keySet());names.add("chiki_ride.wav");names.add("night_motor.wav");return names.stream().limit(1024).toList();}
 public static int wav(ServerPlayer p,String name){var s=ScooterStorage.target(p);if(!permitted(s,p)||!ScooterUpgradeRecipe.has(s.getItemBySlot(EquipmentSlot.FEET),"noteblock")){p.displayClientMessage(Component.literal("Ride or aim at your scooter upgraded with a note block."),false);return 0;}s.stopDisc();return play(p,new Source(s),name);}
 static int play(ServerPlayer p,Source source,String name){
  if(!ScooterMusicSources.validName(name)){p.displayClientMessage(Component.literal("Choose a WAV from the server song list."),false);return 0;}
  if(loading.containsKey(source.id))return 0;
  if(playing.size()+loading.size()>=4&&!playing.containsKey(source.id)){p.displayClientMessage(Component.literal("Four music sources are already active."),false);return 0;}
  stop(source.id);ScooterMusicSources.Song song=ScooterMusicSources.catalog().get(name);
  if(song!=null){playing.put(source.id,new Playback(source,UUID.randomUUID(),null,song,source.level.getGameTime()+song.durationTicks()+20*30,new HashMap<>()));p.displayClientMessage(Component.literal("Playing "+song.title()+"."),false);return 1;}
  if(!name.equals("chiki_ride.wav")&&!name.equals("night_motor.wav")){p.displayClientMessage(Component.literal("Song unavailable; choose one from the server song list."),false);return 0;}
  UUID token=UUID.randomUUID();loading.put(source.id,token);var server=p.getServer();
  CompletableFuture.supplyAsync(()->{try{byte[] bytes;try(var bundled=ScooterMusic.class.getResourceAsStream("/data/goplanska_kukirin/music/"+name)){if(bundled==null)throw new java.io.IOException();bytes=bundled.readNBytes(MAX_BYTES+1);}if(bytes.length>MAX_BYTES||bytes.length<44||!new String(bytes,0,4,java.nio.charset.StandardCharsets.US_ASCII).equals("RIFF")||!new String(bytes,8,4,java.nio.charset.StandardCharsets.US_ASCII).equals("WAVE"))throw new java.io.IOException();return bytes;}catch(Exception ex){throw new java.util.concurrent.CompletionException(ex);}}).whenComplete((bytes,error)->server.execute(()->{if(!token.equals(loading.get(source.id)))return;loading.remove(source.id);if(!source.valid())return;if(error!=null){p.displayClientMessage(Component.literal("Bundled WAV could not be loaded."),false);return;}try{String hash=java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(bytes));var pcm=ScooterWav.decode(bytes);int ticks=(int)Math.ceil(pcm.samples().length/pcm.format().getFrameRate()/pcm.format().getFrameSize()*20);var bundled=new ScooterMusicSources.Song(name,name,"bundled:"+name,"",hash,bytes.length,ticks);playing.put(source.id,new Playback(source,UUID.randomUUID(),null,bundled,source.level.getGameTime()+ticks+20*30,new HashMap<>()));}catch(Exception ex){p.displayClientMessage(Component.literal("Bundled WAV could not be decoded."),false);return;}p.displayClientMessage(Component.literal("Playing "+name+"."),false);}));return 1;
 }
 private static ScooterAudioUrlPacket metadata(Playback p){var source=p.source;var song=p.song;return new ScooterAudioUrlPacket(source.id,p.session,source.block!=null,source.position(),song.url(),song.bearer(),song.sha256(),song.bytes(),source.looping());}
 private static void updateLoop(UUID id){Playback old=playing.get(id);if(old!=null&&old.song!=null)for(var p:old.source.level.getServer().getPlayerList().getPlayers())if(old.sent.containsKey(p.getUUID()))PacketDistributor.sendToPlayer(p,metadata(old));}
 static Source openJukebox(ServerPlayer p,net.minecraft.core.BlockPos pos){Source fresh=new Source(p.serverLevel(),pos);Source source=jukeboxes.get(fresh.id);if(source!=null)return source;if(jukeboxes.size()<256)jukeboxes.put(fresh.id,fresh);return fresh;}
 static boolean jukeboxLoop(Source source,boolean enabled){if(!source.valid())return false;source.blockLoop=enabled;updateLoop(source.id);return true;}
 @SubscribeEvent public static void start(net.neoforged.neoforge.event.server.ServerStartedEvent e){ScooterMusicSources.catalog();}
 @SubscribeEvent public static void commands(RegisterCommandsEvent e){e.getDispatcher().register(Commands.literal("scootermusic").then(Commands.literal("list").executes(c->{var list=songs();c.getSource().sendSuccess(()->Component.literal(list.size()+" songs. Open the scooter Music menu or shift-right-click a jukebox; use /scootermusic <name> with tab completion."),false);return 1;})).then(Commands.literal("loop").then(Commands.argument("enabled",com.mojang.brigadier.arguments.BoolArgumentType.bool()).executes(c->{var p=c.getSource().getPlayerOrException();var s=ScooterStorage.target(p);if(!permitted(s,p))return 0;looping(s,com.mojang.brigadier.arguments.BoolArgumentType.getBool(c,"enabled"));return 1;}))).then(Commands.literal("stop").executes(c->{var p=c.getSource().getPlayerOrException();var s=ScooterStorage.target(p);if(!permitted(s,p))return 0;stop(s);return 1;})).then(Commands.literal("disc").executes(c->{var p=c.getSource().getPlayerOrException();var s=ScooterStorage.target(p);if(!permitted(s,p)||!ScooterUpgradeRecipe.has(s.getItemBySlot(EquipmentSlot.FEET),"jukebox"))return 0;stop(s);s.startDisc();return 1;})).then(Commands.argument("wav",com.mojang.brigadier.arguments.StringArgumentType.greedyString()).suggests((c,b)->net.minecraft.commands.SharedSuggestionProvider.suggest(songs(),b)).executes(c->wav(c.getSource().getPlayerOrException(),com.mojang.brigadier.arguments.StringArgumentType.getString(c,"wav")))));}
 @SubscribeEvent public static void tick(ServerTickEvent.Post e){
  for(var playback:new ArrayList<>(playing.values())){
   var source=playback.source;if(!source.valid()||(!source.looping()&&source.level.getGameTime()>playback.until)){stop(source.id);continue;}
   for(var p:source.level.getServer().getPlayerList().getPlayers()){
    if(p.level()!=source.level||source.distance(p)>64*64){if(playback.sent.remove(p.getUUID())!=null)PacketDistributor.sendToPlayer(p,new ScooterAudioPacket(source.id,playback.session,0,0,new byte[0]));continue;}
    if(playback.song!=null){if(!playback.sent.containsKey(p.getUUID())){PacketDistributor.sendToPlayer(p,metadata(playback));playback.sent.put(p.getUUID(),playback.song.bytes());}continue;}
    int offset=playback.sent.getOrDefault(p.getUUID(),0);for(int n=0;n<2&&offset<playback.data.length;n++){int end=Math.min(offset+32768,playback.data.length);PacketDistributor.sendToPlayer(p,new ScooterAudioPacket(source.id,playback.session,playback.data.length,offset,Arrays.copyOfRange(playback.data,offset,end)));offset=end;}playback.sent.put(p.getUUID(),offset);
   }
  }
  jukeboxes.entrySet().removeIf(entry->!entry.getValue().valid());
 }
 @SubscribeEvent public static void shutdown(net.neoforged.neoforge.event.server.ServerStoppedEvent e){playing.clear();loading.clear();jukeboxes.clear();ScooterJukeboxMusic.clear();}
}
