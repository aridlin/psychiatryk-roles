package pl.aridlin.kukirin;
@mezz.jei.api.JeiPlugin
public final class ScooterJeiInfo implements mezz.jei.api.IModPlugin {
 public net.minecraft.resources.ResourceLocation getPluginUid(){return net.minecraft.resources.ResourceLocation.parse("goplanska_kukirin:scooter_enchantments");}
 @Override public void registerRecipes(mezz.jei.api.registration.IRecipeRegistration registration){var mc=net.minecraft.client.Minecraft.getInstance();if(mc.level==null)return;var lookup=mc.level.registryAccess().lookupOrThrow(net.minecraft.core.registries.Registries.ENCHANTMENT);var all=new java.util.ArrayList<net.minecraft.network.chat.Component>();all.add(net.minecraft.network.chat.Component.translatable("scooter.jei.enchantments"));
  for(String name:new String[]{"efficiency","quick_charge","sweeping_edge","density","frost_walker","feather_falling","depth_strider","soul_speed","piercing","knockback","wind_burst","loyalty","breach","lunge"}){var key=net.minecraft.resources.ResourceKey.create(net.minecraft.core.registries.Registries.ENCHANTMENT,net.minecraft.resources.ResourceLocation.parse(name.equals("lunge")?"goplanska_kukirin:lunge":"minecraft:"+name));var holder=lookup.getOrThrow(key);var description=net.minecraft.network.chat.Component.translatable("scooter.jei.enchant."+name);all.add(description);var book=net.minecraft.world.item.EnchantedBookItem.createForEnchantment(new net.minecraft.world.item.enchantment.EnchantmentInstance(holder,1));registration.addIngredientInfo(book,mezz.jei.api.constants.VanillaTypes.ITEM_STACK,description);}
  registration.addIngredientInfo(Kukirin.ITEM.get(),all.toArray(net.minecraft.network.chat.Component[]::new));
 }
}
