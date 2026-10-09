package pl.aridlin.psychiatrykroles.runtime;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import java.util.function.Consumer;

public final class RuntimeNetwork {
    public static Consumer<String> receiver=ignored->{};
    public record Snapshot(String json) implements CustomPacketPayload {
        public static final Type<Snapshot> TYPE=new Type<>(ResourceLocation.parse("psychiatryk_roles:runtime_snapshot"));
        public static final StreamCodec<RegistryFriendlyByteBuf,Snapshot> CODEC=StreamCodec.of((b,p)->b.writeUtf(p.json,Schema.MAX_DOCUMENT),b->new Snapshot(b.readUtf(Schema.MAX_DOCUMENT)));
        public Type<Snapshot> type(){return TYPE;}
    }
    public record Action(String json) implements CustomPacketPayload {
        public static final Type<Action> TYPE=new Type<>(ResourceLocation.parse("psychiatryk_roles:runtime_action"));
        public static final StreamCodec<RegistryFriendlyByteBuf,Action> CODEC=StreamCodec.of((b,p)->b.writeUtf(p.json,Schema.MAX_ACTION),b->new Action(b.readUtf(Schema.MAX_ACTION)));
        public Type<Action> type(){return TYPE;}
    }
    /** New clients opt in; old 3.0.10 clients have only Snapshot and Action. */
    public record Capabilities(String json) implements CustomPacketPayload {
        public static final Type<Capabilities> TYPE=new Type<>(ResourceLocation.parse("psychiatryk_roles:runtime_capabilities"));
        public static final StreamCodec<RegistryFriendlyByteBuf,Capabilities> CODEC=StreamCodec.of(
            (b,p)->b.writeUtf(p.json,SignalSchema.MAX_HELLO),b->new Capabilities(b.readUtf(SignalSchema.MAX_HELLO)));
        public Type<Capabilities> type(){return TYPE;}
    }
    public static void register(RegisterPayloadHandlersEvent e){
        var r=e.registrar("runtime-envelope-1").optional();
        r.playToClient(Snapshot.TYPE,Snapshot.CODEC,(p,c)->c.enqueueWork(()->receiver.accept(p.json)));
        r.playToServer(Action.TYPE,Action.CODEC,(p,c)->c.enqueueWork(()->{if(c.player() instanceof ServerPlayer player)RuntimeServer.action(player,p.json);}));
        var capabilities=e.registrar("runtime-capabilities-1").optional();
        capabilities.playToServer(Capabilities.TYPE,Capabilities.CODEC,(p,c)->c.enqueueWork(()->{if(c.player() instanceof ServerPlayer player)SignalServer.handshake(player,p.json);}));
    }
    public static boolean supported(ServerPlayer p){
        try{return p.connection.hasChannel(Snapshot.TYPE)&&p.connection.hasChannel(Action.TYPE);}
        catch(NullPointerException disconnected){return false;}
    }
    public static boolean signalsSupported(ServerPlayer p){
        try{return p.connection.hasChannel(Capabilities.TYPE);}catch(NullPointerException disconnected){return false;}
    }
    public static void send(ServerPlayer p,Schema.View view){if(supported(p))PacketDistributor.sendToPlayer(p,new Snapshot(Schema.JSON.toJson(view)));}
}
