package pl.aridlin.psychiatrykroles.runtime;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

/** Per-player server-authored signal state. Calls to this API belong on the server thread. */
public final class SignalServer {
    private static final class PlayerState {
        final Object connection;final SignalState values=new SignalState();final SignalRate rate=new SignalRate();boolean acknowledged;
        PlayerState(Object connection){this.connection=connection;}
    }
    private static final Map<UUID,PlayerState> players=new HashMap<>();
    private static PlayerState state(ServerPlayer player){
        UUID id=player.getUUID();PlayerState old=players.get(id);
        if(old!=null&&old.connection==player.connection)return old;
        var fresh=new PlayerState(player.connection);players.put(id,fresh);return fresh;
    }
    public static void setNumber(ServerPlayer player,String id,double value){state(player).values.set(SignalSchema.valueNumber(id,value));}
    public static void setBoolean(ServerPlayer player,String id,boolean value){state(player).values.set(SignalSchema.valueBoolean(id,value));}
    public static void setText(ServerPlayer player,String id,String value){state(player).values.set(SignalSchema.valueText(id,value));}
    public static void clear(ServerPlayer player,String id){state(player).values.set(SignalSchema.clear(id));}
    public static void clearAll(ServerPlayer player){state(player).values.clearAll();}
    public static boolean acknowledged(ServerPlayer player){
        PlayerState state=players.get(player.getUUID());return state!=null&&state.connection==player.connection&&state.acknowledged;
    }
    public static void handshake(ServerPlayer player,String raw){
        try{SignalSchema.parseHello(raw);}catch(RuntimeException invalid){return;}
        if(!RuntimeNetwork.supported(player)||!RuntimeNetwork.signalsSupported(player))return;
        PlayerState state=state(player);if(state.acknowledged)return;
        state.acknowledged=true;state.values.resetDelivery();HudServer.resend(player);
    }
    @SubscribeEvent public void logout(PlayerEvent.PlayerLoggedOutEvent event){players.remove(event.getEntity().getUUID());}
    /** At most five 2 KiB packets in any rolling second per player. */
    @SubscribeEvent public void tick(ServerTickEvent.Post event){
        if(event.getServer().getTickCount()%4!=0)return;
        for(var entry:java.util.List.copyOf(players.entrySet())){
            var player=event.getServer().getPlayerList().getPlayer(entry.getKey());
            if(player==null||player.connection!=entry.getValue().connection){players.remove(entry.getKey());continue;}
            var state=entry.getValue();if(!state.acknowledged)continue;
            if(!RuntimeNetwork.supported(player)||!RuntimeNetwork.signalsSupported(player))continue;
            long now=System.nanoTime();if(!state.rate.available(now))continue;
            SignalSchema.Message message=state.values.nextMessage();if(message==null)continue;
            String wire=SignalSchema.encode(message);
            state.rate.record(now);
            try{PacketDistributor.sendToPlayer(player,new RuntimeNetwork.Snapshot(wire));}
            catch(RuntimeException disconnected){state.values.resetDelivery();}
        }
    }
}
