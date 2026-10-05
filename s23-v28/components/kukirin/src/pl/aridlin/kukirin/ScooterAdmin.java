package pl.aridlin.kukirin;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.network.PacketDistributor;
@EventBusSubscriber(modid="goplanska_kukirin")
public final class ScooterAdmin {
 static void write(RegistryFriendlyByteBuf b,ScooterTuning.Values v){b.writeDouble(v.steering());b.writeDouble(v.normalGrip());b.writeDouble(v.driftGrip());b.writeDouble(v.recovery());b.writeDouble(v.tyreVolume());}
 static ScooterTuning.Values read(RegistryFriendlyByteBuf b){return new ScooterTuning.Values(b.readDouble(),b.readDouble(),b.readDouble(),b.readDouble(),b.readDouble());}
 public record Edit(boolean save,ScooterTuning.Values values) implements CustomPacketPayload{
  public static final Type<Edit> TYPE=new Type<>(ResourceLocation.parse("goplanska_kukirin:admin_tuning_edit"));
  public static final StreamCodec<RegistryFriendlyByteBuf,Edit> CODEC=StreamCodec.of((b,p)->{b.writeBoolean(p.save);write(b,p.values);},b->new Edit(b.readBoolean(),read(b)));
  public Type<Edit> type(){return TYPE;}
 }
 public record State(boolean open,ScooterTuning.Values values) implements CustomPacketPayload{
  public static final Type<State> TYPE=new Type<>(ResourceLocation.parse("goplanska_kukirin:handling_state"));
  public static final StreamCodec<RegistryFriendlyByteBuf,State> CODEC=StreamCodec.of((b,p)->{b.writeBoolean(p.open);write(b,p.values);},b->new State(b.readBoolean(),read(b)));
  public Type<State> type(){return TYPE;}
 }
 public static void register(net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent e){var r=e.registrar("1");r.playToServer(Edit.TYPE,Edit.CODEC,(p,c)->c.enqueueWork(()->{
  if(!(c.player() instanceof ServerPlayer player)||!player.hasPermissions(2))return;
  if(p.save){if(!p.values.valid()){player.displayClientMessage(net.minecraft.network.chat.Component.literal("Invalid tuning values; unchanged."),false);return;}try{ScooterTuning.save(p.values);broadcast(player.server);}catch(Exception ex){player.displayClientMessage(net.minecraft.network.chat.Component.literal("Could not save scooter settings."),false);return;}player.displayClientMessage(net.minecraft.network.chat.Component.literal("Scooter settings saved and applied live."),false);}
  else PacketDistributor.sendToPlayer(player,new State(true,ScooterTuning.server()));
 }));r.playToClient(State.TYPE,State.CODEC,(p,c)->c.enqueueWork(()->{ScooterTuning.receive(p.values);if(p.open)ScooterAdminScreen.open(p.values);}));}
 @SubscribeEvent public static void command(net.neoforged.neoforge.event.RegisterCommandsEvent e){e.getDispatcher().register(net.minecraft.commands.Commands.literal("scooteradmin").requires(s->s.hasPermission(2)).executes(c->{PacketDistributor.sendToPlayer(c.getSource().getPlayerOrException(),new State(true,ScooterTuning.server()));return 1;}));}
 @SubscribeEvent public static void login(net.neoforged.neoforge.event.entity.player.PlayerEvent.PlayerLoggedInEvent e){if(e.getEntity() instanceof ServerPlayer p)PacketDistributor.sendToPlayer(p,new State(false,ScooterTuning.server()));}
 private static ScooterTuning.Values sent;private static long nextSync;
 @SubscribeEvent public static void tick(net.neoforged.neoforge.event.tick.ServerTickEvent.Post e){long now=System.currentTimeMillis();if(now<nextSync)return;nextSync=now+1000;var v=ScooterTuning.server();if(!v.equals(sent))broadcast(e.getServer());}
 private static void broadcast(net.minecraft.server.MinecraftServer s){sent=ScooterTuning.server();for(var p:s.getPlayerList().getPlayers())PacketDistributor.sendToPlayer(p,new State(false,sent));}
}
