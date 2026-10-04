package pl.aridlin.spectate;
import java.util.*;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
public record SpectateData(boolean active,UUID target,String name,int selected,List<ItemStack> inventory,String menu,List<ItemStack> slots,int view) implements CustomPacketPayload {
 public static final Type<SpectateData> TYPE=new Type<>(ResourceLocation.parse("goplanska_spectate:state"));
 public static final StreamCodec<RegistryFriendlyByteBuf,SpectateData> CODEC=new StreamCodec<>(){
  public SpectateData decode(RegistryFriendlyByteBuf b){boolean active=b.readBoolean();UUID id=b.readUUID();String name=b.readUtf(64);int selected=b.readVarInt();int n=b.readVarInt();if(n<0||n>64)throw new IllegalArgumentException();var inv=new ArrayList<ItemStack>();for(int i=0;i<n;i++)inv.add(ItemStack.OPTIONAL_STREAM_CODEC.decode(b));String title=b.readUtf(128);n=b.readVarInt();if(n<0||n>512)throw new IllegalArgumentException();var slots=new ArrayList<ItemStack>();for(int i=0;i<n;i++)slots.add(ItemStack.OPTIONAL_STREAM_CODEC.decode(b));return new SpectateData(active,id,name,selected,inv,title,slots,b.readVarInt());}
  public void encode(RegistryFriendlyByteBuf b,SpectateData d){b.writeBoolean(d.active);b.writeUUID(d.target);b.writeUtf(d.name,64);b.writeVarInt(d.selected);b.writeVarInt(d.inventory.size());for(var s:d.inventory)ItemStack.OPTIONAL_STREAM_CODEC.encode(b,s);b.writeUtf(d.menu,128);b.writeVarInt(d.slots.size());for(var s:d.slots)ItemStack.OPTIONAL_STREAM_CODEC.encode(b,s);b.writeVarInt(d.view);}
 };
 @Override public Type<? extends CustomPacketPayload> type(){return TYPE;}
 public static SpectateData stop(){return new SpectateData(false,new UUID(0,0),"",0,List.of(),"",List.of(),-1);}
}
