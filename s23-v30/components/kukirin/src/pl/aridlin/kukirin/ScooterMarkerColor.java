package pl.aridlin.kukirin;
import net.minecraft.world.item.*;
/** Shared paint palette for the item tint, bound marker and chams. */
public final class ScooterMarkerColor {
 private ScooterMarkerColor(){}
 public static int variant(int variant){return 0xff000000|(variant>=17?(variant==17?0x84d200:variant==18?0x32d27d:0xf9801d):variant==0?0xf9801d:DyeColor.byId(variant-1).getTextureDiffuseColor());}
 public static int item(ItemStack stack){return variant(ScooterDyeRecipe.variant(stack));}
 public static int entity(Scooter scooter,float partial){return variant(ScooterEasterEggs.variant(scooter,partial));}
}
