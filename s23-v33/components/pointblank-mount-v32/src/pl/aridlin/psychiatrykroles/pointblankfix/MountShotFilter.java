package pl.aridlin.psychiatrykroles.pointblankfix;

import java.util.List;
import net.minecraft.world.entity.Entity;
import pl.aridlin.kukirin.Scooter;

/** Prevents a rider's own scooter assembly from intercepting its PointBlank shot. */
public final class MountShotFilter {
    private MountShotFilter() {}
    public static boolean protectedFromOwnShot(Entity shooter,Entity target) {
        if(shooter==null||target==null)return false;
        if(shooter==target)return true;
        Entity root=shooter.getRootVehicle();
        return root instanceof Scooter && root!=shooter && target.getRootVehicle()==root;
    }
    public static List<Entity> targets(Entity shooter,List<Entity> candidates) {
        if(!(shooter.getRootVehicle() instanceof Scooter))return candidates;
        return candidates.stream().filter(entity->!protectedFromOwnShot(shooter,entity)).toList();
    }
}
