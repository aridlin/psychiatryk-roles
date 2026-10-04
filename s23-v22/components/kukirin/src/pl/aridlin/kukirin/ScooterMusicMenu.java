package pl.aridlin.kukirin;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
public final class ScooterMusicMenu {
 public record Request(String action,String song) implements CustomPacketPayload {
  public static final Type<Request> TYPE=new Type<>(ResourceLocation.parse("goplanska_kukirin:music_menu"));
  public static final StreamCodec<RegistryFriendlyByteBuf,Request> CODEC=new StreamCodec<>(){public Request decode(RegistryFriendlyByteBuf b){return new Request(b.readUtf(16),b.readUtf(256));}public void encode(RegistryFriendlyByteBuf b,Request p){b.writeUtf(p.action,16);b.writeUtf(p.song,256);}};public Type<Request> type(){return TYPE;}
 }
 public record Catalog(java.util.List<String> songs,boolean wav,boolean disc) implements CustomPacketPayload {
  public static final Type<Catalog> TYPE=new Type<>(ResourceLocation.parse("goplanska_kukirin:music_catalog"));
  public static final StreamCodec<RegistryFriendlyByteBuf,Catalog> CODEC=new StreamCodec<>(){public Catalog decode(RegistryFriendlyByteBuf b){int n=b.readVarInt();if(n<0||n>1024)throw new IllegalArgumentException("Song catalog limit");var list=new java.util.ArrayList<String>();for(int i=0;i<n;i++)list.add(b.readUtf(256));return new Catalog(java.util.List.copyOf(list),b.readBoolean(),b.readBoolean());}public void encode(RegistryFriendlyByteBuf b,Catalog p){b.writeVarInt(p.songs.size());for(var s:p.songs)b.writeUtf(s,256);b.writeBoolean(p.wav);b.writeBoolean(p.disc);}};public Type<Catalog> type(){return TYPE;}
 }
 public static void register(net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent e){var r=e.registrar("1");r.playToServer(Request.TYPE,Request.CODEC,(data,ctx)->ctx.enqueueWork(()->{if(!(ctx.player() instanceof net.minecraft.server.level.ServerPlayer p))return;var scooter=ScooterStorage.target(p);if(!ScooterMusic.permitted(scooter,p))return;var item=scooter.getItemBySlot(net.minecraft.world.entity.EquipmentSlot.FEET);switch(data.action){case "open"->net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(p,new Catalog(ScooterMusic.songs().stream().filter(s->s.length()<=256).limit(1024).toList(),ScooterUpgradeRecipe.has(item,"noteblock"),ScooterUpgradeRecipe.has(item,"jukebox")));case "play"->ScooterMusic.wav(p,data.song);case "stop"->ScooterMusic.stop(scooter);case "disc"->{if(ScooterUpgradeRecipe.has(item,"jukebox")){ScooterMusic.stop(scooter);scooter.startDisc();}}default->{}}}));r.playToClient(Catalog.TYPE,Catalog.CODEC,(data,ctx)->ctx.enqueueWork(()->ScooterMusicClient.receive(data)));}
 private ScooterMusicMenu(){}
}
