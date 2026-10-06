package pl.aridlin.kukirin;

import net.minecraft.world.item.*;
import net.minecraft.world.item.crafting.*;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.core.component.DataComponents;

/** Repaint only the accents; preserve binding, storage, music, enchantments and trims. */
public final class ScooterDyeRecipe extends SmithingTransformRecipe {
 public static final String COLOR="GoplanskaScooterDye";
 public final DyeColor color;
 public ScooterDyeRecipe(DyeColor color){super(Ingredient.EMPTY,Ingredient.of(Kukirin.ITEM.get()),Ingredient.of(DyeItem.byColor(color)),paint(new ItemStack(Kukirin.ITEM.get()),color));this.color=color;}
 public static ItemStack paint(ItemStack input,DyeColor color){var result=input.copyWithCount(1);CustomData.update(DataComponents.CUSTOM_DATA,result,t->t.putInt(COLOR,color.getId()));return result;}
 public static int variant(ItemStack stack){var data=stack.get(DataComponents.CUSTOM_DATA);if(data==null)return 0;var tag=data.copyTag();if(tag.getBoolean("GoplanskaRental"))return 17+Math.clamp(tag.getInt("RentalBrand"),0,2);return tag.contains(COLOR)?1+Math.clamp(tag.getInt(COLOR),0,15):0;}
 @Override public boolean matches(SmithingRecipeInput input,net.minecraft.world.level.Level level){return super.matches(input,level)&&variant(input.base())!=color.getId()+1;}
 @Override public ItemStack assemble(SmithingRecipeInput input,net.minecraft.core.HolderLookup.Provider registries){return paint(input.base(),color);}
 @Override public boolean isIncomplete(){return false;}
 @Override public RecipeSerializer<?> getSerializer(){return Kukirin.DYE.get();}
 public static final class Serializer implements RecipeSerializer<ScooterDyeRecipe>{
  public com.mojang.serialization.MapCodec<ScooterDyeRecipe> codec(){return DyeColor.CODEC.fieldOf("color").xmap(ScooterDyeRecipe::new,r->r.color);}
  public net.minecraft.network.codec.StreamCodec<net.minecraft.network.RegistryFriendlyByteBuf,ScooterDyeRecipe> streamCodec(){return net.minecraft.network.codec.StreamCodec.composite(DyeColor.STREAM_CODEC,r->r.color,ScooterDyeRecipe::new);}
 }
}
