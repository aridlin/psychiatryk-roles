package pl.aridlin.kukirin;
public final class ScooterPassengers {
 public static boolean enabled(Scooter scooter){var item=scooter.getItemBySlot(net.minecraft.world.entity.EquipmentSlot.FEET);return ScooterUpgradeRecipe.has(item,"saddle")&&!item.getOrDefault(net.minecraft.core.component.DataComponents.CUSTOM_DATA,net.minecraft.world.item.component.CustomData.EMPTY).copyTag().getBoolean("GoplanskaScooterPassengersDisabled");}
 public static void enabled(Scooter scooter,boolean enabled){var item=scooter.getItemBySlot(net.minecraft.world.entity.EquipmentSlot.FEET).copy();net.minecraft.world.item.component.CustomData.update(net.minecraft.core.component.DataComponents.CUSTOM_DATA,item,t->t.putBoolean("GoplanskaScooterPassengersDisabled",!enabled));scooter.setItemSlot(net.minecraft.world.entity.EquipmentSlot.FEET,item);if(!enabled)for(var p:java.util.List.copyOf(scooter.getPassengers()))if(p!=scooter.getFirstPassenger())p.stopRiding();}
 private ScooterPassengers(){}
}
