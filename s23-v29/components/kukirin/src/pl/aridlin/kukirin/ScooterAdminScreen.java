package pl.aridlin.kukirin;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.components.*;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
public final class ScooterAdminScreen extends Screen {
 private final double[] values=new double[5];private final double[] low={.25,.5,.03,.05,0},high={2,1,.4,1,1};private final String[] names={"Steering speed multiplier","Normal tyre grip","Ctrl drift grip (lower = more slide)","Grip recovery (higher = faster)","Tyre scrub volume"};
 public ScooterAdminScreen(ScooterTuning.Values v){super(Component.literal("Server scooter tuning"));set(v);}
 public static void open(ScooterTuning.Values v){net.minecraft.client.Minecraft.getInstance().setScreen(new ScooterAdminScreen(v));}
 private void set(ScooterTuning.Values v){values[0]=v.steering();values[1]=v.normalGrip();values[2]=v.driftGrip();values[3]=v.recovery();values[4]=v.tyreVolume();}
 @Override protected void init(){int width=Math.min(380,this.width-32),left=(this.width-width)/2,y=Math.max(32,(height-210)/2);for(int i=0;i<5;i++){final int n=i;addRenderableWidget(new AbstractSliderButton(left,y+i*28,width,24,Component.empty(),(values[i]-low[i])/(high[i]-low[i])){{updateMessage();}protected void updateMessage(){setMessage(Component.literal(names[n]+": "+String.format(java.util.Locale.ROOT,"%.2f",values[n])));}protected void applyValue(){values[n]=low[n]+value*(high[n]-low[n]);updateMessage();}});}
 addRenderableWidget(Button.builder(Component.literal("Apply live & save"),b->{var v=new ScooterTuning.Values(values[0],values[1],values[2],values[3],values[4]);if(v.valid())net.neoforged.neoforge.network.PacketDistributor.sendToServer(new ScooterAdmin.Edit(true,v));}).bounds(left,y+148,width/2-4,24).build());addRenderableWidget(Button.builder(Component.literal("Defaults"),b->{set(ScooterTuning.DEFAULT);rebuildWidgets();}).bounds(left+width/2+4,y+148,width/2-4,24).build());addRenderableWidget(Button.builder(Component.literal("Done"),b->onClose()).bounds(left,y+176,width,24).build());}
 @Override public void render(GuiGraphics g,int mx,int my,float tick){renderBackground(g,mx,my,tick);super.render(g,mx,my,tick);g.drawCenteredString(font,title,width/2,12,0xffffa537);g.drawCenteredString(font,Component.literal("Applies to everyone; no restart needed. Ctrl enables drifting."),width/2,height-15,0xffdddddd);}
 @Override public boolean isPauseScreen(){return false;}
}
