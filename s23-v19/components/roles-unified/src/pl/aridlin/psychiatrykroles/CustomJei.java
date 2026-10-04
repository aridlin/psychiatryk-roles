package pl.aridlin.psychiatrykroles;
import java.util.*;
import mezz.jei.api.*;
import mezz.jei.api.registration.*;
import mezz.jei.api.ingredients.subtypes.*;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.*;
import net.minecraft.core.component.DataComponents;
@JeiPlugin
public final class CustomJei implements IModPlugin {
 public static mezz.jei.api.runtime.IJeiRuntime runtime;
 @Override public void onRuntimeAvailable(mezz.jei.api.runtime.IJeiRuntime r){runtime=r;}
 @Override public void onRuntimeUnavailable(){runtime=null;}
 public ResourceLocation getPluginUid(){return ResourceLocation.fromNamespaceAndPath("psychiatryk_roles","custom_recipes");}
 private static List<ItemStack> results(){var mc=Minecraft.getInstance();if(mc.level==null)return List.of();return mc.level.getRecipeManager().getRecipes().stream().filter(r->Set.of("psychiatryk_roles","goplanska_kukirin","goplanska_starter").contains(r.id().getNamespace())).map(r->{var stack=r.value().getResultItem(mc.level.registryAccess()).copy();if(stack.has(DataComponents.CUSTOM_DATA)){String label=java.util.Arrays.stream(r.id().getPath().split("_")).map(w->w.isEmpty()?w:Character.toUpperCase(w.charAt(0))+w.substring(1)).collect(java.util.stream.Collectors.joining(" "));stack.set(DataComponents.CUSTOM_NAME,net.minecraft.network.chat.Component.literal(label));}return stack;}).filter(s->!s.isEmpty()).toList();}
 public static String subtype(ItemStack s){var data=s.get(DataComponents.CUSTOM_DATA);if(data==null)return IIngredientSubtypeInterpreter.NONE;var tag=data.copyTag();var keys=tag.getAllKeys().stream().filter(k->k.startsWith("psychiatryk")).sorted().toList();if(keys.isEmpty())return IIngredientSubtypeInterpreter.NONE;return keys.stream().map(k->k+"="+tag.get(k)).collect(java.util.stream.Collectors.joining(";"));}
 @Override public void registerItemSubtypes(ISubtypeRegistration r){for(Item item:List.of(Items.WRITABLE_BOOK,Items.WRITTEN_BOOK,Items.RABBIT_HIDE,Items.SHEARS,Items.STONE_PICKAXE,Items.STONE_SWORD,Items.COMPASS,Items.RECOVERY_COMPASS,Items.CLOCK,Items.ECHO_SHARD,Items.WHITE_DYE,Items.BLAZE_ROD,Items.DARK_OAK_DOOR,Items.DARK_OAK_TRAPDOOR,Items.WARPED_DOOR,Items.WARPED_TRAPDOOR))r.registerSubtypeInterpreter(item,(IIngredientSubtypeInterpreter<ItemStack>)(stack,context)->subtype(stack));}
 @Override public void registerExtraIngredients(IExtraIngredientRegistration r){r.addExtraItemStacks(results());}
}
