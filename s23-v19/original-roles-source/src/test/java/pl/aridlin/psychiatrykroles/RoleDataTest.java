package pl.aridlin.psychiatrykroles;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RoleDataTest {
    @Test
    void migratesOnlyKameleonFromPatientToAlwaysActiveContractor() {
        UUID rozowy = UUID.fromString("042f35a3-fef8-4fa1-91b0-4ead49e40d4e");
        net.minecraft.nbt.CompoundTag legacy = new net.minecraft.nbt.CompoundTag();
        net.minecraft.nbt.ListTag patients = new net.minecraft.nbt.ListTag();
        patients.add(net.minecraft.nbt.StringTag.valueOf(RoleData.INITIAL_CONTRACTOR.toString()));
        patients.add(net.minecraft.nbt.StringTag.valueOf(rozowy.toString()));
        legacy.put("Patients", patients);

        RoleData migrated = RoleData.load(legacy);
        assertFalse(migrated.isPatient(RoleData.INITIAL_CONTRACTOR));
        assertEquals(new RoleData.ContractorRule("always", null, 0),
            migrated.contractorRule(RoleData.INITIAL_CONTRACTOR));
        assertTrue(migrated.isPatient(rozowy));
        assertEquals(migrated.contractorRule(RoleData.INITIAL_CONTRACTOR),
            RoleData.load(migrated.save(new net.minecraft.nbt.CompoundTag()))
                .contractorRule(RoleData.INITIAL_CONTRACTOR));
    }

    @Test
    void contractorRulePersistsAndAdmissionReplacesContractorRole() {
        RoleData data = new RoleData();
        UUID contractor = UUID.randomUUID();
        UUID patient = UUID.randomUUID();
        RoleData.ContractorRule rule = new RoleData.ContractorRule("near", patient, 24);

        assertTrue(data.setContractor(contractor, rule));
        RoleData restored = RoleData.load(data.save(new net.minecraft.nbt.CompoundTag()));
        assertEquals(rule, restored.contractorRule(contractor));
        assertFalse(restored.isPatient(contractor));

        restored.addPatient(contractor);
        assertTrue(restored.isPatient(contractor));
        assertNull(restored.contractorRule(contractor));
    }

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
