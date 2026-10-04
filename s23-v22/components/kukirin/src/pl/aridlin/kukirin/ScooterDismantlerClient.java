package pl.aridlin.kukirin;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
public final class ScooterDismantlerClient {
 public static void open(ScooterDismantler data){Minecraft.getInstance().setScreen(new Screen(Component.literal("Dismantle bound scooter?")){
  protected void init(){addRenderableWidget(Button.builder(Component.literal("Destroy scooter"),b->{net.neoforged.neoforge.network.PacketDistributor.sendToServer(new ScooterDismantler(data.pos(),data.hand(),true));onClose();}).bounds(width/2-125,height/2+10,120,20).build());addRenderableWidget(Button.builder(Component.literal("Keep it"),b->onClose()).bounds(width/2+5,height/2+10,120,20).build());}
  public void render(GuiGraphics g,int x,int y,float partial){renderBackground(g,x,y,partial);super.render(g,x,y,partial);g.drawCenteredString(font,title,width/2,height/2-35,0xffffa537);g.drawCenteredString(font,"The scooter, Nether Star and enchants will be lost.",width/2,height/2-15,0xffeeeeee);}
  public boolean isPauseScreen(){return false;}
 });}
}
