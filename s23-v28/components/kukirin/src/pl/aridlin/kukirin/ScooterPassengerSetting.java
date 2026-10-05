package pl.aridlin.kukirin;
public record ScooterPassengerSetting(boolean enabled) implements net.minecraft.network.protocol.common.custom.CustomPacketPayload {
 public static final Type<ScooterPassengerSetting> TYPE=new Type<>(net.minecraft.resources.ResourceLocation.parse("goplanska_kukirin:passenger_setting"));
 public static final net.minecraft.network.codec.StreamCodec<net.minecraft.network.RegistryFriendlyByteBuf,ScooterPassengerSetting> CODEC=net.minecraft.network.codec.StreamCodec.composite(net.minecraft.network.codec.ByteBufCodecs.BOOL,ScooterPassengerSetting::enabled,ScooterPassengerSetting::new);
 public Type<ScooterPassengerSetting> type(){return TYPE;}
 public static void register(net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent e){e.registrar("1").playToServer(TYPE,CODEC,(data,ctx)->ctx.enqueueWork(()->{if(ctx.player() instanceof net.minecraft.server.level.ServerPlayer p&&p.getVehicle() instanceof Scooter s&&ScooterUpgradeRecipe.has(s.getItemBySlot(net.minecraft.world.entity.EquipmentSlot.FEET),"saddle")&&(ScooterEnchants.bound(s.getItemBySlot(net.minecraft.world.entity.EquipmentSlot.FEET))?ScooterEnchants.owned(s,p):s.getControllingPassenger()==p))ScooterPassengers.enabled(s,data.enabled());}));}
}
