package pl.aridlin.psychiatrykroles;
import java.util.*;
import net.minecraft.core.*;
import net.minecraft.core.registries.Registries;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.*;
import net.minecraft.world.item.*;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.server.level.*;
import net.minecraft.network.chat.Component;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.registries.RegisterEvent;
import net.neoforged.neoforge.network.PacketDistributor;
@EventBusSubscriber(modid="psychiatryk_roles")
public final class MarkingTorches {
 public static TorchBlock BLOCK;
 public static BlockItem ITEM;
 private static TorchBlock createBlock(){return new TorchBlock(ParticleTypes.END_ROD,BlockBehaviour.Properties.ofFullCopy(Blocks.TORCH).lightLevel(s->14)){
 @Override public void setPlacedBy(Level level,BlockPos pos,BlockState state,LivingEntity placer,ItemStack stack){super.setPlacedBy(level,pos,state,placer,stack);if(placer instanceof ServerPlayer p){var a=MarkAreaData.get(p.getServer()).place(p.getUUID(),level.dimension().location().toString(),pos);p.displayClientMessage(Component.literal(a.b()==null?"First area corner marked. Place another marking torch at the opposite corner (up to 128 blocks away).":"Building area marked. Breaking either torch clears it."),true);}}
 };}

 @EventBusSubscriber(modid="psychiatryk_roles",bus=EventBusSubscriber.Bus.MOD)
 public static final class Registration {
 @SubscribeEvent public static void registry(RegisterEvent e){e.register(Registries.BLOCK,h->h.register(net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("psychiatryk_roles","marking_torch"),BLOCK=createBlock()));e.register(Registries.ITEM,h->h.register(net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("psychiatryk_roles","marking_torch"),ITEM=new BlockItem(BLOCK,new Item.Properties())));}
 @SubscribeEvent public static void packets(net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent e){e.registrar("1").playToClient(MarkSync.TYPE,MarkSync.CODEC,(p,c)->c.enqueueWork(()->pl.aridlin.partymarkers.MarkChams.receive(p)));}
 }
 @SubscribeEvent public static void breaking(net.neoforged.neoforge.event.level.BlockEvent.BreakEvent e){if(e.getState().is(BLOCK)&&e.getLevel() instanceof ServerLevel level)MarkAreaData.get(level.getServer()).remove(level.dimension().location().toString(),e.getPos());}
 @SubscribeEvent public static void tick(net.neoforged.neoforge.event.tick.ServerTickEvent.Post e){var server=e.getServer();if(server.getTickCount()%10!=0)return;var data=MarkAreaData.get(server);for(var a:List.copyOf(data.areas)){var level=server.getLevel(net.minecraft.resources.ResourceKey.create(Registries.DIMENSION,net.minecraft.resources.ResourceLocation.parse(a.dimension())));if(level!=null&&((level.hasChunkAt(a.a())&&!level.getBlockState(a.a()).is(BLOCK))||(a.b()!=null&&level.hasChunkAt(a.b())&&!level.getBlockState(a.b()).is(BLOCK))))data.remove(a.dimension(),a.a());}
 var chalk=RoleData.get(server).activeChalkMarkers(System.currentTimeMillis());for(var p:server.getPlayerList().getPlayers()){if(!p.connection.hasChannel(MarkSync.TYPE))continue;String dim=p.level().dimension().location().toString();var list=new ArrayList<MarkSync.Mark>();for(var a:data.areas){if(!a.dimension().equals(dim))continue;BlockPos b=a.b()==null?a.a():a.b();double x=Math.min(a.a().getX(),b.getX()),y=Math.min(a.a().getY(),b.getY()),z=Math.min(a.a().getZ(),b.getZ()),X=Math.max(a.a().getX(),b.getX())+1,Y=Math.max(a.a().getY(),b.getY())+1,Z=Math.max(a.a().getZ(),b.getZ())+1;if(p.distanceToSqr((x+X)/2,(y+Y)/2,(z+Z)/2)>256*256)continue;list.add(new MarkSync.Mark(a.id(),x,y,z,X,Y,Z,0xffffdd55,false));if(list.size()>=128)break;}
 for(var own:chalk){var m=own.marker();if(!m.dimension().equals(dim)||p.distanceToSqr(m.x(),m.y(),m.z())>256*256)continue;UUID id=UUID.nameUUIDFromBytes((own.owner()+":"+m.x()+":"+m.y()+":"+m.z()+":"+m.expiresAt()).getBytes(java.nio.charset.StandardCharsets.UTF_8));list.add(new MarkSync.Mark(id,m.x()-.22,m.y()-.22,m.z()-.22,m.x()+.22,m.y()+.22,m.z()+.22,0xffffffff,true));if(list.size()>=256)break;}
 PacketDistributor.sendToPlayer(p,new MarkSync(List.copyOf(list)));}
 }
}
