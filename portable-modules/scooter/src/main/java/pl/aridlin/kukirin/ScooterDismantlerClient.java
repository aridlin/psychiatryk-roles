package pl.aridlin.kukirin;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
public final class ScooterDismantlerClient {
 public static void open(ScooterDismantler data){Minecraft.getInstance().setScreen(new Screen(Component.literal("Scooter workstation")){
  protected void init(){String[] actions={"chest","jukebox","noteblock","netherite","saddle","infinite","star","trim","dye","enchants","name"};for(int i=0;i<actions.length;i++){String action=actions[i];var button=addRenderableWidget(Button.builder(Component.literal("Remove "+switch(action){case "noteblock"->"note block";case "netherite"->"netherite flight";case "infinite"->"infinite battery";case "star"->"Nether Star binding";case "enchants"->"enchantments";default->action;}),b->{send(action);onClose();}).bounds(width/2-150+(i%2)*154,height/2-110+(i/2)*23,146,20).build());var player=Minecraft.getInstance().player;button.active=player!=null&&ScooterDismantler.remove(player.getItemInHand(data.hand()),action)!=null;}addRenderableWidget(Button.builder(Component.literal("Destroy scooter..."),b->{Minecraft.getInstance().setScreen(new net.minecraft.client.gui.screens.ConfirmScreen(new it.unimi.dsi.fastutil.booleans.BooleanConsumer(){public void accept(boolean ok){if(ok)send("destroy");Minecraft.getInstance().setScreen(null);}public void accept(Boolean ok){accept(ok.booleanValue());}},Component.literal("Destroy scooter?"),Component.literal("This permanently destroys the scooter and everything stored inside it.")));}).bounds(width/2-150,height/2+35,146,20).build());addRenderableWidget(Button.builder(Component.literal("Keep it / Close"),b->onClose()).bounds(width/2+4,height/2+35,146,20).build());}
  void send(String action){net.neoforged.neoforge.network.PacketDistributor.sendToServer(new ScooterDismantler(data.pos(),data.hand(),action));}
  public void render(GuiGraphics g,int x,int y,float partial){renderBackground(g,x,y,partial);super.render(g,x,y,partial);g.drawCenteredString(font,title,width/2,height/2-140,0xffffa537);g.drawCenteredString(font,"Removed materials and stored items return to your inventory.",width/2,height/2+65,0xffeeeeee);g.drawCenteredString(font,"Enchant removal gives no XP refund.",width/2,height/2+78,0xffeeeeee);}
  public boolean isPauseScreen(){return false;}
 });}
}
