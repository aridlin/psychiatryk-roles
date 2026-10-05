package pl.aridlin.kukirin;
import net.minecraft.network.chat.Component;
import net.minecraft.ChatFormatting;
import net.minecraft.world.item.ItemStack;
/** Common-side description also used by smithing result previews; never depends on client classes. */
public final class ScooterLore {
 public static void append(ItemStack stack,java.util.List<Component> lines){
  var data=stack.getOrDefault(net.minecraft.core.component.DataComponents.CUSTOM_DATA,net.minecraft.world.item.component.CustomData.EMPTY).copyTag();
  if(data.getBoolean("GoplanskaRental"))lines.add(Component.translatable("scooter.hud.battery",RentalPolicy.batteryPercent(data.getInt("RentalBattery"))).withStyle(ChatFormatting.GREEN));
  if(ScooterEnchants.bound(stack))lines.add(Component.translatable("scooter.upgrade.bound").withStyle(ChatFormatting.GOLD));
  for(String upgrade:new String[]{"chest","jukebox","noteblock","netherite","saddle"})if(ScooterUpgradeRecipe.has(stack,upgrade))lines.add(Component.translatable("scooter.upgrade."+upgrade).withStyle(ChatFormatting.AQUA));
  lines.add(Component.translatable("scooter.tip.place").withStyle(ChatFormatting.GRAY));
  lines.add(Component.translatable("scooter.tip.guide").withStyle(ChatFormatting.DARK_GRAY));
 }
 private ScooterLore(){}
}
