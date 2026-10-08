from pathlib import Path
r=Path(__file__).resolve().parent.parent
original=(r/'src/pl/aridlin/kukirin/ScooterMusic.java').read_text()
source=original[original.index('   static final class Source {'):]
source=source[:source.rfind('\n}')]
for a,b in {'net.minecraft.world.entity.item.ItemEntity':'ItemEntity','net.minecraft.world.entity.Entity.RemovalReason':'Entity.RemovalReason','net.minecraft.world.phys.Vec3':'Vec3','net.minecraft.world.phys.AABB':'AABB'}.items():source=source.replace(a,b)
header='''package pl.aridlin.kukirin;
import java.util.*;import java.nio.charset.StandardCharsets;import java.util.function.Predicate;
public class LifecycleCheck {
static int checks;static void check(boolean value,String why){checks++;if(!value)throw new AssertionError(why);}
static class Vec3 {final double x;Vec3(double x){this.x=x;}}
static class BlockPos {final double x;BlockPos(double x){this.x=x;}static BlockPos containing(Vec3 p){return new BlockPos(p.x);}BlockPos immutable(){return this;}long asLong(){return(long)x;}int getX(){return(int)x;}int getY(){return 0;}int getZ(){return 0;}}
static class AABB{static AABB ofSize(Vec3 p,int a,int b,int c){return new AABB();}}
static class Entity {enum RemovalReason{UNLOADED_TO_CHUNK,DISCARDED}RemovalReason reason;ServerLevel world;int network;Vec3 p=new Vec3(10);boolean isRemoved(){return reason!=null;}RemovalReason getRemovalReason(){return reason;}ServerLevel level(){return world;}Vec3 position(){return p;}BlockPos blockPosition(){return BlockPos.containing(p);}int getId(){return network;}}
static class ItemStack {static final ItemStack EMPTY=new ItemStack(null);final UUID identity;UUID token=UUID.randomUUID();boolean loop;ItemStack(UUID id){identity=id;}boolean isEmpty(){return identity==null;}}
static class ItemEntity extends Entity {ItemStack stack;ItemEntity(ServerLevel l,UUID id,int net){world=l;stack=new ItemStack(id);network=net;}ItemStack getItem(){return stack;}}
static class PlayerList {Map<UUID,ServerPlayer> players=new HashMap<>();ServerPlayer getPlayer(UUID id){return players.get(id);}List<ServerPlayer> getPlayers(){return new ArrayList<>(players.values());}}
static class Server {PlayerList list=new PlayerList();PlayerList getPlayerList(){return list;}}
static class Dimension {String location(){return "fixture";}}
static class JukeboxBlockEntity {}
static class ServerLevel {Server server;long time;boolean loaded=true;List<ItemEntity> items=new ArrayList<>();ServerLevel(Server s){server=s;}Server getServer(){return server;}long getGameTime(){return time;}boolean hasChunkAt(BlockPos p){return loaded;}Dimension dimension(){return new Dimension();}Object getBlockEntity(BlockPos p){return new JukeboxBlockEntity();}<T>List<T> getEntitiesOfClass(Class<T> klass,AABB box,Predicate<T> filter){return items.stream().map(klass::cast).filter(filter).toList();}}
static class ServerPlayer extends Entity {UUID uuid;ItemStack equipped=ItemStack.EMPTY;boolean dead;ServerPlayer(ServerLevel l,UUID id,int net){world=l;uuid=id;network=net;}UUID getUUID(){return uuid;}ServerLevel serverLevel(){return world;}double distanceToSqr(Entity entity){return Math.pow(p.x-entity.p.x,2);}double distanceToSqr(double x,double y,double z){return Math.pow(p.x-x,2);}}
static class Scooter extends Entity {UUID uuid=UUID.randomUUID();UUID getUUID(){return uuid;}}
static class WearableJukebox {static ItemStack equipped(ServerPlayer p){return p.equipped;}static UUID token(ItemStack s){return s.token;}static UUID musicIdentity(ItemStack s){return s.identity;}static boolean musicValid(ServerPlayer p,UUID token){return !p.isRemoved()&&p.world.server.list.getPlayer(p.uuid)==p&&!p.equipped.isEmpty()&&p.equipped.token.equals(token);}static boolean looping(ItemStack s){return s.loop;}}
static class ScooterMusic {static boolean looping(Scooter s){return false;}static boolean autoplay(Source s){return false;}}
'''
main='''
public static void main(String[]args){Server server=new Server();ServerLevel overworld=new ServerLevel(server),nether=new ServerLevel(server);UUID id=UUID.randomUUID(),item=UUID.randomUUID();ServerPlayer original=new ServerPlayer(overworld,id,1);original.equipped=new ItemStack(item);server.list.players.put(id,original);Source source=new Source(original);
check(source.valid(),"initial equipment valid");original.dead=true;check(source.valid(),"dead player audible until equipment drop");
original.equipped=ItemStack.EMPTY;source.continuityUntil=System.nanoTime()+60_000_000_000L;check(source.suspended(),"death handoff retained");ItemEntity drop=new ItemEntity(overworld,item,22);overworld.items.add(drop);check(source.refreshWearer(),"same physical jukebox drop discovered");check(source.valid(),"dropped item stays audible");check(source.entityId()==22,"source follows dropped item network id");check(source.position().x==drop.p.x,"source uses dropped position");
ServerPlayer respawn=new ServerPlayer(overworld,id,3);respawn.p=new Vec3(1000);server.list.players.put(id,respawn);check(!source.refreshWearer()&&source.valid(),"respawn without item keeps death drop");respawn.equipped=new ItemStack(item);check(source.refreshWearer()&&source.valid(),"same equipped item rebinds respawn object");check(source.entityId()==3&&source.dropped==null,"rebind drops stale entity reference");respawn.world=nether;check(source.refreshWearer()&&source.level==nether&&source.valid(),"dimension transition rebinds source level");check(source.wornToken.equals(respawn.equipped.token),"equip epoch updated");
respawn.equipped=new ItemStack(UUID.randomUUID());check(!source.valid(),"different jukebox cannot impersonate old source");respawn.equipped=ItemStack.EMPTY;source.continuityUntil=System.nanoTime()+60_000_000_000L;ItemEntity nextDrop=new ItemEntity(nether,item,23);nether.items.add(nextDrop);check(source.refreshWearer()&&source.entityId()==23,"drop follows new level");nextDrop.reason=Entity.RemovalReason.UNLOADED_TO_CHUNK;nether.loaded=false;source.continuityUntil=0;check(source.suspended()&&source.retained(),"unloaded physical drop retained without chunk force-load");check(!source.refreshWearer(),"unloaded chunk not searched");nether.loaded=true;nether.time=30;ItemEntity reloaded=new ItemEntity(nether,item,24);nether.items.clear();nether.items.add(reloaded);check(source.refreshWearer()&&source.valid()&&source.entityId()==24,"same item reloads after grace expires");
reloaded.reason=Entity.RemovalReason.DISCARDED;source.continuityUntil=0;check(!source.retained(),"destroyed item stops naturally");Source placed=new Source(overworld,new BlockPos(0));placed.blockLoop=true;overworld.loaded=false;check(placed.suspended(),"existing placed loop unloading retained");
System.out.println("{\\"success\\":true,\\"assertions\\":"+checks+",\\"source_implementation_extracted\\":true,\\"engine_doubles\\":true,\\"native_game_launched\\":false}");}}
'''

