package pl.aridlin.psychiatrykroles;
import java.util.UUID;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.network.PacketDistributor;

@EventBusSubscriber(modid="psychiatryk_roles",bus=EventBusSubscriber.Bus.MOD)
public final class RestartVoteUI {
 static RestartManager manager;
 public record State(UUID id,String duration,int yes,int no,int required,int total,int remaining,boolean active,boolean open) implements CustomPacketPayload {
  public static final Type<State> TYPE=new Type<>(ResourceLocation.fromNamespaceAndPath("psychiatryk_roles","restart_vote_state"));
  public static final StreamCodec<RegistryFriendlyByteBuf,State> CODEC=StreamCodec.of((b,s)->{b.writeUUID(s.id);b.writeUtf(s.duration,32);b.writeVarInt(s.yes);b.writeVarInt(s.no);b.writeVarInt(s.required);b.writeVarInt(s.total);b.writeVarInt(s.remaining);b.writeBoolean(s.active);b.writeBoolean(s.open);},b->new State(b.readUUID(),b.readUtf(32),b.readVarInt(),b.readVarInt(),b.readVarInt(),b.readVarInt(),b.readVarInt(),b.readBoolean(),b.readBoolean()));
  public Type<? extends CustomPacketPayload> type(){return TYPE;}
 }
 public record Choice(UUID id,boolean yes) implements CustomPacketPayload {
  public static final Type<Choice> TYPE=new Type<>(ResourceLocation.fromNamespaceAndPath("psychiatryk_roles","restart_vote_choice"));
  public static final StreamCodec<RegistryFriendlyByteBuf,Choice> CODEC=StreamCodec.of((b,s)->{b.writeUUID(s.id);b.writeBoolean(s.yes);},b->new Choice(b.readUUID(),b.readBoolean()));
  public Type<? extends CustomPacketPayload> type(){return TYPE;}
 }
 @SubscribeEvent public static void network(net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent e){
  var r=e.registrar("1");
  r.playToClient(State.TYPE,State.CODEC,(s,c)->c.enqueueWork(()->RestartVoteScreen.receive(s)));
  r.playToServer(Choice.TYPE,Choice.CODEC,(s,c)->c.enqueueWork(()->{if(manager!=null&&c.player() instanceof ServerPlayer p)manager.castGUI(p,s);}));
 }
 static void send(ServerPlayer player,RestartVote vote,boolean open,boolean active){
  PacketDistributor.sendToPlayer(player,new State(vote.id,vote.duration,vote.yes.size(),vote.no.size(),vote.required(),vote.eligible.size(),(int)Math.max(0,(vote.deadline-System.nanoTime()+999_999_999L)/1_000_000_000L),active,open));
 }
}
