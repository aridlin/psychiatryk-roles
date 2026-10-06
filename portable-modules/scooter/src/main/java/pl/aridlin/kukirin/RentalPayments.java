package pl.aridlin.kukirin;
import net.minecraft.server.level.ServerPlayer;
/** Server economy extension point. Default payment is an inventory item, without Roles/poker chips. */
public final class RentalPayments {
 public interface Provider {long balance(ServerPlayer player);boolean charge(ServerPlayer player,long amount);void refund(ServerPlayer player,long amount);}
 private static Provider provider=new InventoryCurrency();
 public static synchronized void register(Provider value){provider=java.util.Objects.requireNonNull(value);}
 public static long balance(ServerPlayer p){return provider.balance(p);}
 public static boolean charge(ServerPlayer p,long a){return a>=0&&provider.charge(p,a);}
 public static void refund(ServerPlayer p,long a){if(a>0)provider.refund(p,a);}
 private static final class InventoryCurrency implements Provider {
  private net.minecraft.world.item.Item item(){return net.minecraft.core.registries.BuiltInRegistries.ITEM.get(net.minecraft.resources.ResourceLocation.parse(PortableOptions.get().currency()));}
  public long balance(ServerPlayer p){var item=item();if(item==net.minecraft.world.item.Items.AIR)return 0;long result=0;for(int i=0;i<p.getInventory().getContainerSize();i++){var stack=p.getInventory().getItem(i);if(stack.is(item))result+=stack.getCount();}return result;}
  public boolean charge(ServerPlayer p,long a){if(balance(p)<a)return false;var item=item();long left=a;for(int i=0;i<p.getInventory().getContainerSize()&&left>0;i++){var stack=p.getInventory().getItem(i);if(stack.is(item)){int n=(int)Math.min(left,stack.getCount());stack.shrink(n);left-=n;}}p.getInventory().setChanged();return true;}
  public void refund(ServerPlayer p,long a){var item=item();if(item==net.minecraft.world.item.Items.AIR)throw new IllegalStateException("Currency no longer exists");while(a>0){int n=(int)Math.min(a,item.getDefaultMaxStackSize());var stack=new net.minecraft.world.item.ItemStack(item,n);if(!p.getInventory().add(stack))p.drop(stack,false);a-=n;}p.getInventory().setChanged();}
 }
 private RentalPayments(){}
}
