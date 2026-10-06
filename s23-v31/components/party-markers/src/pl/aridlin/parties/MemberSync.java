package pl.aridlin.parties;
import java.util.*;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
public record MemberSync(List<Member> members) implements CustomPacketPayload {
 public record Member(UUID uuid,String name,String dimension,double x,double y,double z,int color,float thickness,int entityId,String kind){}
 public static final Type<MemberSync> TYPE=new Type<>(ResourceLocation.fromNamespaceAndPath("goplanska_parties","members"));
 public static final StreamCodec<RegistryFriendlyByteBuf,MemberSync> CODEC=new StreamCodec<>() {
  public MemberSync decode(RegistryFriendlyByteBuf b){int n=b.readVarInt();if(n<0||n>256)throw new IllegalArgumentException("Invalid member count");var list=new ArrayList<Member>();for(int i=0;i<n;i++)list.add(new Member(b.readUUID(),b.readUtf(64),b.readUtf(128),b.readDouble(),b.readDouble(),b.readDouble(),b.readInt(),b.readFloat(),b.readVarInt(),b.readUtf(16)));return new MemberSync(List.copyOf(list));}
  public void encode(RegistryFriendlyByteBuf b,MemberSync data){b.writeVarInt(data.members.size());for(var m:data.members){b.writeUUID(m.uuid());b.writeUtf(m.name(),64);b.writeUtf(m.dimension(),128);b.writeDouble(m.x());b.writeDouble(m.y());b.writeDouble(m.z());b.writeInt(m.color());b.writeFloat(m.thickness());b.writeVarInt(m.entityId());b.writeUtf(m.kind(),16);}}
 };
 @Override public Type<? extends CustomPacketPayload> type(){return TYPE;}
}
