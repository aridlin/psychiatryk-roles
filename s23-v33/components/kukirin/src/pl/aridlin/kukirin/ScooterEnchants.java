package pl.aridlin.kukirin;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.*;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.commands.Commands;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
@EventBusSubscriber(modid="goplanska_kukirin")
public final class ScooterEnchants {
 public static final String BOUND="GoplanskaBoundScooter",OWNER="GoplanskaScooterOwner";
 public static int level(Scooter scooter,String enchant){return scooter.cachedEnchantLevel(enchant);}

 public static boolean bound(ItemStack stack){var data=stack.get(DataComponents.CUSTOM_DATA);return data!=null&&data.getUnsafe().getBoolean(BOUND);}
 public static java.util.UUID owner(ItemStack stack){var data=stack.get(DataComponents.CUSTOM_DATA);if(data==null)return null;var tag=data.getUnsafe();return tag.hasUUID(OWNER)?tag.getUUID(OWNER):null;}
 public static void bind(ItemStack stack,ServerPlayer p){if(bound(stack)&&owner(stack)==null)CustomData.update(DataComponents.CUSTOM_DATA,stack,tag->tag.putUUID(OWNER,p.getUUID()));}
 public static boolean owned(Scooter s,ServerPlayer p){return p.getUUID().equals(owner(s.getItemBySlot(EquipmentSlot.FEET)));}
 private record Recall(ScooterIndex.Entry entry,long expires){}
 private static final java.util.Map<java.util.UUID,Recall> recalls=new java.util.HashMap<>();
 @SubscribeEvent public static void logout(net.neoforged.neoforge.event.entity.player.PlayerEvent.PlayerLoggedOutEvent e){recalls.remove(e.getEntity().getUUID());landings.remove(e.getEntity().getUUID());}
 private static boolean deliver(Scooter s,ServerPlayer p){if(!owned(s,p)||level(s,"loyalty")==0||s.isVehicle())return false;var item=s.getItemBySlot(EquipmentSlot.FEET).copyWithCount(1);if(!p.getInventory().add(item)){p.displayClientMessage(net.minecraft.network.chat.Component.literal("Make room in your inventory first."),false);return false;}ScooterIndex.get(p.getServer()).remove(s.getUUID());s.discard();p.displayClientMessage(net.minecraft.network.chat.Component.literal("Scooter recalled."),false);return true;}
 private static boolean eligibleDrop(net.minecraft.world.entity.item.ItemEntity item,ServerPlayer p){if(!item.getItem().is(Kukirin.ITEM.get())||!p.getUUID().equals(owner(item.getItem())))return false;var key=ResourceKey.create(Registries.ENCHANTMENT,ResourceLocation.withDefaultNamespace("loyalty"));return item.getItem().getEnchantmentLevel(p.registryAccess().lookupOrThrow(Registries.ENCHANTMENT).getOrThrow(key))>0;}
 private static boolean deliverDrop(net.minecraft.world.entity.item.ItemEntity item,ServerPlayer p){if(!eligibleDrop(item,p))return false;var copy=item.getItem().copy();if(!p.getInventory().add(copy)){item.setItem(copy);p.displayClientMessage(net.minecraft.network.chat.Component.literal("Make room in your inventory first."),false);return false;}ScooterIndex.get(p.getServer()).remove(item.getUUID());item.discard();p.displayClientMessage(net.minecraft.network.chat.Component.literal("Scooter item recalled."),false);return true;}
 private static void retryRecall(ServerPlayer p){var request=recalls.get(p.getUUID());if(request==null)return;var world=p.getServer().getLevel(ResourceKey.create(Registries.DIMENSION,ResourceLocation.parse(request.entry().dimension())));var entity=world==null?null:world.getEntity(request.entry().id());if(entity instanceof Scooter s){deliver(s,p);recalls.remove(p.getUUID());}else if(entity instanceof net.minecraft.world.entity.item.ItemEntity item){deliverDrop(item,p);recalls.remove(p.getUUID());}else if(p.level().getGameTime()>request.expires()){recalls.remove(p.getUUID());p.displayClientMessage(net.minecraft.network.chat.Component.literal("Scooter could not be loaded; it has not been removed."),false);}}
 private record Landing(long until,int feather){}
 private static final java.util.Map<java.util.UUID,Landing> landings=new java.util.HashMap<>();
 @SubscribeEvent public static void fall(net.neoforged.neoforge.event.entity.living.LivingFallEvent e){if(!(e.getEntity() instanceof ServerPlayer p))return;var l=landings.get(p.getUUID());if(l!=null&&p.level().getGameTime()<=l.until())e.setDamageMultiplier(e.getDamageMultiplier()*Math.max(.2f,1-.2f*l.feather()));}
 // Pickup and dismantling discard the entity; chunk unload must keep its recall record.
 @SubscribeEvent public static void removed(net.neoforged.neoforge.event.entity.EntityLeaveLevelEvent e){
  if(!(e.getLevel() instanceof net.minecraft.server.level.ServerLevel world))return;
  var entity=e.getEntity();if(!(entity instanceof Scooter)&&!(entity instanceof net.minecraft.world.entity.item.ItemEntity))return;
  var reason=entity.getRemovalReason();if(reason==null)return;
  var index=ScooterIndex.get(world.getServer());
  if(reason.shouldDestroy())index.remove(entity.getUUID());
  else if(entity instanceof Scooter s)index.track(s);
  else if(entity instanceof net.minecraft.world.entity.item.ItemEntity item&&item.getItem().is(Kukirin.ITEM.get()))index.track(item);
 }
 @SubscribeEvent public static void protectDrop(net.neoforged.neoforge.event.tick.EntityTickEvent.Pre e){if(!(e.getEntity() instanceof net.minecraft.world.entity.item.ItemEntity item)||item.level().isClientSide||!item.getItem().is(Kukirin.ITEM.get())||!bound(item.getItem()))return;item.setInvulnerable(true);item.setUnlimitedLifetime();if(item.tickCount%20==0)ScooterIndex.get(((net.minecraft.server.level.ServerLevel)item.level()).getServer()).track(item);var tag=item.getPersistentData();if(item.getY()<item.level().getMinBuildHeight()-16){var p=tag.contains("ScooterSafePos")?net.minecraft.core.BlockPos.of(tag.getLong("ScooterSafePos")):item.level().getSharedSpawnPos();item.teleportTo(p.getX()+.5,p.getY()+1,p.getZ()+.5);item.setDeltaMovement(net.minecraft.world.phys.Vec3.ZERO);}else if(item.onGround())tag.putLong("ScooterSafePos",item.blockPosition().asLong());}
 @SubscribeEvent public static void inventory(PlayerTickEvent.Post e){if(!(e.getEntity() instanceof ServerPlayer p))return;retryRecall(p);if(p.getVehicle() instanceof Scooter s)landings.put(p.getUUID(),new Landing(p.level().getGameTime()+40,level(s,"feather_falling")));else{var l=landings.get(p.getUUID());if(l!=null&&p.level().getGameTime()>l.until())landings.remove(p.getUUID());}for(int i=0;i<p.getInventory().getContainerSize();i++){var stack=p.getInventory().getItem(i);if(stack.is(Kukirin.ITEM.get()))bind(stack,p);}}
 @SubscribeEvent public static void commands(RegisterCommandsEvent e){e.getDispatcher().register(Commands.literal("scooterstorage").executes(c->{var p=c.getSource().getPlayerOrException();var scooter=ScooterStorage.target(p);if(scooter!=null&&ScooterStorage.open(scooter,p))return 1;c.getSource().sendFailure(net.minecraft.network.chat.Component.literal("Ride or aim at a scooter upgraded with a chest."));return 0;}));e.getDispatcher().register(Commands.literal("scooterrecall").executes(c->{var p=c.getSource().getPlayerOrException();for(var w:p.getServer().getAllLevels())for(var entity:w.getAllEntities())if(entity instanceof net.minecraft.world.entity.item.ItemEntity item&&eligibleDrop(item,p))return deliverDrop(item,p)?1:0;Scooter found=null;double nearest=Double.MAX_VALUE;for(var world:p.getServer().getAllLevels())for(var entity:world.getAllEntities())if(entity instanceof Scooter s&&owned(s,p)&&level(s,"loyalty")>0&&!s.isVehicle()){double d=s.level()==p.level()?s.distanceToSqr(p):Double.MAX_VALUE/2;if(d<nearest){nearest=d;found=s;}}if(found==null){for(var entry:new java.util.ArrayList<>(ScooterIndex.get(p.getServer()).entries.values()))if(entry.owner().equals(p.getUUID())&&entry.loyalty()){var world=p.getServer().getLevel(net.minecraft.resources.ResourceKey.create(Registries.DIMENSION,ResourceLocation.parse(entry.dimension())));if(world==null)continue;world.getChunkSource().addRegionTicket(net.minecraft.server.level.TicketType.PORTAL,new net.minecraft.world.level.ChunkPos(entry.pos()),3,entry.pos());recalls.put(p.getUUID(),new Recall(entry,p.level().getGameTime()+100));c.getSource().sendSuccess(()->net.minecraft.network.chat.Component.literal("Calling your scooter…"),false);return 1;}}if(found==null){c.getSource().sendFailure(net.minecraft.network.chat.Component.literal("No unoccupied bound scooter with Loyalty found."));return 0;}return deliver(found,p)?1:0;}));}
}
