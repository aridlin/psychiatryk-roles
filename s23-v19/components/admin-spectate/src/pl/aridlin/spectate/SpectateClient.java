package pl.aridlin.spectate;
import net.minecraft.client.*;
import net.minecraft.client.gui.*;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.client.event.RenderGuiEvent;
public final class SpectateClient {
 public static SpectateData data=SpectateData.stop();static CameraType oldView;
 public static void receive(SpectateData value){var mc=Minecraft.getInstance();if(!data.active()&&value.active()){oldView=mc.options.getCameraType();}data=value;if(!value.active()){if(mc.player!=null)mc.setCameraEntity(mc.player);if(oldView!=null)mc.options.setCameraType(oldView);oldView=null;if(mc.screen instanceof MirrorScreen)mc.setScreen(null);return;}if(value.view()>=0)mc.options.setCameraType(value.view()==0?CameraType.FIRST_PERSON:CameraType.THIRD_PERSON_BACK);if(!value.slots().isEmpty()){if(mc.screen==null)mc.setScreen(new MirrorScreen());}else if(mc.screen instanceof MirrorScreen)mc.setScreen(null);}
 public static net.minecraft.client.player.AbstractClientPlayer target(){var mc=Minecraft.getInstance();if(!data.active()||mc.level==null)return null;for(var p:mc.level.players())if(p.getUUID().equals(data.target()))return p;return null;}
 @EventBusSubscriber(modid="goplanska_spectate",value=Dist.CLIENT)
 public static class Events {
 @SubscribeEvent public static void hideSpectatorHead(net.neoforged.neoforge.client.event.RenderPlayerEvent.Pre e){if(e.getEntity().isSpectator())e.setCanceled(true);}
 @SubscribeEvent public static void attach(net.neoforged.neoforge.client.event.ClientTickEvent.Post e){var mc=Minecraft.getInstance();if(!data.active()||mc.player==null)return;var target=target();if(target!=null&&mc.getCameraEntity()!=target){mc.setCameraEntity(target);org.slf4j.LoggerFactory.getLogger("GoplanskaSpectate").info("Spectate camera attached to {}",target.getGameProfile().getName());}}
 @SubscribeEvent public static void hud(RenderGuiEvent.Post e){var mc=Minecraft.getInstance();if(!data.active()||mc.player==null)return;var g=e.getGuiGraphics();int x=(g.guiWidth()-182)/2,y=g.guiHeight()-22;g.fill(x,y,x+182,y+22,0xc0101010);for(int i=0;i<9&&i<data.inventory().size();i++){if(i==data.selected())g.fill(x+1+i*20,y+1,x+21+i*20,y+21,0xffaaaaaa);g.renderItem(data.inventory().get(i),x+3+i*20,y+3);g.renderItemDecorations(mc.font,data.inventory().get(i),x+3+i*20,y+3);}g.drawString(mc.font,"Spectating "+data.name()+" · /spectate stop",8,8,0xffffffff,true);}}
 static final class MirrorScreen extends Screen {
  MirrorScreen(){super(Component.literal("Spectated container"));}
  @Override public boolean isPauseScreen(){return false;}
  @Override public void onClose(){minecraft.setScreen(null);}
  @Override public void render(GuiGraphics g,int mouseX,int mouseY,float partial){int cols=9,rows=(data.slots().size()+8)/9,w=cols*18+16,h=rows*18+30,x=(width-w)/2,y=(height-h)/2;g.fill(x,y,x+w,y+h,0xee242424);g.drawString(font,data.name()+" — "+data.menu()+" (view only)",x+8,y+6,0xffffffff,false);for(int i=0;i<data.slots().size();i++){int sx=x+8+(i%cols)*18,sy=y+22+(i/cols)*18;g.fill(sx,sy,sx+17,sy+17,0xff656565);var item=data.slots().get(i);g.renderItem(item,sx,sy);g.renderItemDecorations(font,item,sx,sy);if(mouseX>=sx&&mouseX<sx+18&&mouseY>=sy&&mouseY<sy+18&&!item.isEmpty())g.renderTooltip(font,item,mouseX,mouseY);}super.render(g,mouseX,mouseY,partial);}
 }
}
