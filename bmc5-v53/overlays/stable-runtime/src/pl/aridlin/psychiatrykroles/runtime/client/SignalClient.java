package pl.aridlin.psychiatrykroles.runtime.client;

import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import pl.aridlin.psychiatrykroles.runtime.RuntimeNetwork;
import pl.aridlin.psychiatrykroles.runtime.SignalSchema;
import pl.aridlin.psychiatrykroles.runtime.SignalValues;

/** Client-owned state is cleared on every connection change, including disconnect. */
@EventBusSubscriber(modid="psychiatryk_runtime",value=Dist.CLIENT)
public final class SignalClient {
    private SignalClient() {}
    private static final SignalValues values=new SignalValues();
    private static Object connection;
    private static boolean helloSent;
    private static long lastHelloAttempt;
    public static VisualExpressions.Expression expression(String name){
        String id=SignalSchema.variable(name);return ignored->values.numeric(id);
    }
    public static boolean tryAccept(String raw){
        if(!SignalSchema.isSignal(raw))return false;
        Object next=Minecraft.getInstance().getConnection();
        if(connection!=next){values.clear();connection=next;helloSent=false;}
        try{values.apply(SignalSchema.parse(raw));}catch(RuntimeException rejected){/* Preserve the last valid state. */}
        return true;
    }
    @SubscribeEvent public static void tick(ClientTickEvent.Post ignored){
        var mc=Minecraft.getInstance();var next=mc.getConnection();
        if(connection!=next){values.clear();connection=next;helloSent=false;lastHelloAttempt=0;}
        if(next==null||mc.player==null||helloSent)return;
        long now=System.nanoTime();if(lastHelloAttempt!=0&&now-lastHelloAttempt<1_000_000_000L)return;
        lastHelloAttempt=now;
        try{if(next.hasChannel(RuntimeNetwork.Capabilities.TYPE)){
            PacketDistributor.sendToServer(new RuntimeNetwork.Capabilities(SignalSchema.hello()));helloSent=true;
        }}catch(RuntimeException notReady){/* Retry after a second if negotiation is still settling. */}
    }
}
