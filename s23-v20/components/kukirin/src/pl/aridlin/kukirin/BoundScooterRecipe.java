package pl.aridlin.kukirin;
import net.minecraft.world.item.crafting.*;
import net.minecraft.world.item.*;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.core.component.DataComponents;
public final class BoundScooterRecipe extends SmithingTransformRecipe {
 public BoundScooterRecipe(){super(Ingredient.EMPTY,Ingredient.of(Kukirin.ITEM.get()),Ingredient.of(Items.NETHER_STAR),result());}
 private static ItemStack result(){var stack=new ItemStack(Kukirin.ITEM.get());CustomData.update(DataComponents.CUSTOM_DATA,stack,t->t.putBoolean(ScooterEnchants.BOUND,true));return stack;}
 @Override public boolean matches(SmithingRecipeInput input,net.minecraft.world.level.Level level){return super.matches(input,level)&&!ScooterEnchants.bound(input.base());}
 @Override public ItemStack assemble(SmithingRecipeInput input,net.minecraft.core.HolderLookup.Provider registries){var item=input.base().copyWithCount(1);CustomData.update(DataComponents.CUSTOM_DATA,item,t->t.putBoolean(ScooterEnchants.BOUND,true));return item;}
 @Override public boolean isIncomplete(){return false;}
 @Override public RecipeSerializer<?> getSerializer(){return Kukirin.BINDING.get();}
 public static final class Serializer implements RecipeSerializer<BoundScooterRecipe>{
  public com.mojang.serialization.MapCodec<BoundScooterRecipe> codec(){return com.mojang.serialization.MapCodec.unit(BoundScooterRecipe::new);}
  public net.minecraft.network.codec.StreamCodec<net.minecraft.network.RegistryFriendlyByteBuf,BoundScooterRecipe> streamCodec(){return net.minecraft.network.codec.StreamCodec.of((buffer,recipe)->{},buffer->new BoundScooterRecipe());}
 }
}
