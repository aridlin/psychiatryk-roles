package pl.aridlin.kukirin;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
/** Only actually submitted rental models refresh their two-minute visibility lease. */
public record ScooterRentalSeen(int[] ids) implements CustomPacketPayload {
 public static final Type<ScooterRentalSeen> TYPE=new Type<>(ResourceLocation.parse("goplanska_kukirin:rental_seen"));
 public static final StreamCodec<RegistryFriendlyByteBuf,ScooterRentalSeen> CODEC=new StreamCodec<>(){
  public ScooterRentalSeen decode(RegistryFriendlyByteBuf b){int n=b.readVarInt();if(n<0||n>64)throw new IllegalArgumentException("Rental visibility limit");int[] ids=new int[n];for(int i=0;i<n;i++)ids[i]=b.readVarInt();return new ScooterRentalSeen(ids);}
  public void encode(RegistryFriendlyByteBuf b,ScooterRentalSeen p){if(p.ids.length>64)throw new IllegalArgumentException("Rental visibility limit");b.writeVarInt(p.ids.length);for(int id:p.ids)b.writeVarInt(id);}
 };
 public Type<ScooterRentalSeen> type(){return TYPE;}
 public static void register(net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent e){e.registrar("1").playToServer(TYPE,CODEC,(data,ctx)->ctx.enqueueWork(()->{if(ctx.player() instanceof net.minecraft.server.level.ServerPlayer player){var state=player.getPersistentData();long now=player.level().getGameTime();if(now<state.getLong("RentalSeenNext"))return;state.putLong("RentalSeenNext",now+20);for(int id:data.ids)if(player.level().getEntity(id) instanceof Scooter scooter)ScooterRental.renderedBy(scooter,player);}}));}
}
