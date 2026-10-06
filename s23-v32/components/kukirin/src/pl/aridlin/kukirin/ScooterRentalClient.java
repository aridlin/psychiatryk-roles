package pl.aridlin.kukirin;
@net.neoforged.fml.common.EventBusSubscriber(modid="goplanska_kukirin",value=net.neoforged.api.distmarker.Dist.CLIENT)
public final class ScooterRentalClient {
 private static final java.util.Set<Integer> seen=new java.util.HashSet<>();private static net.minecraft.client.multiplayer.ClientLevel level;private static int ticks;
 public static void rendered(Scooter s){if(ScooterRental.isRental(s)&&seen.size()<64)seen.add(s.getId());}
 @net.neoforged.bus.api.SubscribeEvent public static void tick(net.neoforged.neoforge.client.event.ClientTickEvent.Post e){var mc=net.minecraft.client.Minecraft.getInstance();if(mc.level!=level){seen.clear();level=mc.level;}if(++ticks%40==0&&mc.player!=null&&!seen.isEmpty()){net.neoforged.neoforge.network.PacketDistributor.sendToServer(new ScooterRentalSeen(seen.stream().mapToInt(Integer::intValue).toArray()));seen.clear();}}
}
