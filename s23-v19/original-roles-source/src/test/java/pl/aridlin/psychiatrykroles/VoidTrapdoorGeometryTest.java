package pl.aridlin.psychiatrykroles;

import net.minecraft.core.Direction;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

class VoidTrapdoorGeometryTest {
    private static VoidTrapdoors.Contact fallingContact(int mask, double eastOffset) {
        Vec3 center = new Vec3(0, .1, 0);
        Vec3 from = center.add(eastOffset, 1, 0);
        Vec3 to = center.add(eastOffset, -1, 0);
        AABB currentBox = new AABB(to.x - .005, to.y - .005, to.z - .005,
            to.x + .005, to.y + .005, to.z + .005);
        return VoidTrapdoors.contact(center, mask, currentBox, from, to, to.subtract(from));
    }

    @Test void aFallingItemCrossesEvenWhenItEndsBelowTheThinPlane() {
        assertNotNull(fallingContact(15, 0));
    }

    @Test void theFrameDoesNotTriggerButAnUncoveredEastEdgeDoes() {
        assertNull(fallingContact(15, .44));
        int noEastFrame = 15 & ~(1 << Direction.EAST.get2DDataValue());
        assertNotNull(fallingContact(noEastFrame, .44));
    }
}
