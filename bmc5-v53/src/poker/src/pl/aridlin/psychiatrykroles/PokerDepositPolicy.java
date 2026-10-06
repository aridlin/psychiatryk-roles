package pl.aridlin.psychiatrykroles;
import net.minecraft.world.item.ItemStack;
/** Exact-component escrow permits normal names/damage/enchants, while protected gameplay tools stay out. */
final class PokerDepositPolicy {
 static boolean allowed(ItemStack stack){if(stack.isEmpty())return false;
  var data=stack.get(net.minecraft.core.component.DataComponents.CUSTOM_DATA);
  if(data!=null){for(String key:data.getUnsafe().getAllKeys())if(key.startsWith("psychiatryk")||key.startsWith("Goplanska")||key.equals("ConsultantItem")||key.equals("Owner"))return false;}
  return true;
 }
 private PokerDepositPolicy(){}
}
