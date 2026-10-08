package pl.aridlin.psychiatrykroles.runtime.client;

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
    private record PreparedNode(int type,double x,double y,double w,double h,Component text,int color,int background,
                                double value,ItemStack item,ResourceLocation image,int textureWidth,int textureHeight) {}
    private record PreparedScene(PreparedNode[] nodes) {}

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
            HudSchema.validateCollection(staged.values());var prepared=compile(staged);
            scenes.clear();scenes.putAll(staged);drawList=prepared;
        }catch(RuntimeException invalid){
            var player=Minecraft.getInstance().player;
            if(player!=null)player.displayClientMessage(Component.literal("Server HUD update rejected safely."),false);
        }
        return true;
    }
    /** Registered as an asset-ready hook; refreshes image references before the asset ACK. */
    public static void assetsChanged(){rebuild();}
    private static void rebuild(){drawList=compile(scenes);}
    private static PreparedScene[] compile(LinkedHashMap<String,HudSchema.Scene> source){
        var result=new PreparedScene[source.size()];int index=0;
        for(var scene:source.values()){
            var prepared=new PreparedNode[scene.nodes().size()];int n=0;
            for(var node:scene.nodes())prepared[n++]=prepare(node);
            result[index++]=new PreparedScene(prepared);
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
        return new PreparedNode(type,n.x(),n.y(),n.w(),n.h(),text,n.color(),n.background(),n.value(),stack,image,width,height);
    }
    @SubscribeEvent public static void render(RenderGuiEvent.Post event){
        var active=drawList;if(active.length==0)return;
        var mc=Minecraft.getInstance();if(mc.level==null||mc.getConnection()==null)return;
        GuiGraphics graphics=event.getGuiGraphics();int screenWidth=mc.getWindow().getGuiScaledWidth();
        int screenHeight=mc.getWindow().getGuiScaledHeight();
        for(int s=0;s<active.length;s++)for(int i=0;i<active[s].nodes.length;i++){
            var node=active[s].nodes[i];int x=(int)Math.round(node.x*screenWidth),y=(int)Math.round(node.y*screenHeight);
            int w=(int)Math.round(node.w*screenWidth),h=(int)Math.round(node.h*screenHeight);
            switch(node.type){
                case TEXT->graphics.drawString(mc.font,node.text,x,y,node.color,true);
                case RECT->{if(w>0&&h>0)graphics.fill(x,y,x+w,y+h,node.color);}
                case PROGRESS->{if(w>0&&h>0){graphics.fill(x,y,x+w,y+h,node.background);
                    int filled=(int)Math.round(w*node.value);if(filled>0)graphics.fill(x,y,x+filled,y+h,node.color);}}
                case ITEM->{if(!node.item.isEmpty())graphics.renderItem(node.item,x,y);}
                case IMAGE->{if(node.image!=null&&node.textureWidth>0&&node.textureHeight>0&&w>0&&h>0)
                    graphics.blit(node.image,x,y,0.0F,0.0F,w,h,node.textureWidth,node.textureHeight);}
                default->{}
            }
        }
    }
    @SubscribeEvent public static void tick(ClientTickEvent.Post ignored){
        var next=Minecraft.getInstance().getConnection();if(connection!=next){scenes.clear();drawList=new PreparedScene[0];connection=next;}
    }
}
