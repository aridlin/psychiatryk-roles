package pl.aridlin.psychiatrykroles.testing;

import java.util.*;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.*;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetExperiencePacket;
import net.minecraft.resources.*;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.*;
import net.minecraft.world.level.levelgen.Heightmap;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.entity.*;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.registries.NeoForgeRegistries;
import pl.aridlin.psychiatrykroles.PsychiatrykRoles;
import pl.aridlin.psychiatrykroles.testing.mixin.PlayerSaveInvoker;

/** Two testing dimensions, one test bank, and an independent ordinary-world bank. */
@EventBusSubscriber(modid="psychiatryk_roles")
public final class TestingWorlds {
 private static final org.slf4j.Logger LOGGER=com.mojang.logging.LogUtils.getLogger();
 public static final ResourceKey<Level> NORMAL=key("test_normal"),FLAT=key("test_flat");
 public static final long NORMAL_SEED=202610060400L;
 private static final String MARK="PsychiatrykTestingBankV1";
 private static final Set<UUID> SWAPPING=new HashSet<>();
 private static ResourceKey<Level> key(String path){return ResourceKey.create(Registries.DIMENSION,ResourceLocation.fromNamespaceAndPath("psychiatryk_roles",path));}
 public static boolean testing(ResourceKey<Level> k){return k.equals(NORMAL)||k.equals(FLAT);}
 private static CompoundTag marker(ServerPlayer p){var root=p.getPersistentData();if(!root.contains(Player.PERSISTED_NBT_TAG))root.put(Player.PERSISTED_NBT_TAG,new CompoundTag());var persistent=root.getCompound(Player.PERSISTED_NBT_TAG);if(!persistent.contains(MARK))persistent.put(MARK,new CompoundTag());return persistent.getCompound(MARK);}
 public static CompoundTag snapshot(ServerPlayer p){return BankNbt.capture(p.saveWithoutId(new CompoundTag()));}
 private static CompoundTag position(ServerPlayer p){var t=new CompoundTag();t.putString("dimension",p.level().dimension().location().toString());t.putDouble("x",p.getX());t.putDouble("y",p.getY());t.putDouble("z",p.getZ());t.putFloat("yaw",p.getYRot());t.putFloat("pitch",p.getXRot());return t;}
 private static boolean owner(ServerPlayer p){return p.hasPermissions(2)&&p.getGameProfile().getName().equalsIgnoreCase("aridlin");}
 public static boolean creativeAllowed(ServerPlayer p,CompoundTag entry){return owner(p)||entry.getBoolean("creativeGrant")||PsychiatrykRoles.testingPatient(p);}
 private static GameType testMode(ServerPlayer p,CompoundTag entry){return creativeAllowed(p,entry)&&!entry.getBoolean("preferSurvival")?GameType.CREATIVE:GameType.SURVIVAL;}
 public static void restore(ServerPlayer p,CompoundTag bank){
  CompoundTag current=p.saveWithoutId(new CompoundTag());CompoundTag merged=BankNbt.merge(current,bank);
  // Player's reader only updates shoulder entities when these keys exist.
  for(String k:List.of("ShoulderEntityLeft","ShoulderEntityRight"))if(!merged.contains(k))merged.put(k,new CompoundTag());
  // NeoForge's deserialize merges attachments, so explicitly discard the outgoing bank's instances.
  var ids=new HashSet<String>(current.getCompound(BankNbt.ATTACHMENTS).getAllKeys());ids.addAll(bank.getCompound(BankNbt.ATTACHMENTS).getAllKeys());
  for(String id:ids)if(!id.equals("neoforge:persistent_data")&&NeoForgeRegistries.ATTACHMENT_TYPES.get(ResourceLocation.parse(id))==null)throw new IllegalStateException("Missing inventory attachment type: "+id);
  for(String id:ids)if(!id.equals("neoforge:persistent_data"))p.removeData(NeoForgeRegistries.ATTACHMENT_TYPES.get(ResourceLocation.parse(id)));
  p.removeAllEffects();p.setRespawnPosition(Level.OVERWORLD,null,0,false,false);p.load(merged);p.setDeltaMovement(net.minecraft.world.phys.Vec3.ZERO);p.fallDistance=0;p.clearFire();
  var after=p.saveWithoutId(new CompoundTag());for(String k:List.of("Inventory","EnderItems"))if(!Objects.equals(merged.get(k),after.get(k)))throw new IllegalStateException("Inventory restore did not round-trip: "+k);
 }
 private static void sync(ServerPlayer p){InventorySync.send(p);p.inventoryMenu.broadcastFullState();p.containerMenu.broadcastFullState();p.onUpdateAbilities();p.connection.send(new ClientboundSetExperiencePacket(p.experienceProgress,p.totalExperience,p.experienceLevel));p.server.getPlayerList().sendAllPlayerInfo(p);p.server.getPlayerList().sendActivePlayerEffects(p);}
 private static void savePlayer(ServerPlayer p)throws Exception{((PlayerSaveInvoker)p.server.getPlayerList()).testing$save(p);try{Class.forName("pl.aridlin.psychiatrykroles.io.PlayerSaves").getMethod("flushAll").invoke(null);}catch(ClassNotFoundException ignored){}}
 private static String bank(boolean test){return test?"test":"survival";}
 /** Called only on the server thread. Pre-swap journal completes before inventory mutation. */
 public static int move(ServerPlayer p,ResourceKey<Level> targetKey){
  if(SWAPPING.contains(p.getUUID()))return 0;
  boolean fromTest=testing(p.level().dimension()),toTest=testing(targetKey);var store=TestBanks.get(p.server);var entry=store.entry(p.getUUID());
  String activeMarker=marker(p).getString("active");if(!activeMarker.isEmpty()&&!activeMarker.equals(bank(fromTest))){p.sendSystemMessage(Component.literal("Inventory bank/dimension mismatch; this switch is blocked until your original world is restored."));return 0;}
  if(!fromTest&&!toTest)return 0;
  var target=p.server.getLevel(targetKey);if(target==null){p.sendSystemMessage(Component.literal("Testing dimension is not loaded; no inventory was changed."));return 0;}
  if(fromTest&&!toTest&&!entry.contains("survival")){p.sendSystemMessage(Component.literal("No survival bank exists. Contact aridlin; inventory remains in the test world."));return 0;}
  p.closeContainer();p.stopRiding();if(p.isSleeping())p.stopSleepInBed(true,true);
  CompoundTag source=snapshot(p),sourcePosition=position(p);GameType sourceMode=p.gameMode.getGameModeForPlayer();
  String posKey=fromTest?p.level().dimension().equals(FLAT)?"flatPosition":"normalPosition":"survivalPosition";
  entry.put(bank(fromTest),source.copy());entry.put(posKey,sourcePosition.copy());if(!fromTest)entry.putInt("survivalMode",sourceMode.getId());
  CompoundTag destination=entry.contains(bank(toTest))?entry.getCompound(bank(toTest)).copy():snapshot(new ServerPlayer(p.server,p.serverLevel(),p.getGameProfile(),p.clientInformation()));
  String targetPosition=toTest?targetKey.equals(FLAT)?"flatPosition":"normalPosition":"survivalPosition";
  var pos=entry.getCompound(targetPosition).copy();
  if(pos.isEmpty()){int x=0,z=0;target.getChunk(x>>4,z>>4);int y=target.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,x,z);pos.putDouble("x",x+.5);pos.putDouble("y",y);pos.putDouble("z",z+.5);}
  try{
   entry.putString("active",bank(fromTest));entry.putString("pending",bank(toTest));store.setDirty();store.durable(p.server);savePlayer(p);
  }catch(Exception ex){LOGGER.error("Testing-world inventory journal/save failed; switch canceled",ex);p.sendSystemMessage(Component.literal("Could not save the inventory journal. The switch was canceled."));return 0;}
  SWAPPING.add(p.getUUID());
  try{
   if(fromTest!=toTest)restore(p,destination);
   p.teleportTo(target,pos.getDouble("x"),pos.getDouble("y"),pos.getDouble("z"),pos.getFloat("yaw"),pos.getFloat("pitch"));
   if(!p.level().dimension().equals(targetKey))throw new IllegalStateException("Dimension transition was refused");
   marker(p).putString("active",bank(toTest));marker(p).putString("dimension",targetKey.location().toString());
   p.setGameMode(toTest?testMode(p,entry):GameType.byId(entry.getInt("survivalMode")));sync(p);savePlayer(p);
   entry.put(bank(toTest),snapshot(p));entry.putString("active",bank(toTest));entry.remove("pending");store.setDirty();store.durable(p.server);
   p.sendSystemMessage(Component.literal(toTest?"Testing inventory active. /testworld back restores your normal inventory.":"Normal inventory restored."));return 1;
  }catch(Exception ex){
   LOGGER.error("Testing-world transition failed; restoring durable source bank",ex);
   // The pre-swap source is durable. A failed transition is rolled back before any further interaction.
   try{restore(p,source);var old=p.server.getLevel(ResourceKey.create(Registries.DIMENSION,ResourceLocation.parse(sourcePosition.getString("dimension"))));p.teleportTo(old,sourcePosition.getDouble("x"),sourcePosition.getDouble("y"),sourcePosition.getDouble("z"),sourcePosition.getFloat("yaw"),sourcePosition.getFloat("pitch"));p.setGameMode(sourceMode);marker(p).putString("active",bank(fromTest));marker(p).putString("dimension",old.dimension().location().toString());sync(p);savePlayer(p);entry.putString("active",bank(fromTest));entry.remove("pending");store.durable(p.server);}catch(Exception rollback){p.connection.disconnect(Component.literal("Inventory switch failed; your saved source bank is retained. Contact aridlin before joining again."));}
   p.sendSystemMessage(Component.literal("Testing-world switch failed and was canceled."));return 0;
  }finally{SWAPPING.remove(p.getUUID());}
 }
 public static int back(ServerPlayer p){var e=TestBanks.get(p.server).entry(p.getUUID());var pos=e.getCompound("survivalPosition");if(!pos.contains("dimension"))return 0;return move(p,ResourceKey.create(Registries.DIMENSION,ResourceLocation.parse(pos.getString("dimension"))));}
 public static int mode(ServerPlayer p,boolean creative){if(!testing(p.level().dimension()))return 0;var d=TestBanks.get(p.server);var e=d.entry(p.getUUID());if(creative&&!creativeAllowed(p,e)){p.sendSystemMessage(Component.literal("Creative testing requires an aridlin grant for your class."));return 0;}e.putBoolean("preferSurvival",!creative);d.setDirty();p.setGameMode(creative?GameType.CREATIVE:GameType.SURVIVAL);return 1;}
 @SubscribeEvent public static void commands(RegisterCommandsEvent e){
  e.getDispatcher().register(Commands.literal("testworld")
   .then(Commands.literal("normal").executes(c->move(c.getSource().getPlayerOrException(),NORMAL)))
   .then(Commands.literal("flat").executes(c->move(c.getSource().getPlayerOrException(),FLAT)))
   .then(Commands.literal("back").executes(c->back(c.getSource().getPlayerOrException())))
   .then(Commands.literal("mode").then(Commands.literal("survival").executes(c->mode(c.getSource().getPlayerOrException(),false))).then(Commands.literal("creative").executes(c->mode(c.getSource().getPlayerOrException(),true))))
   .then(Commands.literal("grant").requires(s->s.getEntity() instanceof ServerPlayer p&&owner(p)).then(Commands.argument("player",EntityArgument.player()).then(Commands.literal("creative").executes(c->grant(EntityArgument.getPlayer(c,"player"),true))).then(Commands.literal("survival").executes(c->grant(EntityArgument.getPlayer(c,"player"),false))))));
 }
 private static int grant(ServerPlayer p,boolean creative){var d=TestBanks.get(p.server);var e=d.entry(p.getUUID());e.putBoolean("creativeGrant",creative);e.putBoolean("preferSurvival",!creative);d.setDirty();if(testing(p.level().dimension()))p.setGameMode(creative?GameType.CREATIVE:GameType.SURVIVAL);return 1;}
 @SubscribeEvent(priority=EventPriority.HIGHEST) public static void travel(EntityTravelToDimensionEvent e){if(e.getEntity().level().isClientSide())return;boolean cross=testing(e.getEntity().level().dimension())!=testing(e.getDimension());if(cross&&(!(e.getEntity() instanceof ServerPlayer p)||!SWAPPING.contains(p.getUUID())))e.setCanceled(true);}
 @SubscribeEvent(priority=EventPriority.HIGHEST) public static void entityJoin(EntityJoinLevelEvent e){if(e.getLevel().isClientSide()||e.getEntity() instanceof Player)return;var data=e.getEntity().getPersistentData();boolean targetTest=testing(e.getLevel().dimension());if(data.contains("PsychiatrykTestOrigin")&&data.getBoolean("PsychiatrykTestOrigin")!=targetTest){e.setCanceled(true);return;}data.putBoolean("PsychiatrykTestOrigin",targetTest);}
 @SubscribeEvent(priority=EventPriority.HIGHEST) public static void gameMode(PlayerEvent.PlayerChangeGameModeEvent e){if(e.getEntity() instanceof ServerPlayer p&&testing(p.level().dimension())&&!SWAPPING.contains(p.getUUID())){var bank=TestBanks.get(p.server).entry(p.getUUID());if(e.getNewGameMode()!=GameType.SURVIVAL&&(e.getNewGameMode()!=GameType.CREATIVE||!creativeAllowed(p,bank)))e.setCanceled(true);}}
 @SubscribeEvent public static void logout(PlayerEvent.PlayerLoggedOutEvent e){if(e.getEntity() instanceof ServerPlayer p&&!SWAPPING.contains(p.getUUID())){var d=TestBanks.get(p.server);var entry=d.entry(p.getUUID());entry.put(bank(testing(p.level().dimension())),snapshot(p));d.setDirty();}}
 @SubscribeEvent public static void login(PlayerEvent.PlayerLoggedInEvent e){if(!(e.getEntity() instanceof ServerPlayer p))return;var d=TestBanks.get(p.server);var entry=d.entry(p.getUUID());boolean test=testing(p.level().dimension());String active=marker(p).getString("active");if(test&&!entry.contains("survival")){p.connection.disconnect(Component.literal("Your test-world player has no survival bank. Contact aridlin; no inventory was changed."));return;}if(!active.isEmpty()&&!active.equals(bank(test))){p.connection.disconnect(Component.literal("Inventory bank/dimension mismatch. Contact aridlin; both saved banks are retained."));return;}entry.put(bank(test),snapshot(p));entry.putString("active",bank(test));entry.remove("pending");marker(p).putString("active",bank(test));marker(p).putString("dimension",p.level().dimension().location().toString());d.setDirty();if(test)p.setGameMode(testMode(p,entry));try{sync(p);}catch(RuntimeException error){LOGGER.error("Could not synchronize loaded inventory bank",error);p.connection.disconnect(Component.literal("Inventory bank synchronization failed; saved inventory is retained. Contact aridlin."));}}
 @SubscribeEvent public static void clone(PlayerEvent.Clone e){if(e.getOriginal() instanceof ServerPlayer old&&e.getEntity() instanceof ServerPlayer next&&testing(old.level().dimension()))marker(next).putString("respawnDimension",old.level().dimension().location().toString());}
 @SubscribeEvent public static void respawn(PlayerEvent.PlayerRespawnEvent e){if(!(e.getEntity() instanceof ServerPlayer p))return;var m=marker(p);if(!m.contains("respawnDimension"))return;var target=p.server.getLevel(ResourceKey.create(Registries.DIMENSION,ResourceLocation.parse(m.getString("respawnDimension"))));m.remove("respawnDimension");if(target==null){p.connection.disconnect(Component.literal("Testing respawn dimension unavailable; inventory preserved."));return;}SWAPPING.add(p.getUUID());try{target.getChunk(0,0);BlockPos spawn=new BlockPos(0,target.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,0,0),0);p.teleportTo(target,spawn.getX()+.5,spawn.getY(),spawn.getZ()+.5,0,0);p.setGameMode(testMode(p,TestBanks.get(p.server).entry(p.getUUID())));marker(p).putString("active","test");marker(p).putString("dimension",target.dimension().location().toString());var d=TestBanks.get(p.server);d.entry(p.getUUID()).put("test",snapshot(p));d.setDirty();}finally{SWAPPING.remove(p.getUUID());}}
 @SubscribeEvent public static void tick(PlayerTickEvent.Post e){if(e.getEntity() instanceof ServerPlayer p&&!SWAPPING.contains(p.getUUID())){String active=marker(p).getString("active");if(!active.isEmpty()&&!active.equals(bank(testing(p.level().dimension())))){String dimension=marker(p).getString("dimension");var original=p.server.getLevel(ResourceKey.create(Registries.DIMENSION,ResourceLocation.parse(dimension)));if(original==null){p.connection.disconnect(Component.literal("Inventory bank mismatch; saved banks retained."));return;}SWAPPING.add(p.getUUID());try{p.closeContainer();p.teleportTo(original,original.getSharedSpawnPos().getX()+.5,original.getSharedSpawnPos().getY(),original.getSharedSpawnPos().getZ()+.5,0,0);}finally{SWAPPING.remove(p.getUUID());}}}}
 private TestingWorlds(){}
}
