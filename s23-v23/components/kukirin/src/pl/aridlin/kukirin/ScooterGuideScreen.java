package pl.aridlin.kukirin;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;
public final class ScooterGuideScreen extends Screen {
 final Runnable back;int page,scroll,maxScroll;
 public ScooterGuideScreen(Runnable back){super(Component.translatable("scooter.menu.guide"));this.back=back;}
 @Override protected void init(){int left=width/2-132;String[] tabs={"basics","upgrades","enchants"};for(int i=0;i<3;i++){final int p=i;addRenderableWidget(Button.builder(Component.translatable("scooter.guide.tab."+tabs[i]),b->{page=p;scroll=0;}).bounds(left+i*89,32,86,20).build());}addRenderableWidget(Button.builder(Component.translatable("gui.back"),b->onClose()).bounds(width/2-70,height-28,140,20).build());}
 @Override public void render(net.minecraft.client.gui.GuiGraphics g,int mx,int my,float partial){renderBackground(g,mx,my,partial);super.render(g,mx,my,partial);g.drawCenteredString(font,title,width/2,12,0xffffa537);int left=Math.max(16,width/2-200),wrap=Math.min(400,width-32),y=64;g.enableScissor(left,60,left+wrap,height-35);int count=page==0?9:page==1?9:11;String category=page==0?"basics":page==1?"upgrades":"enchants";for(int i=0;i<count;i++){var text=Component.translatable("scooter.guide."+category+"."+i);for(var line:font.split(text,wrap)){g.drawString(font,line,left,y-scroll,0xffeeeeee);y+=11;}y+=5;}g.disableScissor();maxScroll=Math.max(0,y-(height-35));if(maxScroll>0)g.drawString(font,Component.translatable("scooter.guide.scroll"),width-100,height-40,0xffaaaaaa);}
 @Override public boolean mouseScrolled(double x,double y,double horizontal,double vertical){scroll=Math.clamp(scroll-(int)(vertical*22),0,maxScroll);return true;}
 @Override public void onClose(){back.run();}
 @Override public boolean isPauseScreen(){return false;}
}
