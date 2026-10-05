package pl.aridlin.parties;
import java.util.*;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record WaypointSync(List<PartyData.Waypoint> points) implements CustomPacketPayload {
    public static final Type<WaypointSync> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath("goplanska_parties","waypoints"));
    public static final StreamCodec<RegistryFriendlyByteBuf,WaypointSync> CODEC = new StreamCodec<>() {
        public WaypointSync decode(RegistryFriendlyByteBuf b) {
            int count = b.readVarInt(); if(count < 0 || count > 32) throw new IllegalArgumentException("Invalid waypoint count");
            List<PartyData.Waypoint> list = new ArrayList<>();
            for(int i=0;i<count;i++)list.add(new PartyData.Waypoint(b.readVarInt(),b.readUtf(32),b.readUtf(128),b.readDouble(),b.readDouble(),b.readDouble(),b.readUUID()));
            return new WaypointSync(List.copyOf(list));
        }
        public void encode(RegistryFriendlyByteBuf b,WaypointSync data) {
            b.writeVarInt(data.points.size());
            for(var w:data.points){b.writeVarInt(w.party());b.writeUtf(w.name(),32);b.writeUtf(w.dimension(),128);b.writeDouble(w.x());b.writeDouble(w.y());b.writeDouble(w.z());b.writeUUID(w.creator());}
        }
    };
    @Override public Type<? extends CustomPacketPayload> type(){return TYPE;}
}
