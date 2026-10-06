package pl.aridlin.psychiatrykroles;
import java.util.*;
import net.minecraft.server.level.*;
import net.minecraft.world.level.GameType;
import net.minecraft.world.item.*;
import net.minecraft.world.phys.*;
import net.minecraft.core.*;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.network.chat.Component;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.*;
@EventBusSubscriber(modid="psychiatryk_roles")
public final class PhaseCharm {
 public static Item ITEM;
 private record Session(ServerPlayer player,ServerLevel level,Vec3 start,float yaw,float pitch,GameType mode,int end){}
 private static final Map<UUID,Session> active=new HashMap<>();
 private static final Map<UUID,Integer> cooldown=new HashMap<>();
 public static boolean roleGameMode(ServerPlayer p,GameType mode){return active(p)?false:p.setGameMode(mode);}
 public static boolean active(ServerPlayer p){return active.containsKey(p.getUUID());}
 public static boolean hasCharm(ServerPlayer p){if(ITEM==null)return false;for(int i=0;i<p.getInventory().getContainerSize();i++)if(p.getInventory().getItem(i).is(ITEM))return true;return false;}
 public static void activate(ServerPlayer p){int tick=p.getServer().getTickCount();if(!p.isAlive()||p.isSpectator()||active(p)||p.getPersistentData().getBoolean("bodycam_active"))return;if(!hasCharm(p)){p.displayClientMessage(Component.literal("Carry a Phase Charm to use this key."),true);return;}int remaining=cooldown.getOrDefault(p.getUUID(),0)-tick;if(remaining>0){p.displayClientMessage(Component.literal("Phase cooldown: "+String.format(Locale.ROOT,"%.1fs",remaining/20.0)),true);return;}p.stopRiding();p.closeContainer();var config=PhaseConfig.get();var session=new Session(p,p.serverLevel(),p.position(),p.getYRot(),p.getXRot(),p.gameMode.getGameModeForPlayer(),tick+config.durationTicks());active.put(p.getUUID(),session);var tag=p.getPersistentData();tag.putInt("psychiatrykPhaseMode",session.mode().getId());tag.putString("psychiatrykPhaseDimension",session.level().dimension().location().toString());tag.putDouble("psychiatrykPhaseX",session.start().x);tag.putDouble("psychiatrykPhaseY",session.start().y);tag.putDouble("psychiatrykPhaseZ",session.start().z);p.setGameMode(GameType.SPECTATOR);p.setCamera(p);}
 public static boolean fullyAir(ServerPlayer p){var box=p.getDimensions(net.minecraft.world.entity.Pose.STANDING).makeBoundingBox(p.position()).deflate(1e-6);var level=p.serverLevel();if(box.getXsize()*box.getYsize()*box.getZsize()>4096)return false;if(!level.getWorldBorder().isWithinBounds(box)||box.minY<level.getMinBuildHeight()||box.maxY>level.getMaxBuildHeight())return false;for(var pos:BlockPos.betweenClosed(BlockPos.containing(box.minX,box.minY,box.minZ),BlockPos.containing(box.maxX,box.maxY,box.maxZ))){if(!level.hasChunkAt(pos)||!level.getBlockState(pos).isAir())return false;}return true;}
 private static void finish(Session s,boolean forceReturn){var p=s.player();boolean safe=!forceReturn&&p.isAlive()&&fullyAir(p);active.remove(p.getUUID());p.setCamera(p);if(!safe)p.teleportTo(s.level(),s.start().x,s.start().y,s.start().z,s.yaw(),s.pitch());p.setGameMode(s.mode());p.setDeltaMovement(Vec3.ZERO);p.fallDistance=0;if(safe)cooldown.put(p.getUUID(),p.getServer().getTickCount()+PhaseConfig.get().cooldownTicks());else cooldown.remove(p.getUUID());clearRecovery(p);}
 private static void clearRecovery(ServerPlayer p){for(String key:List.of("Mode","Dimension","X","Y","Z"))p.getPersistentData().remove("psychiatrykPhase"+key);}
 @SubscribeEvent public static void tick(ServerTickEvent.Post e){PhaseConfig.get();for(var s:new ArrayList<>(active.values()))if(e.getServer().getTickCount()>=s.end()||!s.player().isAlive())finish(s,!s.player().isAlive());}
 @SubscribeEvent public static void logout(PlayerEvent.PlayerLoggedOutEvent e){if(e.getEntity() instanceof ServerPlayer p){var s=active.get(p.getUUID());if(s!=null){finish(s,true);}cooldown.remove(p.getUUID());}}
 @SubscribeEvent public static void stop(ServerStoppingEvent e){for(var s:new ArrayList<>(active.values()))finish(s,true);cooldown.clear();}
 @SubscribeEvent public static void login(PlayerEvent.PlayerLoggedInEvent e){if(!(e.getEntity() instanceof ServerPlayer p))return;var tag=p.getPersistentData();if(!tag.contains("psychiatrykPhaseMode"))return;var level=p.getServer().getLevel(net.minecraft.resources.ResourceKey.create(Registries.DIMENSION,ResourceLocation.parse(tag.getString("psychiatrykPhaseDimension"))));if(level!=null)p.teleportTo(level,tag.getDouble("psychiatrykPhaseX"),tag.getDouble("psychiatrykPhaseY"),tag.getDouble("psychiatrykPhaseZ"),p.getYRot(),p.getXRot());p.setGameMode(GameType.byId(tag.getInt("psychiatrykPhaseMode")));clearRecovery(p);}
 public static final class CharmItem extends Item {
  public CharmItem(){super(new Item.Properties().stacksTo(1).rarity(Rarity.RARE));}
  @Override public net.minecraft.world.InteractionResultHolder<ItemStack> use(net.minecraft.world.level.Level level,net.minecraft.world.entity.player.Player player,net.minecraft.world.InteractionHand hand){
   if(player instanceof ServerPlayer serverPlayer)activate(serverPlayer);
   return net.minecraft.world.InteractionResultHolder.sidedSuccess(player.getItemInHand(hand),level.isClientSide());
  }
  @Override public net.minecraft.world.InteractionResult useOn(net.minecraft.world.item.context.UseOnContext ctx){
   if(ctx.getPlayer() instanceof ServerPlayer serverPlayer)activate(serverPlayer);
   return net.minecraft.world.InteractionResult.sidedSuccess(ctx.getLevel().isClientSide());
  }
 }
 @EventBusSubscriber(modid="psychiatryk_roles",bus=EventBusSubscriber.Bus.MOD)
 public static final class Registration {
 @SubscribeEvent public static void item(net.neoforged.neoforge.registries.RegisterEvent e){e.register(Registries.ITEM,h->h.register(ResourceLocation.fromNamespaceAndPath("psychiatryk_roles","phase_charm"),ITEM=new CharmItem()));}
 @SubscribeEvent public static void network(net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent e){e.registrar("1").playToServer(PhasePacket.TYPE,PhasePacket.CODEC,(data,ctx)->ctx.enqueueWork(()->{if(ctx.player() instanceof ServerPlayer p)activate(p);}));}
 }
}
