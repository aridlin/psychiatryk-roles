package pl.aridlin.kukirin;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.*;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.EquipmentSlot;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.api.distmarker.Dist;
@EventBusSubscriber(modid="goplanska_kukirin",value=Dist.CLIENT)
public final class ScooterInventoryButtons {
 private static final java.util.Map<InventoryScreen,java.util.List<Button>> BUTTONS=new java.util.WeakHashMap<>();
 @SubscribeEvent public static void init(net.neoforged.neoforge.client.event.ScreenEvent.Init.Post e){var mc=Minecraft.getInstance();if(!(e.getScreen() instanceof InventoryScreen screen)||mc.player==null||!(mc.player.getVehicle() instanceof Scooter scooter))return;
 int right=6;boolean sidebar=screen.getGuiLeft()>=116&&!screen.getRecipeBookComponent().isVisible();int w=sidebar?Math.min(120,screen.getGuiLeft()-12):Math.min(108,(screen.width-16)/2),x=sidebar?right:screen.width/2-w-4,y=sidebar?Math.max(6,screen.height/2-80):Math.max(4,(screen.height-166)/2-30);
 var buttons=new java.util.ArrayList<Button>();BUTTONS.put(screen,buttons);
 Runnable back=()->mc.setScreen(new InventoryScreen(mc.player));
 buttons.add(Button.builder(Component.translatable("scooter.menu.settings"),b->mc.setScreen(new ScooterSettingsScreen(back))).bounds(x,y,w,sidebar?32:24).tooltip(Tooltip.create(Component.translatable("scooter.inventory.settings_tip"))).build());
 buttons.add(Button.builder(Component.translatable("scooter.menu.help"),b->mc.setScreen(new ScooterGuideScreen(back))).bounds(sidebar?x:x+w+8,sidebar?y+38:y,w,sidebar?32:24).tooltip(Tooltip.create(Component.translatable("scooter.inventory.help_tip"))).build());
 int extrasY=sidebar?y+82:Math.min(screen.height-24,(screen.height+166)/2+4);int extraW=sidebar?w:Math.min(108,(screen.width-16)/2);
 var storage=Button.builder(Component.translatable("scooter.menu.storage"),b->net.neoforged.neoforge.network.PacketDistributor.sendToServer(new ScooterStorageOpen())).bounds(x,extrasY,extraW,22).build();storage.active=ScooterUpgradeRecipe.has(scooter.getItemBySlot(EquipmentSlot.FEET),"chest");storage.setTooltip(Tooltip.create(Component.translatable(storage.active?"scooter.inventory.storage_tip":"scooter.inventory.storage_missing")));buttons.add(storage);
 var music=Button.builder(Component.translatable("scooter.menu.music"),b->ScooterMenus.requestMusic(back)).bounds(sidebar?x:x+extraW+8,sidebar?extrasY+26:extrasY,extraW,22).build();music.setTooltip(Tooltip.create(Component.translatable("scooter.inventory.music_tip")));buttons.add(music);buttons.forEach(e::addListener);
 }
 @SubscribeEvent public static void layout(net.neoforged.neoforge.client.event.ScreenEvent.Render.Pre e){if(!(e.getScreen() instanceof InventoryScreen screen))return;var buttons=BUTTONS.get(screen);if(buttons==null)return;boolean side=screen.getGuiLeft()>=116&&!screen.getRecipeBookComponent().isVisible();int w=side?Math.min(120,screen.getGuiLeft()-12):Math.min(108,(screen.width-16)/2),x=side?6:screen.width/2-w-4,y=side?screen.getGuiTop():Math.max(4,screen.getGuiTop()-30);for(int i=0;i<4;i++){var b=buttons.get(i);b.setWidth(w);b.setHeight(i<2?(side?32:24):22);b.setX(side?x:x+(i%2)*(w+8));b.setY(side?y+(i==0?0:i==1?38:i==2?82:108):i<2?y:Math.min(screen.height-24,screen.getGuiTop()+170));}}
}
