package pl.aridlin.kukirin;
import net.minecraft.client.gui.components.*;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import java.util.function.*;
public final class ScooterSettingsScreen extends Screen {
 private final Runnable back;private int columnX,columnWidth;
 public ScooterSettingsScreen(){this(null);}
 public ScooterSettingsScreen(Runnable back){super(Component.literal("Scooter settings"));this.back=back;}
 @Override public void onClose(){if(back!=null)back.run();else super.onClose();}
 @Override protected void init(){var o=ScooterClientOptions.get();int y=Math.max(28,height/2-78);columnWidth=Math.min(250,(width-36)/2);columnX=width/2-columnWidth-6;toggle("Mouse steering",()->ScooterClientOptions.get().mouseSteering,v->ScooterClientOptions.get().mouseSteering=v,y);toggle("Headlights",()->ScooterClientOptions.get().headlightsEnabled,v->ScooterClientOptions.get().headlightsEnabled=v,y+26);toggle("Speedometer",()->ScooterClientOptions.get().speedometerEnabled,v->ScooterClientOptions.get().speedometerEnabled=v,y+52);toggle("Camera effects",()->ScooterClientOptions.get().effectsEnabled,v->ScooterClientOptions.get().effectsEnabled=v,y+78);toggle("Sounds",()->ScooterClientOptions.get().audioEnabled,v->ScooterClientOptions.get().audioEnabled=v,y+104);columnX=width/2+6;slider("FOV boost",o.fovBoostDegrees/25,v->ScooterClientOptions.get().fovBoostDegrees=(float)(v*25),y);slider("Camera lean",o.cameraLeanDegrees/5,v->ScooterClientOptions.get().cameraLeanDegrees=(float)(v*5),y+26);slider("Motor volume",o.motorVolume,v->ScooterClientOptions.get().motorVolume=(float)v,y+52);slider("Wind volume",o.windVolume,v->ScooterClientOptions.get().windVolume=(float)v,y+78);slider("Music volume",o.musicVolume,v->ScooterClientOptions.get().musicVolume=(float)v,y+104);addRenderableWidget(Button.builder(Component.literal("Done"),b->onClose()).bounds(width/2-100,y+140,200,20).build());}
 private void toggle(String name,BooleanSupplier get,Consumer<Boolean> set,int y){addRenderableWidget(Button.builder(label(name,get.getAsBoolean()),b->{set.accept(!get.getAsBoolean());b.setMessage(label(name,get.getAsBoolean()));ScooterClientOptions.save();}).bounds(columnX,y,columnWidth,22).build());}
 private Component label(String name,boolean value){return Component.literal(name+": "+(value?"ON":"OFF"));}
 private void slider(String name,double initial,DoubleConsumer set,int y){addRenderableWidget(new AbstractSliderButton(columnX,y,columnWidth,22,Component.literal(name+": "+Math.round(initial*100)+"%"),initial){protected void updateMessage(){setMessage(Component.literal(name+": "+Math.round(value*100)+"%"));}protected void applyValue(){set.accept(value);ScooterClientOptions.save();}});}
 @Override public void render(GuiGraphics g,int x,int y,float partial){renderBackground(g,x,y,partial);super.render(g,x,y,partial);g.drawCenteredString(font,title,width/2,Math.max(9,height/2-99),0xffffa537);}
 @Override public boolean isPauseScreen(){return false;}
}
