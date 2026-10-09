package pl.aridlin.psychiatrykroles.runtime.client;

import com.google.gson.JsonParser;
import java.util.HashMap;
import java.util.LinkedHashMap;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderGuiEvent;
import pl.aridlin.psychiatrykroles.runtime.HudSchema;

/** Bounded scene renderer. Parsing, item construction and image resolution happen on update, not render. */
@EventBusSubscriber(modid="psychiatryk_runtime",value=Dist.CLIENT)
public final class HudClient {
    private HudClient() {}
    private static final LinkedHashMap<String,HudSchema.Scene> scenes=new LinkedHashMap<>();
    private static PreparedScene[] drawList=new PreparedScene[0];
    private static Object connection;
    private static final int TEXT=0,RECT=1,PROGRESS=2,ITEM=3,IMAGE=4;
    private static final long CLOCK_ORIGIN=System.nanoTime();
    private static final double[] variables=new double[VisualExpressions.VARIABLE_COUNT];
    private record PreparedNode(int type,double x,double y,double w,double h,Component text,int color,int background,
                                double value,ItemStack item,ResourceLocation image,int textureWidth,int textureHeight,
                                VisualExpressions.Expression visible,VisualExpressions.Expression animatedX,
                                VisualExpressions.Expression animatedY,VisualExpressions.Expression animatedValue) {}
    private record PreparedScene(String id,long startedAt,PreparedNode[] nodes) {}