methods=original[original.index('   static boolean paused(Source source) {'):original.index('   public static int skip(ServerPlayer player, Source requested) {')]
extra='''
static Map<UUID,Playback> playing=new HashMap<>();static int optionsSent;static boolean lastPaused;
static boolean sameSource(Source a,Source b){return a.level==b.level&&a.scooter==b.scooter&&a.wearer==b.wearer&&Objects.equals(a.wornToken,b.wornToken)&&Objects.equals(a.block,b.block);}
static void options(ServerPlayer p,Source s){optionsSent++;lastPaused=paused(s);}
interface CustomPacketPayload{}
record Snapshot(boolean paused,long position)implements CustomPacketPayload{}
record ScooterAudioPacket(UUID source,UUID session,int total,int offset,byte[]data)implements CustomPacketPayload{}
static class PacketDistributor{static List<CustomPacketPayload>sent=new ArrayList<>();static void sendToPlayer(ServerPlayer p,CustomPacketPayload packet,CustomPacketPayload[]extra){sent.add(packet);}}
static class MusicImports{static Map<ServerPlayer,Source> selected=new HashMap<>();static Source selected(ServerPlayer p){return selected.get(p);}}
static class Song{int bytes(){return 999;}}
static class Playback{Source source;Song song=new Song();UUID session=UUID.randomUUID();MusicSessionClock clock;Map<UUID,Integer>sent=new HashMap<>();Playback(Source s,java.util.concurrent.atomic.AtomicLong now){source=s;clock=new MusicSessionClock(now::get);}}
static Snapshot metadata(Playback p){return new Snapshot(p.clock.paused(),p.clock.elapsedMillis());}
'''
main=main.replace('System.out.println(','''// Pause remains source-authorized and shared while retaining the session/options.
overwoldPlaceholder
System.out.println(''')
main=main.replace('overwoldPlaceholder','''overworld.loaded=true;server.list.players.clear();ServerPlayer controller=new ServerPlayer(overworld,UUID.randomUUID(),31),listener=new ServerPlayer(overworld,UUID.randomUUID(),32);server.list.players.put(controller.uuid,controller);server.list.players.put(listener.uuid,listener);Source speaker=new Source(overworld,new BlockPos(10));speaker.blockLoop=true;MusicImports.selected.put(controller,speaker);MusicImports.selected.put(listener,speaker);java.util.concurrent.atomic.AtomicLong now=new java.util.concurrent.atomic.AtomicLong();Playback track=new Playback(speaker,now);playing.put(speaker.id,track);now.set(12300000000L);UUID session=track.session;
check(pause(controller,speaker,true)==1&&track.clock.paused(),"authorized pause works");check(PacketDistributor.sent.stream().allMatch(p->p instanceof Snapshot snapshot&&snapshot.paused()),"every listener gets paused snapshot");check(optionsSent==2&&lastPaused,"all open source controls acknowledge pause");now.set(500000000000L);check(track.clock.elapsedMillis()==12300,"paused session never advances");check(speaker.blockLoop&&track.session.equals(session),"loop and session retained");Source another=new Source(overworld,new BlockPos(22));MusicImports.selected.put(controller,another);check(pause(controller,speaker,false)==0&&track.clock.paused(),"wrong selected source cannot resume");MusicImports.selected.put(controller,speaker);check(pause(controller,speaker,false)==1&&!track.clock.paused()&&track.clock.elapsedMillis()==12300,"authorized resume uses frozen playhead");check(track.session.equals(session),"resume keeps one synchronized session");playing.remove(speaker.id);check(pause(controller,speaker,true)==0&&!lastPaused,"stopped source responds without fabricated playback");playing.put(speaker.id,track);track.song=null;PacketDistributor.sent.clear();check(pause(controller,speaker,true)==1,"legacy byte path pause does not crash");check(PacketDistributor.sent.stream().allMatch(p->p instanceof ScooterAudioPacket packet&&packet.total()==0),"legacy path cancels outgoing audio while paused");check(track.sent.isEmpty(),"legacy path stops chunk delivery");''')

