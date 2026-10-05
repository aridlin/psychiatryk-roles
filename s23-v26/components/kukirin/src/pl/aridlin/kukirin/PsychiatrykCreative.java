package pl.aridlin.kukirin;
import net.minecraft.world.item.*;
import net.minecraft.core.registries.*;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.registries.DeferredRegister;
/** One searchable home for all unified custom items and component-bearing presets. */
public final class PsychiatrykCreative {
 private static final DeferredRegister<CreativeModeTab> TABS=DeferredRegister.create(Registries.CREATIVE_MODE_TAB,"psychiatryk_roles");
 static {TABS.register("custom_items",()->CreativeModeTab.builder().title(Component.literal("Psychiatryk Roles")).icon(()->new ItemStack(Kukirin.ITEM.get())).displayItems((parameters,out)->{
  for(var item:BuiltInRegistries.ITEM){var id=BuiltInRegistries.ITEM.getKey(item);if(java.util.Set.of("psychiatryk_roles","goplanska_kukirin","goplanska_starter").contains(id.getNamespace())&&item!=Items.AIR)out.accept(item);}
  for(var preset:ScooterPresets.create(parameters.holders()))out.accept(preset);
  for(var rental:ScooterRental.presets())out.accept(rental);
  for(var name:java.util.List.of("door","trapdoor","immersive_door","immersive_trapdoor"))out.accept(voidPair(name).copyWithCount(1));
  var mc=net.minecraft.client.Minecraft.getInstance();if(mc.level!=null){var seen=new java.util.HashSet<String>();for(var holder:mc.level.getRecipeManager().getRecipes()){if(!java.util.Set.of("psychiatryk_roles","goplanska_starter").contains(holder.id().getNamespace()))continue;var stack=holder.value().getResultItem(parameters.holders());if(!stack.isEmpty()&&seen.add(stack.getItem()+"/"+stack.getComponents())){try{out.accept(stack.copyWithCount(1));}catch(IllegalStateException duplicate){/* Same preset is already directly registered. */}}}}
 }).build());}
 private static ItemStack voidPair(String name){try{var method=Class.forName("pl.aridlin.psychiatrykroles.VoidGive").getDeclaredMethod("pair",String.class,boolean.class);method.setAccessible(true);return (ItemStack)method.invoke(null,name,false);}catch(ReflectiveOperationException error){throw new IllegalStateException("Cannot create Void Door preset",error);}}
 public static void register(net.neoforged.bus.api.IEventBus bus){TABS.register(bus);}
}
