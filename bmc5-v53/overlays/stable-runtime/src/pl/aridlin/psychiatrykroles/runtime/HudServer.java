package pl.aridlin.psychiatrykroles.runtime;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

/** Server-authoritative replace/clear API; no client-originated HUD instructions. */
public final class HudServer {
    private static final Map<UUID,LinkedHashMap<String,HudSchema.Scene>> scenes=new HashMap<>();
    private static final Map<UUID,Set<String>> deferred=new HashMap<>();

    public static void replace(ServerPlayer player,HudSchema.Scene scene){
        HudSchema.validate(scene);
        for(var node:scene.nodes()){
            if(node.type().equals("item"))Schema.require(BuiltInRegistries.ITEM.containsKey(ResourceLocation.parse(node.item())),"Unknown HUD item");
            if(node.type().equals("image"))Schema.require(AssetServer.hasPng(node.asset()),"Missing HUD image asset");
        }
        var items=scenes.get(player.getUUID());
        var staged=items==null?new LinkedHashMap<String,HudSchema.Scene>():new LinkedHashMap<>(items);
        var frozen=new HudSchema.Scene(scene.id(),java.util.List.copyOf(scene.nodes()));staged.put(scene.id(),frozen);
        HudSchema.validateCollection(staged.values());scenes.put(player.getUUID(),staged);
        if(canSend(player,frozen)){send(player,HudSchema.replace(frozen));removeDeferred(player.getUUID(),scene.id());}
        else deferred.computeIfAbsent(player.getUUID(),ignored->new java.util.HashSet<>()).add(scene.id());
    }
    public static void clear(ServerPlayer player,String id){
        HudSchema.id(id);var items=scenes.get(player.getUUID());if(items!=null){items.remove(id);if(items.isEmpty())scenes.remove(player.getUUID());}
        removeDeferred(player.getUUID(),id);
        if(hudSupported(player))send(player,HudSchema.clear(id));
    }
    public static void clearAll(ServerPlayer player){
        var items=scenes.remove(player.getUUID());deferred.remove(player.getUUID());
        if(items!=null&&hudSupported(player))for(String id:items.keySet())send(player,HudSchema.clear(id));
    }
    public static void resend(ServerPlayer player){
        var items=scenes.get(player.getUUID());if(items==null)return;
        for(var scene:items.values()){
            if(canSend(player,scene)){send(player,HudSchema.replace(scene));removeDeferred(player.getUUID(),scene.id());}
            else deferred.computeIfAbsent(player.getUUID(),ignored->new java.util.HashSet<>()).add(scene.id());
        }
    }
    private static boolean canSend(ServerPlayer player,HudSchema.Scene scene){
        if(!hudSupported(player))return false;
        for(var node:scene.nodes())if(node.type().equals("image")&&!AssetServer.ready(player))return false;
        return true;
    }
    /** AssetNetwork identifies the client generation that also understands HUD snapshots. */
    private static boolean hudSupported(ServerPlayer player){
        return RuntimeNetwork.supported(player)&&AssetNetwork.supported(player);
    }
    private static void send(ServerPlayer player,HudSchema.Message message){
        if("replace".equals(message.op())&&!SignalServer.acknowledged(player))
            message=HudSchema.replace(HudSchema.legacyScene(message.scene()));
        String raw=HudSchema.JSON.toJson(message);HudSchema.parse(raw);
        PacketDistributor.sendToPlayer(player,new RuntimeNetwork.Snapshot(raw));
    }
    private static void removeDeferred(UUID player,String id){
        var ids=deferred.get(player);if(ids!=null){ids.remove(id);if(ids.isEmpty())deferred.remove(player);}
    }
    @SubscribeEvent public void logout(PlayerEvent.PlayerLoggedOutEvent event){
        scenes.remove(event.getEntity().getUUID());deferred.remove(event.getEntity().getUUID());
    }
    @SubscribeEvent public void tick(ServerTickEvent.Post event){
        if(event.getServer().getTickCount()%20!=0)return;
        for(UUID id:java.util.List.copyOf(deferred.keySet())){
            var player=event.getServer().getPlayerList().getPlayer(id);
            if(player==null){deferred.remove(id);continue;}
            var items=scenes.get(id);if(items==null){deferred.remove(id);continue;}
            for(String sceneId:java.util.List.copyOf(deferred.get(id))){
                var scene=items.get(sceneId);if(scene==null){removeDeferred(id,sceneId);continue;}
                if(canSend(player,scene)){send(player,HudSchema.replace(scene));removeDeferred(id,sceneId);}
            }
        }
    }
}
