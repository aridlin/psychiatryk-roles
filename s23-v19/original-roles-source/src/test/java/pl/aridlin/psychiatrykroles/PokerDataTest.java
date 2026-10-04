package pl.aridlin.psychiatrykroles;

import org.junit.jupiter.api.Test;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class PokerDataTest {
    @Test
    void offlinePokerItemDeliveryPersistsUntilClaimed() {
        UUID player = UUID.randomUUID();
        PokerData data = new PokerData();
        data.queueGuiItem(player);
        PokerData restored = PokerData.load(data.save(new net.minecraft.nbt.CompoundTag()));
        assertTrue(restored.hasPendingGuiItem(player));
        restored.deliveredGuiItem(player);
        assertFalse(PokerData.load(restored.save(new net.minecraft.nbt.CompoundTag())).hasPendingGuiItem(player));
    }

    @Test
    void elytraCannotBeExchanged() {
        assertEquals(0, PokerItemValues.value("minecraft:elytra"));
        assertFalse(PokerItemValues.all().containsKey("minecraft:elytra"));
    }

    @Test
    void rareItemsCannotBeBoughtBackFromPoker() {
        assertTrue(PokerItemValues.all().size() >= 50);
        assertTrue(PokerItemValues.value("minecraft:copper_ingot") > 0);
        assertTrue(PokerItemValues.value("minecraft:ender_pearl") > 0);
        assertTrue(PokerItemValues.canExchangeOut("minecraft:iron_ingot"));
        assertTrue(PokerItemValues.canExchangeOut("minecraft:emerald"));
        assertFalse(PokerItemValues.canExchangeOut("minecraft:ender_pearl"));
        assertFalse(PokerItemValues.canExchangeOut("minecraft:diamond"));
        assertFalse(PokerItemValues.canExchangeOut("minecraft:nether_star"));
        assertFalse(PokerItemValues.exchangeOutItems().containsKey("minecraft:elytra"));
    }

    @Test
    void walletPersistsAndNeverOverdraws() {
        UUID player = UUID.fromString("00000000-0000-0000-0000-000000000069");
        PokerData data = new PokerData(); data.credit(player, 100);
        assertTrue(data.debit(player, 40)); assertFalse(data.debit(player, 61));
        PokerData loaded = PokerData.load(data.save(new net.minecraft.nbt.CompoundTag()));
        assertEquals(60, loaded.balance(player));
    }
}
