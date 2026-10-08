package pl.aridlin.kukirin;

import java.io.IOException;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.network.chat.Component;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.network.PacketDistributor;

@EventBusSubscriber(modid="goplanska_kukirin")
public final class ScooterAdmin {
    static void write(RegistryFriendlyByteBuf buf, ScooterTuning.Values values) {
        buf.writeDouble(values.steering()); buf.writeDouble(values.normalGrip());
        buf.writeDouble(values.driftGrip()); buf.writeDouble(values.recovery());
        buf.writeDouble(values.tyreVolume()); buf.writeDouble(values.minimumTurnRate());
    }
    static ScooterTuning.Values read(RegistryFriendlyByteBuf buf) {
        return new ScooterTuning.Values(buf.readDouble(),buf.readDouble(),buf.readDouble(),
                buf.readDouble(),buf.readDouble(),buf.readDouble());
    }
    public record Edit(boolean save, ScooterTuning.Values values) implements CustomPacketPayload {
        public static final Type<Edit> TYPE = new Type<>(ResourceLocation.parse("goplanska_kukirin:admin_tuning_edit"));
        public static final StreamCodec<RegistryFriendlyByteBuf,Edit> CODEC = StreamCodec.of(
                (buf,packet)->{buf.writeBoolean(packet.save);write(buf,packet.values);},
                buf->new Edit(buf.readBoolean(),read(buf)));
        public Type<Edit> type() { return TYPE; }
    }
    public record State(boolean open, ScooterTuning.Values values) implements CustomPacketPayload {
        public static final Type<State> TYPE = new Type<>(ResourceLocation.parse("goplanska_kukirin:handling_state"));
        public static final StreamCodec<RegistryFriendlyByteBuf,State> CODEC = StreamCodec.of(
                (buf,packet)->{buf.writeBoolean(packet.open);write(buf,packet.values);},
                buf->new State(buf.readBoolean(),read(buf)));
        public Type<State> type() { return TYPE; }
    }
    /** The same permission guard covers files and the outgoing authoritative snapshot. */
    static boolean applyEdit(boolean authorized, ScooterTuning.Values values, Runnable publish) throws IOException {
        if (!authorized || values == null || !values.valid()) return false;
        ScooterTuning.save(values); publish.run(); return true;
    }
    public static void register(net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent event) {
        var registrar = event.registrar("2");
        registrar.playToServer(Edit.TYPE,Edit.CODEC,(packet,context)->context.enqueueWork(()->{
            if (!(context.player() instanceof ServerPlayer player) || !player.hasPermissions(2)) return;
            if (!packet.save) { PacketDistributor.sendToPlayer(player,new State(true,ScooterTuning.server())); return; }
            try {
                if (!applyEdit(player.hasPermissions(2),packet.values,()->broadcast(player.server))) {
                    player.displayClientMessage(Component.literal("Invalid tuning values; unchanged."),false); return;
                }
                player.displayClientMessage(Component.literal("Shared scooter settings saved; applied to everyone, including future joiners."),false);
            } catch (IOException ex) {
                player.displayClientMessage(Component.literal("Could not save shared scooter settings; unchanged."),false);
            }
        }));
        registrar.playToClient(State.TYPE,State.CODEC,(packet,context)->context.enqueueWork(()->{
            ScooterTuning.receive(packet.values);
            if (packet.open && packet.values.valid()) ScooterAdminScreen.open(packet.values);
        }));
    }
    @SubscribeEvent public static void command(net.neoforged.neoforge.event.RegisterCommandsEvent event) {
        event.getDispatcher().register(net.minecraft.commands.Commands.literal("scooteradmin")
                .requires(source->source.hasPermission(2)).executes(context->{
                    PacketDistributor.sendToPlayer(context.getSource().getPlayerOrException(),new State(true,ScooterTuning.server()));
                    return 1;
                }));
    }
    static void synchronize(ServerPlayer player) {
        PacketDistributor.sendToPlayer(player,new State(false,ScooterTuning.server()));
    }
    @SubscribeEvent public static void login(net.neoforged.neoforge.event.entity.player.PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) synchronize(player);
    }
    @SubscribeEvent public static void respawn(net.neoforged.neoforge.event.entity.player.PlayerEvent.PlayerRespawnEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) synchronize(player);
    }
    @SubscribeEvent public static void dimension(net.neoforged.neoforge.event.entity.player.PlayerEvent.PlayerChangedDimensionEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) synchronize(player);
    }
    private static ScooterTuning.Values sent;
    private static long nextSync;
    @SubscribeEvent public static void started(net.neoforged.neoforge.event.server.ServerStartedEvent event) {
        nextSync=0; sent=null; ScooterTuning.reload(); broadcast(event.getServer());
    }
    @SubscribeEvent public static void tick(net.neoforged.neoforge.event.tick.ServerTickEvent.Post event) {
        long now=System.currentTimeMillis(); if(now<nextSync)return; nextSync=now+1000;
        var values=ScooterTuning.server(); if(!values.equals(sent))broadcast(event.getServer());
    }
    static void sendToPlayers(Iterable<ServerPlayer> players, ScooterTuning.Values values) {
        for (ServerPlayer player: players) PacketDistributor.sendToPlayer(player,new State(false,values));
    }
    private static void broadcast(MinecraftServer server) {
        sent=ScooterTuning.server(); sendToPlayers(server.getPlayerList().getPlayers(),sent);
    }
}
