package pl.aridlin.kukirin;
import java.util.*;
import net.minecraft.world.item.*;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.*;
public final class ScooterPresets {
 public static List<ItemStack> create(HolderLookup.Provider registries){return List.of(
  preset(registries,"Lime Silence",DyeColor.LIME,true,false,true,Map.ofEntries(Map.entry("efficiency",3),Map.entry("sweeping_edge",1),Map.entry("frost_walker",2),Map.entry("depth_strider",3),Map.entry("feather_falling",4),Map.entry("loyalty",3),Map.entry("wind_burst",2),Map.entry("lunge",2),Map.entry("unbreaking",3),Map.entry("mending",1))),
  preset(registries,"Orange Roadrunner",DyeColor.ORANGE,true,true,true,Map.of("efficiency",5,"quick_charge",3,"sweeping_edge",2,"feather_falling",4,"wind_burst",3,"lunge",3,"loyalty",3)),
  preset(registries,"Blue Ice Runner",DyeColor.LIGHT_BLUE,true,true,false,Map.of("frost_walker",2,"density",3,"sweeping_edge",3,"efficiency",2,"feather_falling",4,"depth_strider",3,"loyalty",3,"lunge",2)),
  flyer(registries));
 }
 private static ItemStack flyer(HolderLookup.Provider registries){var item=preset(registries,"Netherite Flyer",DyeColor.BLACK,true,false,true,Map.of("efficiency",5,"quick_charge",3,"sweeping_edge",3,"feather_falling",4,"loyalty",3,"wind_burst",3));CustomData.update(DataComponents.CUSTOM_DATA,item,t->t.putBoolean(ScooterUpgradeRecipe.key("netherite"),true));return item;}
 private static ItemStack preset(HolderLookup.Provider registries,String name,DyeColor color,boolean chest,boolean jukebox,boolean note,Map<String,Integer> enchants){
  var result=ScooterDyeRecipe.paint(new ItemStack(Kukirin.ITEM.get()),color);
  result.set(DataComponents.CUSTOM_NAME,net.minecraft.network.chat.Component.literal(name));
  CustomData.update(DataComponents.CUSTOM_DATA,result,t->{t.putBoolean(ScooterEnchants.BOUND,true);if(chest)t.putBoolean(ScooterUpgradeRecipe.key("chest"),true);if(jukebox)t.putBoolean(ScooterUpgradeRecipe.key("jukebox"),true);if(note)t.putBoolean(ScooterUpgradeRecipe.key("noteblock"),true);});
  if(name.equals("Lime Silence"))result.set(DataComponents.TRIM,new net.minecraft.world.item.armortrim.ArmorTrim(registries.lookupOrThrow(Registries.TRIM_MATERIAL).getOrThrow(ResourceKey.create(Registries.TRIM_MATERIAL,ResourceLocation.withDefaultNamespace("emerald"))),registries.lookupOrThrow(Registries.TRIM_PATTERN).getOrThrow(ResourceKey.create(Registries.TRIM_PATTERN,ResourceLocation.withDefaultNamespace("silence")))));
  for(var enchant:enchants.entrySet()){var id=enchant.getKey().equals("lunge")?ResourceLocation.parse("goplanska_kukirin:lunge"):ResourceLocation.withDefaultNamespace(enchant.getKey());result.enchant(registries.lookupOrThrow(Registries.ENCHANTMENT).getOrThrow(ResourceKey.create(Registries.ENCHANTMENT,id)),enchant.getValue());}
  return result;
 }
}
