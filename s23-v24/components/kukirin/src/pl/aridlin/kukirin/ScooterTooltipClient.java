package pl.aridlin.kukirin;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.api.distmarker.Dist;
import net.minecraft.network.chat.Component;
import net.minecraft.ChatFormatting;
@EventBusSubscriber(modid="goplanska_kukirin",value=Dist.CLIENT)
public final class ScooterTooltipClient {
 @SubscribeEvent public static void tooltip(net.neoforged.neoforge.event.entity.player.ItemTooltipEvent e){if(!e.getItemStack().is(Kukirin.ITEM.get()))return;var mc=net.minecraft.client.Minecraft.getInstance();e.getToolTip().add(Component.translatable("scooter.tip.menu",mc.options.keyInventory.getTranslatedKeyMessage()).withStyle(ChatFormatting.GRAY));if(net.minecraft.client.gui.screens.Screen.hasShiftDown()){e.getToolTip().add(Component.translatable("scooter.tip.pickup").withStyle(ChatFormatting.GRAY));e.getToolTip().add(Component.translatable("scooter.tip.jump",mc.options.keyJump.getTranslatedKeyMessage()).withStyle(ChatFormatting.GRAY));e.getToolTip().add(Component.translatable("scooter.tip.recall",ScooterRecallKey.RECALL.getTranslatedKeyMessage()).withStyle(ChatFormatting.GRAY));e.getToolTip().add(Component.translatable("scooter.tip.smith").withStyle(ChatFormatting.GRAY));}}
}