    /** Returns false for ordinary runtime menu snapshots. HUD errors are contained client-side. */
    public static boolean tryAccept(String raw){
        if(!HudSchema.isHud(raw))return false;
        try{
            var message=HudSchema.parse(raw);var nextConnection=Minecraft.getInstance().getConnection();
            if(connection!=nextConnection){scenes.clear();drawList=new PreparedScene[0];connection=nextConnection;}
            var staged=new LinkedHashMap<>(scenes);
            if(message.op().equals("clear"))staged.remove(message.id());
            else{
                if(!staged.containsKey(message.id())&&staged.size()>=HudSchema.MAX_SCENES)
                    throw new IllegalArgumentException("HUD scene limit");
                staged.put(message.id(),message.scene());
            }
            HudSchema.validateCollection(staged.values());
            var prepared=compile(staged,message.op().equals("replace")?message.id():null);
            scenes.clear();scenes.putAll(staged);drawList=prepared;
        }catch(RuntimeException invalid){
            var player=Minecraft.getInstance().player;
            if(player!=null)player.displayClientMessage(Component.literal("Server HUD update rejected safely."),false);
        }
        return true;
    }
    /** Registered as an asset-ready hook; refreshes image references before the asset ACK. */
    public static void assetsChanged(){rebuild();}
    private static void rebuild(){drawList=compile(scenes,null);}
    private static PreparedScene[] compile(LinkedHashMap<String,HudSchema.Scene> source,String changedId){
        var oldOrigins=new HashMap<String,Long>();
        for(var old:drawList)oldOrigins.put(old.id(),old.startedAt());
        var result=new PreparedScene[source.size()];int index=0;
        for(var scene:source.values()){
            var prepared=new PreparedNode[scene.nodes().size()];int n=0;
            for(var node:scene.nodes())prepared[n++]=prepare(node);
            long origin=scene.id().equals(changedId)?System.nanoTime():
                oldOrigins.getOrDefault(scene.id(),System.nanoTime());
            result[index++]=new PreparedScene(scene.id(),origin,prepared);
        }
        return result;
    }
    private static PreparedNode prepare(HudSchema.Node n){
        int type=switch(n.type()){case "text"->TEXT;case "rect"->RECT;case "progress"->PROGRESS;
            case "item"->ITEM;case "image"->IMAGE;default->throw new IllegalArgumentException("Unknown HUD node");};
        Component text=type==TEXT?Component.literal(n.text()):null;
        ItemStack stack=ItemStack.EMPTY;
        if(type==ITEM){var id=ResourceLocation.parse(n.item());
            if(BuiltInRegistries.ITEM.containsKey(id))stack=new ItemStack(BuiltInRegistries.ITEM.get(id));
        }
        ResourceLocation image=null;int width=0,height=0;
        if(type==IMAGE){image=AssetClient.texture(n.asset()).orElse(null);
            var dimensions=AssetClient.dimensions(n.asset()).orElse(null);
            if(dimensions!=null){width=dimensions.width();height=dimensions.height();}
        }
        var budget=new VisualExpressions.Budget();
        return new PreparedNode(type,n.x(),n.y(),n.w(),n.h(),text,n.color(),n.background(),n.value(),stack,image,width,height,
            expression(n.visibleWhen(),budget),expression(n.xRule(),budget),
            expression(n.yRule(),budget),expression(n.valueRule(),budget));
    }
    private static VisualExpressions.Expression expression(String raw,VisualExpressions.Budget budget){
        return raw==null||raw.isEmpty()?null:VisualExpressions.compile(JsonParser.parseString(raw),budget,
            HudSchema.HUD_VARIABLES,SignalClient::expression);
    }
    @SubscribeEvent public static void render(RenderGuiEvent.Post event){
        var active=drawList;if(active.length==0)return;
        var mc=Minecraft.getInstance();if(mc.level==null||mc.player==null||mc.getConnection()==null)return;
        GuiGraphics graphics=event.getGuiGraphics();int screenWidth=mc.getWindow().getGuiScaledWidth();
        int screenHeight=mc.getWindow().getGuiScaledHeight();
        long now=System.nanoTime();
        variables[0]=Math.min(1_000_000.0,(now-CLOCK_ORIGIN)/1_000_000_000.0);
        variables[1]=mc.player.getDeltaMovement().horizontalDistance();
        variables[2]=mc.player.getYRot();variables[3]=mc.player.getXRot();
        variables[12]=mc.player.onGround()?0:1;
        variables[13]=mc.player.getDeltaMovement().y;
        variables[17]=mc.player.getHealth();variables[18]=mc.player.getMaxHealth();
        variables[19]=mc.player.getFoodData().getFoodLevel();
        variables[20]=screenWidth;variables[21]=screenHeight;
        for(int s=0;s<active.length;s++){
            variables[22]=Math.min(1_000_000.0,(now-active[s].startedAt())/1_000_000_000.0);
            for(int i=0;i<active[s].nodes.length;i++){
            var node=active[s].nodes[i];
            if(!HudMotion.visible(node.visible,variables))continue;
            int x=(int)Math.round(HudMotion.position(node.animatedX,node.x,node.w,variables)*screenWidth);
            int y=(int)Math.round(HudMotion.position(node.animatedY,node.y,node.h,variables)*screenHeight);
            int w=(int)Math.round(node.w*screenWidth),h=(int)Math.round(node.h*screenHeight);
            switch(node.type){
                case TEXT->graphics.drawString(mc.font,node.text,x,y,node.color,true);
                case RECT->{if(w>0&&h>0)graphics.fill(x,y,x+w,y+h,node.color);}
                case PROGRESS->{if(w>0&&h>0){graphics.fill(x,y,x+w,y+h,node.background);
                    int filled=(int)Math.round(w*HudMotion.progress(node.animatedValue,node.value,variables));
                    if(filled>0)graphics.fill(x,y,x+filled,y+h,node.color);}}
                case ITEM->{if(!node.item.isEmpty())graphics.renderItem(node.item,x,y);}
                case IMAGE->{if(node.image!=null&&node.textureWidth>0&&node.textureHeight>0&&w>0&&h>0)
                    graphics.blit(node.image,x,y,0.0F,0.0F,w,h,node.textureWidth,node.textureHeight);}
                default->{}
            }
            }
        }
    }
    @SubscribeEvent public static void tick(ClientTickEvent.Post ignored){
        var next=Minecraft.getInstance().getConnection();if(connection!=next){scenes.clear();drawList=new PreparedScene[0];connection=next;}
    }
}
