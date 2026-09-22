package pl.aridlin.psychiatrykroles;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RoleDataTest {
    @Test
    void chalkMarkersExpireAndKeepOnlyTheFiveNewestPerPlayer() {
        RoleData data = new RoleData();
        UUID owner = UUID.randomUUID();
        long future = System.currentTimeMillis() + 60_000L;
        data.addChalkMarker(owner, new RoleData.ChalkMarker("minecraft:overworld", -1, 0, 0, future));
        for (int index = 0; index < 6; index++) {
            data.addChalkMarker(owner, new RoleData.ChalkMarker("minecraft:overworld", index, 0, 0, future));
        }
        data.addChalkMarker(UUID.randomUUID(), new RoleData.ChalkMarker(
            "minecraft:overworld", 99, 0, 0, System.currentTimeMillis() - 1
        ));

        List<RoleData.OwnedChalkMarker> active = data.activeChalkMarkers(System.currentTimeMillis());

        assertEquals(5, active.size());
        assertEquals(List.of(1.0D, 2.0D, 3.0D, 4.0D, 5.0D),
            active.stream().map(value -> value.marker().x()).toList());

        assertTrue(data.removeOldestChalkMarker(owner));
        assertEquals(List.of(2.0D, 3.0D, 4.0D, 5.0D),
            data.activeChalkMarkers(System.currentTimeMillis()).stream()
                .map(value -> value.marker().x()).toList());
    }
}
