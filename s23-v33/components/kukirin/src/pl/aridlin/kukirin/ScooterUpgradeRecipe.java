package pl.aridlin.kukirin;
import net.minecraft.world.item.crafting.*;
import net.minecraft.world.item.*;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.core.component.DataComponents;
public final class ScooterUpgradeRecipe extends SmithingTransformRecipe {
 public final String upgrade;
 public ScooterUpgradeRecipe(String upgrade){super(Ingredient.EMPTY,Ingredient.of(Kukirin.ITEM.get()),Ingredient.of(addition(upgrade)),result(upgrade));this.upgrade=upgrade;}
 public static String key(String upgrade){return "GoplanskaScooter"+switch(upgrade){case "infinite"->"InfiniteBattery";case "saddle"->"Passenger";case "chest"->"Storage";case "jukebox"->"Jukebox";case "noteblock"->"Wav";case "netherite"->"Flight";default->throw new IllegalArgumentException("Unknown scooter upgrade");};}
 static Item addition(String upgrade){return switch(upgrade){case "infinite"->Items.COMMAND_BLOCK;case "saddle"->Items.SADDLE;case "chest"->Items.CHEST;case "jukebox"->Items.JUKEBOX;case "noteblock"->Items.NOTE_BLOCK;case "netherite"->Items.NETHERITE_BLOCK;default->throw new IllegalArgumentException("Unknown scooter upgrade");};}
 static ItemStack result(String upgrade){var stack=new ItemStack(Kukirin.ITEM.get());CustomData.update(DataComponents.CUSTOM_DATA,stack,t->t.putBoolean(key(upgrade),true));return stack;}
 public static boolean has(ItemStack item,String upgrade){var d=item.get(DataComponents.CUSTOM_DATA);return d!=null&&d.getUnsafe().getBoolean(key(upgrade));}
 public boolean matches(SmithingRecipeInput input,net.minecraft.world.level.Level level){return super.matches(input,level)&&!has(input.base(),upgrade)&&!((upgrade.equals("netherite")||upgrade.equals("infinite"))&&input.base().getOrDefault(DataComponents.CUSTOM_DATA,CustomData.EMPTY).getUnsafe().getBoolean("GoplanskaRental"));}
 public ItemStack assemble(SmithingRecipeInput input,net.minecraft.core.HolderLookup.Provider registries){var item=input.base().copyWithCount(1);CustomData.update(DataComponents.CUSTOM_DATA,item,t->t.putBoolean(key(upgrade),true));return item;}
 public boolean isIncomplete(){return false;}
 public RecipeSerializer<?> getSerializer(){return Kukirin.UPGRADE.get();}
 public static final class Serializer implements RecipeSerializer<ScooterUpgradeRecipe>{public com.mojang.serialization.MapCodec<ScooterUpgradeRecipe> codec(){return com.mojang.serialization.Codec.STRING.fieldOf("upgrade").xmap(ScooterUpgradeRecipe::new,r->r.upgrade);}public net.minecraft.network.codec.StreamCodec<net.minecraft.network.RegistryFriendlyByteBuf,ScooterUpgradeRecipe> streamCodec(){return net.minecraft.network.codec.StreamCodec.composite(net.minecraft.network.codec.ByteBufCodecs.STRING_UTF8,r->r.upgrade,ScooterUpgradeRecipe::new);}}
}
