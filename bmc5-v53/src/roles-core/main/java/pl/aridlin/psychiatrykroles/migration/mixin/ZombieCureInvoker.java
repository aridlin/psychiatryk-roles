package pl.aridlin.psychiatrykroles.migration.mixin;
import java.util.UUID;
import net.minecraft.world.entity.monster.ZombieVillager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;
@Mixin(ZombieVillager.class)
public interface ZombieCureInvoker {
    @Invoker("startConverting") void bmc$startConverting(UUID player,int ticks);
}
