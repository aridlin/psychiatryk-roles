package pl.aridlin.psychiatrykroles;

import org.junit.jupiter.api.Test;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class PokerDataTest {
    @Test
    void walletPersistsAndNeverOverdraws() {
        UUID player = UUID.fromString("00000000-0000-0000-0000-000000000069");
        PokerData data = new PokerData(); data.credit(player, 100);
        assertTrue(data.debit(player, 40)); assertFalse(data.debit(player, 61));
        PokerData loaded = PokerData.load(data.save(new net.minecraft.nbt.CompoundTag()));
        assertEquals(60, loaded.balance(player));
    }
}
