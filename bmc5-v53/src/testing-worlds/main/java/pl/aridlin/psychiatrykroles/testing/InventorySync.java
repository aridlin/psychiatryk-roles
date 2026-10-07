package pl.aridlin.psychiatrykroles.testing;

import java.util.Map;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

/** Optional mod-specific full snapshots after replacing attachment instances. Fail closed on API mismatch. */
public final class InventorySync {
 public static void send(ServerPlayer p){try{
  if(ModList.get().isLoaded("accessories"))Class.forName("io.wispforest.accessories.networking.client.SyncEntireContainer").getMethod("syncToAllTrackingAndSelf",ServerPlayer.class).invoke(null,p);
  if(ModList.get().isLoaded("curios")){
   var api=Class.forName("top.theillusivec4.curios.api.CuriosApi");var optional=(java.util.Optional<?>)api.getMethod("getCuriosInventory",LivingEntity.class).invoke(null,p);
   if(optional.isPresent()){var handler=optional.get();var type=Class.forName("top.theillusivec4.curios.api.type.capability.ICuriosItemHandler");var slots=type.getMethod("getCurios").invoke(handler);var packet=(CustomPacketPayload)Class.forName("top.theillusivec4.curios.common.network.server.sync.SPacketSyncCurios").getConstructor(int.class,Map.class).newInstance(p.getId(),slots);PacketDistributor.sendToPlayersTrackingEntityAndSelf(p,packet);}
  }
  if(ModList.get().isLoaded("framework")){
   var type=NeoForgeRegistries.ATTACHMENT_TYPES.get(ResourceLocation.parse("framework:data_holder"));
   if(type!=null&&p.hasData(type)){var holder=p.getData(type);var field=holder.getClass().getDeclaredField("dataMap");field.setAccessible(true);var entries=(Map<?,?>)field.get(holder);var mark=Class.forName("com.mrcrayfish.framework.entity.sync.DataEntry").getMethod("markForSync");for(Object entry:entries.values())mark.invoke(entry);}
  }
 }catch(ReflectiveOperationException error){throw new IllegalStateException("Installed inventory sync API changed; bank switch cannot complete safely",error);}}
 private InventorySync(){}
}
