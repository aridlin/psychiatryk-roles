package pl.aridlin.starter;
import java.util.*;
import net.minecraft.core.*;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.*;
import net.minecraft.world.*;
import net.minecraft.world.item.*;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.block.Rotation;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.common.util.BlockSnapshot;
import net.neoforged.neoforge.event.EventHooks;
@Mod("goplanska_starter")
public final class KinkerStarter {
 public static final DeferredRegister.Items ITEMS=DeferredRegister.createItems("goplanska_starter");
 public static final DeferredItem<Item> STARTER=ITEMS.register("kinker_starter",()->new Item(new Item.Properties().stacksTo(16)){
  @Override public InteractionResult useOn(UseOnContext c){if(c.getLevel().isClientSide)return InteractionResult.SUCCESS;if(!(c.getPlayer() instanceof ServerPlayer p))return InteractionResult.PASS;return activate(p,(ServerLevel)c.getLevel(),c.getClickedPos().relative(c.getClickedFace()),c.getItemInHand(),p.getDirection());}
 });
 public record Pending(ServerLevel level,BlockPos origin,Rotation rotation,long created){}
 private static final Map<UUID,Pending> pending=new HashMap<>();
 public KinkerStarter(IEventBus bus){ITEMS.register(bus);bus.addListener(this::network);net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(this::commands);
  bus.addListener((net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent e)->{if(e.getTabKey()==CreativeModeTabs.TOOLS_AND_UTILITIES)e.accept(STARTER.get());});
  net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener((net.neoforged.neoforge.event.entity.player.PlayerEvent.PlayerLoggedOutEvent e)->pending.remove(e.getEntity().getUUID()));
  net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener((net.neoforged.neoforge.event.entity.player.PlayerEvent.PlayerChangedDimensionEvent e)->pending.remove(e.getEntity().getUUID()));
 }
 private void commands(net.neoforged.neoforge.event.RegisterCommandsEvent e){e.getDispatcher().register(net.minecraft.commands.Commands.literal("packrollback").requires(source->source.hasPermission(4)).executes(c->{c.getSource().sendFailure(Component.literal("This release adds saved entities. A manual rollback requires matching world and mod backups; automatic rollback remains active for failed startup. No restart scheduled."));return 0;}));}
 private void network(RegisterPayloadHandlersEvent e){e.registrar("1").playToClient(PreviewPacket.TYPE,PreviewPacket.CODEC,(data,ctx)->ctx.enqueueWork(()->PreviewClient.receive(data)));e.registrar("1").playToServer(MirrorPacket.TYPE,MirrorPacket.CODEC,(data,ctx)->ctx.enqueueWork(()->{if(ctx.player() instanceof ServerPlayer p)MirrorPacket.activate(p);}));}
 static Rotation rotation(Direction d){return switch(d){case EAST->Rotation.CLOCKWISE_90;case SOUTH->Rotation.CLOCKWISE_180;case WEST->Rotation.COUNTERCLOCKWISE_90;default->Rotation.NONE;};}
 public static boolean clear(ServerPlayer player,ServerLevel level,Pending preview){
  var positions=new ArrayList<BlockPos>();for(var b:StarterLayout.create(preview.origin(),preview.rotation()))positions.add(b.pos());positions.addAll(StarterLayout.emptyInterior(preview.origin(),preview.rotation()));
  for(var pos:positions)if(!level.hasChunkAt(pos)||!level.getWorldBorder().isWithinBounds(pos)||pos.getY()<level.getMinBuildHeight()||pos.getY()>=level.getMaxBuildHeight()||!level.getBlockState(pos).canBeReplaced()||!level.getFluidState(pos).isEmpty()||!level.mayInteract(player,pos))return false;
  return player.mayBuild();
 }
 private static void preview(ServerPlayer p,Pending q,boolean visible,boolean valid){if(p instanceof net.neoforged.neoforge.common.util.FakePlayer)return;PacketDistributor.sendToPlayer(p,new PreviewPacket(q.origin,q.rotation,visible,valid));}
 public static InteractionResult activate(ServerPlayer p,ServerLevel level,BlockPos origin,ItemStack stack,Direction facing){
  long time=level.getGameTime();Pending q=pending.get(p.getUUID());
  if(q==null||q.level()!=level||time-q.created()>1200||p.isShiftKeyDown()||(q!=null&&q.origin().distToCenterSqr(p.position())>144)){
   q=new Pending(level,origin.immutable(),rotation(facing.getOpposite()),time);pending.put(p.getUUID(),q);boolean valid=clear(p,level,q);preview(p,q,true,valid);
   p.sendSystemMessage(Component.literal(valid?"Kinker Starter preview: use again to build and consume one item. Sneak-use moves the preview. It expires in 60 seconds.":"Kinker Starter preview: space is blocked (red). Sneak-use to choose another position."));return InteractionResult.CONSUME;
  }
  if(time-q.created()<5)return InteractionResult.CONSUME;
  if(!clear(p,level,q)){preview(p,q,true,false);p.sendSystemMessage(Component.literal("Placement blocked. Clear the preview area or sneak-use somewhere else."));return InteractionResult.CONSUME;}
  var snapshots=new ArrayList<BlockSnapshot>();
  try{
   for(var piece:StarterLayout.create(q.origin(),q.rotation())){var snapshot=BlockSnapshot.create(level.dimension(),level,piece.pos());snapshots.add(snapshot);if(!level.setBlock(piece.pos(),piece.state(),3))throw new IllegalStateException("Block placement failed");if(EventHooks.onBlockPlace(p,snapshot,Direction.UP))throw new IllegalStateException("Area is protected");}
  }catch(Exception failure){Collections.reverse(snapshots);for(var snapshot:snapshots)snapshot.restore();preview(p,q,true,false);p.sendSystemMessage(Component.literal("Nothing placed: "+failure.getMessage()));return InteractionResult.CONSUME;}
  pending.remove(p.getUUID());preview(p,q,false,true);stack.shrink(1);p.sendSystemMessage(Component.literal("Kinker forge and stations built. Add lava to the fuel tank before smelting."));return InteractionResult.CONSUME;
 }
}