build=r/'build/lifecycle';build.mkdir(parents=True,exist_ok=True);(build/'LifecycleCheck.java').write_text(header+source+extra+methods+main)

# Extend the exact extracted server methods with persistent-data engine doubles.
path=build/'LifecycleCheck.java';text=path.read_text()
volume_methods=original[original.index('   private static int storedVolume(Source source) {'):original.index('   static String currentSong(Source source) {')]
text=text.replace('static class JukeboxBlockEntity {}','static class JukeboxBlockEntity {MapTag data=new MapTag();MapTag getPersistentData(){return data;}void setChanged(){}}')
text=text.replace('boolean loaded=true;List<ItemEntity>','boolean loaded=true;JukeboxBlockEntity jukebox=new JukeboxBlockEntity();List<ItemEntity>').replace('return new JukeboxBlockEntity();','return jukebox;')
text=text.replace('boolean isEmpty(){return identity==null;}', 'CustomData custom=new CustomData();CustomData getOrDefault(Object key,CustomData fallback){return custom;}boolean isEmpty(){return identity==null;}')
text=text.replace('UUID getUUID(){return uuid;}}\nstatic class WearableJukebox', 'ItemStack feet=new ItemStack(UUID.randomUUID());ItemStack getItemBySlot(EquipmentSlot slot){return feet;}UUID getUUID(){return uuid;}}\nstatic class WearableJukebox')
extras="""
static class MapTag{Map<String,Integer> values=new HashMap<>();boolean contains(String key){return values.containsKey(key);}int getInt(String key){return values.getOrDefault(key,0);}void putInt(String key,int value){values.put(key,value);}}
static class CustomData{static final CustomData EMPTY=new CustomData();MapTag tag=new MapTag();MapTag getUnsafe(){return tag;}static void update(Object key,ItemStack stack,java.util.function.Consumer<MapTag> edit){edit.accept(stack.custom.tag);}}
static class DataComponents{static final Object CUSTOM_DATA=new Object();}enum EquipmentSlot{FEET}
static Map<UUID,Modes> modeStates=new HashMap<>();static class Modes{Source source;int volume;Modes(Source source){this.source=source;volume=storedVolume(source);}}static Modes modes(Source source,boolean create){return modeStates.computeIfAbsent(source.id,id->new Modes(source));}
static void refreshTransportOptions(Source source){for(ServerPlayer listener:source.level.getServer().getPlayerList().getPlayers()){Source context=MusicImports.selected(listener);if(context!=null&&sameSource(source,context))options(listener,source);}}
"""
text=text.replace('public static void main(String[]args)',extras+volume_methods+'\npublic static void main(String[]args)')
extra_checks="""
track.song=new Song();check(setVolume(controller,speaker,37)==1&&volume(speaker)==37,"shared source gain is set");check(track.clock.paused()&&track.clock.elapsedMillis()==12300,"volume change retains paused clock");modeStates.clear();check(volume(speaker)==37,"placed gain survives source mode reinitialization via persistent NBT");check(setVolume(controller,speaker,-1)==0&&setVolume(controller,speaker,101)==0&&volume(speaker)==37,"out of range volume rejected");MusicImports.selected.put(controller,another);check(setVolume(controller,speaker,90)==0&&volume(speaker)==37,"wrong selected source cannot change gain");
controller.equipped=new ItemStack(UUID.randomUUID());Source worn=new Source(controller);MusicImports.selected.put(controller,worn);check(setVolume(controller,worn,17)==1,"wearable gain setter");modeStates.clear();check(volume(worn)==17,"wearable item gain persists after mode recreation");Scooter vehicle=new Scooter();vehicle.world=overworld;Source scooterSource=new Source(vehicle);MusicImports.selected.put(controller,scooterSource);check(setVolume(controller,scooterSource,66)==1,"scooter gain setter");modeStates.clear();check(volume(scooterSource)==66,"scooter item gain persists after mode recreation");
"""
text=text.replace('System.out.println(',extra_checks+'System.out.println(')
path.write_text(text)
