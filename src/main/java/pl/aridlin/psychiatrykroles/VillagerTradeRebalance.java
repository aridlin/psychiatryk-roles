package pl.aridlin.psychiatrykroles;

import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerTrades;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.trading.ItemCost;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.item.trading.MerchantOffers;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.village.VillagerTradesEvent;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;

import java.util.List;

/** Reprices both newly generated and already saved villager offers. */
final class VillagerTradeRebalance {
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void onTradeTable(VillagerTradesEvent event) {
        for (int level = 1; level <= 5; level++) {
            List<VillagerTrades.ItemListing> listings = event.getTrades().get(level);
            if (listings == null) continue;
            final int tradeLevel = level;
            for (int index = 0; index < listings.size(); index++) {
                VillagerTrades.ItemListing original = listings.get(index);
                listings.set(index, (trader, random) -> reprice(original.getOffer(trader, random), tradeLevel));
            }
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void onVillagerOpen(PlayerInteractEvent.EntityInteract event) {
        if (event.getLevel().isClientSide() || !(event.getTarget() instanceof Villager villager)) return;
        MerchantOffers offers = villager.getOffers();
        int level = villager.getVillagerData().getLevel();
        for (int index = 0; index < offers.size(); index++) {
            MerchantOffer old = offers.get(index);
            MerchantOffer updated = reprice(old, level);
            if (updated != old) offers.set(index, updated);
        }
    }

    private static MerchantOffer reprice(MerchantOffer offer, int villagerLevel) {
        if (offer == null) return null;
        ItemStack originalCost = offer.getBaseCostA();
        if (!originalCost.is(Items.EMERALD)) return offer;
        ItemStack result = offer.getResult();
        int emeralds = requiredEmeralds(result, villagerLevel);
        if (emeralds <= originalCost.getCount()) return offer;

        // A merchant has only two payment slots. 21 emerald blocks + 3 emeralds
        // is exactly three 64-item stacks, while remaining a normal trade.
        int blocks = emeralds / 9;
        int loose = emeralds % 9;
        var second = offer.getItemCostB();
        if (second.isEmpty() && loose > 0) second = java.util.Optional.of(new ItemCost(Items.EMERALD, loose));
        if (offer.getItemCostB().isPresent() && loose > 0) blocks++;
        MerchantOffer changed = new MerchantOffer(
            new ItemCost(Items.EMERALD_BLOCK, blocks), second, result.copy(),
            offer.getUses(), offer.getMaxUses(), offer.getXp(),
            0.0F, offer.getDemand()
        );
        changed.setSpecialPriceDiff(0);
        return changed;
    }

    private static int requiredEmeralds(ItemStack result, int villagerLevel) {
        if (result.getItem() instanceof ArmorItem armor) {
            if (armor.getEquipmentSlot() == EquipmentSlot.CHEST && result.isEnchanted()) return 192;
            if (result.isEnchanted()) return 128;
            if (villagerLevel >= 4) return 96;
        }
        if (result.is(Items.ENCHANTED_BOOK)) return 126;
        if (villagerLevel >= 5) return 64;
        if (villagerLevel >= 4) return 32;
        return 0;
    }
}
