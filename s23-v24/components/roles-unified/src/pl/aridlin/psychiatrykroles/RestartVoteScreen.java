package pl.aridlin.psychiatrykroles;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.PacketDistributor;
/** A non-pausing ballot; closing it never silently counts as a vote. */
public final class RestartVoteScreen extends Screen {
 RestartVoteUI.State state;
 RestartVoteScreen(RestartVoteUI.State state){super(Component.literal("Restart vote"));this.state=state;}
 public static void receive(RestartVoteUI.State state){var mc=Minecraft.getInstance();if(mc.screen instanceof RestartVoteScreen screen&&screen.state.id().equals(state.id())){if(!state.active())mc.setScreen(null);else screen.state=state;}else if(state.active()&&state.open())mc.setScreen(new RestartVoteScreen(state));}
 @Override protected void init(){boolean polish=minecraft.options.languageCode.startsWith("pl");addRenderableWidget(Button.builder(Component.literal(polish?"Tak":"Yes"),b->choose(true)).bounds(width/2-104,height/2+20,100,24).build());addRenderableWidget(Button.builder(Component.literal(polish?"Nie":"No"),b->choose(false)).bounds(width/2+4,height/2+20,100,24).build());}
 void choose(boolean yes){PacketDistributor.sendToServer(new RestartVoteUI.Choice(state.id(),yes));onClose();}
 @Override public void render(GuiGraphics g,int x,int y,float partial){renderBackground(g,x,y,partial);super.render(g,x,y,partial);g.drawCenteredString(font,title,width/2,height/2-64,0xffffdd55);g.drawCenteredString(font,Component.literal("Restart in "+state.duration()+" after approval"),width/2,height/2-40,0xffffffff);g.drawCenteredString(font,Component.literal("Yes: "+state.yes()+" / "+state.required()+" required   No: "+state.no()),width/2,height/2-20,0xffffffff);g.drawCenteredString(font,Component.literal(state.total()+" eligible players · "+state.remaining()+"s left"),width/2,height/2,0xffaaaaaa);}
 @Override public boolean isPauseScreen(){return false;}
}
