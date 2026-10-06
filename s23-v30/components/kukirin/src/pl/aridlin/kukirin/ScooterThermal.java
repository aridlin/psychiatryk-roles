package pl.aridlin.kukirin;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.core.component.DataComponents;
/** Twenty seconds to overheat in the Nether, thirty seconds to fully cool outside. */
public final class ScooterThermal {
 public static int next(int heat,boolean nether){return Math.clamp(heat+(nether?3:-2),0,60);}
 public static boolean hot(int heat,boolean wasHot){return heat>=60||(wasHot&&heat>20);}
 public static void tick(Scooter s){if(s.level().isClientSide)return;var item=s.getItemBySlot(EquipmentSlot.FEET);var tag=item.getOrDefault(DataComponents.CUSTOM_DATA,CustomData.EMPTY).getUnsafe();int heat=Math.clamp(tag.getInt("GoplanskaScooterHeat"),0,60);boolean hot=hot(heat,tag.getBoolean("GoplanskaScooterHot"));s.heatState(heat,hot);if(s.tickCount%20!=0)return;boolean nether=s.level().dimension()==net.minecraft.world.level.Level.NETHER;int next=next(heat,nether);boolean throttled=hot(next,hot);if(next!=heat||throttled!=hot){item=item.copy();CustomData.update(DataComponents.CUSTOM_DATA,item,t->{t.putInt("GoplanskaScooterHeat",next);t.putBoolean("GoplanskaScooterHot",throttled);});s.setItemSlot(EquipmentSlot.FEET,item);s.heatState(next,throttled);}}
 private ScooterThermal(){}
}
