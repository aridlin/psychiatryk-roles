package pl.aridlin.psychiatrykroles.runtime;

import java.util.function.Consumer;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

/** Optional, frozen asset transfer envelope. Client handlers are installed only on Dist.CLIENT. */
public final class AssetNetwork {
    private AssetNetwork() {}
    public static volatile Consumer<String> clientManifest=ignored->{};
    public static volatile Consumer<Chunk> clientChunk=ignored->{};

    public record Manifest(String json) implements CustomPacketPayload {
        public static final Type<Manifest> TYPE=new Type<>(ResourceLocation.parse("psychiatryk_roles:asset_manifest"));
        public static final StreamCodec<RegistryFriendlyByteBuf,Manifest> CODEC=StreamCodec.of(
            (b,p)->b.writeUtf(p.json(),AssetSchema.MAX_MANIFEST),b->new Manifest(b.readUtf(AssetSchema.MAX_MANIFEST)));
        @Override public Type<Manifest> type(){return TYPE;}
    }
    public record Request(String json) implements CustomPacketPayload {
        public static final Type<Request> TYPE=new Type<>(ResourceLocation.parse("psychiatryk_roles:asset_request"));
        public static final StreamCodec<RegistryFriendlyByteBuf,Request> CODEC=StreamCodec.of(
            (b,p)->b.writeUtf(p.json(),AssetSchema.MAX_REQUEST),b->new Request(b.readUtf(AssetSchema.MAX_REQUEST)));
        @Override public Type<Request> type(){return TYPE;}
    }
    public record Chunk(String revision,String id,int offset,byte[] bytes) implements CustomPacketPayload {
        public static final Type<Chunk> TYPE=new Type<>(ResourceLocation.parse("psychiatryk_roles:asset_chunk"));
        public static final StreamCodec<RegistryFriendlyByteBuf,Chunk> CODEC=StreamCodec.of((b,p)->{
            AssetSchema.hash(p.revision());AssetSchema.id(p.id());
            AssetSchema.require(p.offset()>=0&&p.bytes()!=null&&p.bytes().length>0&&p.bytes().length<=AssetSchema.CHUNK,"Invalid chunk");
            b.writeUtf(p.revision(),64);b.writeUtf(p.id(),80);b.writeVarInt(p.offset());b.writeByteArray(p.bytes());
        },b->{String revision=b.readUtf(64),id=b.readUtf(80);int offset=b.readVarInt();byte[] bytes=b.readByteArray(AssetSchema.CHUNK);
            AssetSchema.hash(revision);AssetSchema.id(id);AssetSchema.require(offset>=0&&bytes.length>0,"Invalid chunk");return new Chunk(revision,id,offset,bytes);
        });
        @Override public Type<Chunk> type(){return TYPE;}
    }
    public record Ready(String json) implements CustomPacketPayload {
        public static final Type<Ready> TYPE=new Type<>(ResourceLocation.parse("psychiatryk_roles:asset_ready"));
        public static final StreamCodec<RegistryFriendlyByteBuf,Ready> CODEC=StreamCodec.of(
            (b,p)->b.writeUtf(p.json(),256),b->new Ready(b.readUtf(256)));
        @Override public Type<Ready> type(){return TYPE;}
    }
    public static void register(RegisterPayloadHandlersEvent event){
        var registrar=event.registrar("asset-preload-1").optional();
        registrar.playToClient(Manifest.TYPE,Manifest.CODEC,(packet,context)->context.enqueueWork(()->clientManifest.accept(packet.json())));
        registrar.playToClient(Chunk.TYPE,Chunk.CODEC,(packet,context)->context.enqueueWork(()->clientChunk.accept(packet)));
        registrar.playToServer(Request.TYPE,Request.CODEC,(packet,context)->context.enqueueWork(()->{
            if(context.player() instanceof ServerPlayer player)AssetServer.request(player,packet.json());
        }));
        registrar.playToServer(Ready.TYPE,Ready.CODEC,(packet,context)->context.enqueueWork(()->{
            if(context.player() instanceof ServerPlayer player)AssetServer.readyAck(player,packet.json());
        }));
    }
    public static boolean supported(ServerPlayer p){
        try{return p.connection.hasChannel(Manifest.TYPE)&&p.connection.hasChannel(Request.TYPE)
            &&p.connection.hasChannel(Chunk.TYPE)&&p.connection.hasChannel(Ready.TYPE);
        }catch(NullPointerException disconnected){return false;}
    }
    public static void sendManifest(ServerPlayer p,AssetSchema.Manifest m){
        if(supported(p))PacketDistributor.sendToPlayer(p,new Manifest(AssetSchema.JSON.toJson(m)));
    }
    public static void sendChunk(ServerPlayer p,Chunk chunk){if(supported(p))PacketDistributor.sendToPlayer(p,chunk);}
    public static void requestMissing(AssetSchema.Request r){PacketDistributor.sendToServer(new Request(AssetSchema.JSON.toJson(r)));}
    public static void announceReady(AssetSchema.Ready r){PacketDistributor.sendToServer(new Ready(AssetSchema.JSON.toJson(r)));}
}
