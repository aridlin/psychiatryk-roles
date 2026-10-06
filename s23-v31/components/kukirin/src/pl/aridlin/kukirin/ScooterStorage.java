package pl.aridlin.kukirin;
import net.minecraft.world.*;
import net.minecraft.world.item.*;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.component.ItemContainerContents;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
public final class ScooterStorage {
 public static boolean open(Scooter s,ServerPlayer p){var stack=s.getItemBySlot(EquipmentSlot.FEET);if(!ScooterUpgradeRecipe.has(stack,"chest")||(ScooterEnchants.bound(stack)&&!ScooterEnchants.owned(s,p)))return false;var container=new SimpleContainer(27){public boolean stillValid(net.minecraft.world.entity.player.Player player){return !s.isRemoved()&&s.distanceToSqr(player)<=64&&(!ScooterEnchants.bound(s.getItemBySlot(EquipmentSlot.FEET))||player.getUUID().equals(ScooterEnchants.owner(s.getItemBySlot(EquipmentSlot.FEET))));}public void setChanged(){super.setChanged();var item=s.getItemBySlot(EquipmentSlot.FEET).copy();var contents=net.minecraft.core.NonNullList.withSize(27,ItemStack.EMPTY);for(int i=0;i<27;i++)contents.set(i,getItem(i).copy());item.set(DataComponents.CONTAINER,ItemContainerContents.fromItems(contents));s.setItemSlot(EquipmentSlot.FEET,item);}};
 var contents=net.minecraft.core.NonNullList.withSize(27,ItemStack.EMPTY);stack.getOrDefault(DataComponents.CONTAINER,ItemContainerContents.EMPTY).copyInto(contents);for(int i=0;i<27;i++)container.setItem(i,contents.get(i));p.openMenu(new SimpleMenuProvider((id,inventory,player)->ChestMenu.threeRows(id,inventory,container),Component.literal("Scooter storage")));return true;}
 public static Scooter target(ServerPlayer p){if(p.getVehicle() instanceof Scooter s)return s;var hit=p.pick(6,0,false);var entityHit=net.minecraft.world.entity.projectile.ProjectileUtil.getEntityHitResult(p,p.getEyePosition(),p.getEyePosition().add(p.getLookAngle().scale(6)),p.getBoundingBox().expandTowards(p.getLookAngle().scale(6)).inflate(1),e->e instanceof Scooter,36);return entityHit!=null?(Scooter)entityHit.getEntity():null;}
}
