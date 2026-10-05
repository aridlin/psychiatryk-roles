package pl.aridlin.psychiatrykroles;
import java.util.*;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
public record MarkSync(List<Mark> marks) implements CustomPacketPayload {
 public record Mark(UUID id,double x,double y,double z,double X,double Y,double Z,int color,boolean chalk){}
 public static final Type<MarkSync> TYPE=new Type<>(ResourceLocation.fromNamespaceAndPath("psychiatryk_roles","marking_areas"));
 public static final StreamCodec<RegistryFriendlyByteBuf,MarkSync> CODEC=new StreamCodec<>(){public MarkSync decode(RegistryFriendlyByteBuf b){int n=b.readVarInt();if(n<0||n>256)throw new IllegalArgumentException("Too many marks");var list=new ArrayList<Mark>();for(int i=0;i<n;i++)list.add(new Mark(b.readUUID(),b.readDouble(),b.readDouble(),b.readDouble(),b.readDouble(),b.readDouble(),b.readDouble(),b.readInt(),b.readBoolean()));return new MarkSync(List.copyOf(list));}public void encode(RegistryFriendlyByteBuf b,MarkSync p){b.writeVarInt(p.marks.size());for(var m:p.marks){b.writeUUID(m.id());b.writeDouble(m.x());b.writeDouble(m.y());b.writeDouble(m.z());b.writeDouble(m.X());b.writeDouble(m.Y());b.writeDouble(m.Z());b.writeInt(m.color());b.writeBoolean(m.chalk());}}};
 @Override public Type<? extends CustomPacketPayload> type(){return TYPE;}
}
