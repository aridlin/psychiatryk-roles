package pl.aridlin.psychiatrykroles.peeb.mixin;

import net.minecraft.network.syncher.EntityDataAccessor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import pl.aridlin.kukirin.Scooter;

@Mixin({Scooter.class})
public interface ScooterBatteryAccess {
   @Accessor("BRAKE_TURN")
   static EntityDataAccessor<Boolean> peeb$brakeData() {
      throw new AssertionError("Mixin accessor not applied");
   }
}
