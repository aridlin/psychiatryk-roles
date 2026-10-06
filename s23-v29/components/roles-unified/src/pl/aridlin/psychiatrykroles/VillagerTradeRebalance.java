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
public final class VillagerTradeRebalance {
    VillagerTradeRebalance(){net.neoforged.neoforge.common.NeoForge.EVENT_BUS.register(new Wiesniuk());}
    static final String SNAPSHOT="GoplanskaOfferSnapshot",PROFESSION="GoplanskaOfferSnapshotProfession";
    static final String BASE="GoplanskaVanillaOffers";
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void onTradeTable(VillagerTradesEvent event) {
        for (int level = 1; level <= 5; level++) {
            List<VillagerTrades.ItemListing> listings = event.getTrades().get(level);
            if (listings == null) continue;
            final int tradeLevel = level;
            for (int index = 0; index < listings.size(); index++) {
                VillagerTrades.ItemListing original = unwrap(listings.get(index));
                listings.set(index,new WrappedListing(original,tradeLevel));
            }
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void onVillagerOpen(PlayerInteractEvent.EntityInteract event) {
        if (event.getLevel().isClientSide() || !(event.getTarget() instanceof Villager villager)) return;
        if(villager.isTrading()&&villager.getTradingPlayer()!=event.getEntity())return;
        restoreSnapshot(villager);
        MerchantOffers offers = villager.getOffers();
        int level = villager.getVillagerData().getLevel();
        for (int index = 0; index < offers.size(); index++) {
            MerchantOffer old = offers.get(index);
            MerchantOffer base = baseline(villager,old,index);
            boolean special=Wiesniuk.is(event.getEntity());
            MerchantOffer updated = special ? withStock(base,old,base.getMaxUses()*2) : withStock(reprice(base, level),old,base.getMaxUses());
            if (updated != old) offers.set(index, updated);
        }
        saveSnapshot(villager);
    }

    static MerchantOffer withStock(MerchantOffer cost,MerchantOffer state,int limit){var n=new MerchantOffer(cost.getItemCostA(),cost.getItemCostB(),state.getResult().copy(),state.getUses(),limit,state.getXp(),cost.getPriceMultiplier(),state.getDemand());n.setSpecialPriceDiff(state.getSpecialPriceDiff());return n;}
    static MerchantOffer baseline(Villager v,MerchantOffer old,int index){
        var ops=v.registryAccess().createSerializationContext(net.minecraft.nbt.NbtOps.INSTANCE);var tags=v.getPersistentData().getList(BASE,10);
        for(var tag:tags){var b=MerchantOffer.CODEC.parse(ops,tag).result();if(b.isPresent()&&ItemStack.isSameItemSameComponents(b.get().getResult(),old.getResult())&&b.get().getResult().getCount()==old.getResult().getCount()&&b.get().getXp()==old.getXp()&&(old.getBaseCostA().is(Items.EMERALD)||old.getBaseCostA().is(Items.EMERALD_BLOCK)||b.get().getBaseCostA().getItem()==old.getBaseCostA().getItem()))return b.get();}
        // Keep the saved offer when no vanilla baseline exists. Re-running listing factories
        // here mutates villager state and can replace or exhaust an existing trade.
        var b=old.copy();tags.add(MerchantOffer.CODEC.encodeStart(ops,b).getOrThrow());v.getPersistentData().put(BASE,tags);return b;
    }

    private record WrappedListing(VillagerTrades.ItemListing original,int level) implements VillagerTrades.ItemListing {
        public MerchantOffer getOffer(net.minecraft.world.entity.Entity trader,net.minecraft.util.RandomSource random){var offer=original.getOffer(trader,random);if(offer!=null){var tags=trader.getPersistentData().getList(BASE,10);tags.add(MerchantOffer.CODEC.encodeStart(trader.registryAccess().createSerializationContext(net.minecraft.nbt.NbtOps.INSTANCE),offer).getOrThrow());trader.getPersistentData().put(BASE,tags);}return reprice(offer,level);}
    }
    static VillagerTrades.ItemListing unwrap(VillagerTrades.ItemListing listing){while(listing instanceof WrappedListing w)listing=w.original();return listing;}
    static String profession(Villager v){return net.minecraft.core.registries.BuiltInRegistries.VILLAGER_PROFESSION.getKey(v.getVillagerData().getProfession()).toString();}
    public static void restoreSnapshot(Villager v){
        var data=v.getPersistentData();if(!profession(v).equals(data.getString(PROFESSION)))return;
        var offers=v.getOffers();var savedTags=data.getList(SNAPSHOT,10);if(offers.size()>=savedTags.size())return;var ops=v.registryAccess().createSerializationContext(net.minecraft.nbt.NbtOps.INSTANCE);
        for(var tag:savedTags){var saved=MerchantOffer.CODEC.parse(ops,tag).result();if(saved.isEmpty())continue;var b=saved.get();boolean exists=offers.stream().anyMatch(o->o.getResult().getItem()==b.getResult().getItem()&&o.getResult().getCount()==b.getResult().getCount()&&o.getXp()==b.getXp());if(!exists)offers.add(b);}
    }
    public static void saveSnapshot(Villager v){
        var list=new net.minecraft.nbt.ListTag();var ops=v.registryAccess().createSerializationContext(net.minecraft.nbt.NbtOps.INSTANCE);
        for(var offer:v.getOffers())list.add(MerchantOffer.CODEC.encodeStart(ops,offer).getOrThrow());
        v.getPersistentData().put(SNAPSHOT,list);v.getPersistentData().putString(PROFESSION,profession(v));
    }

    private static MerchantOffer reprice(MerchantOffer offer, int villagerLevel) {
        if (offer == null) return null;
        ItemStack originalCost = offer.getBaseCostA();
        if (!originalCost.is(Items.EMERALD)) return offer;
        ItemStack result = offer.getResult();
        int emeralds = Math.max(originalCost.getCount()*4,requiredEmeralds(result, villagerLevel));
        if (emeralds <= originalCost.getCount()) return offer;

        // A merchant has only two payment slots. 21 emerald blocks + 3 emeralds
        // is exactly three 64-item stacks, while remaining a normal trade.
        int blocks = emeralds / 9;
        int loose = emeralds % 9;
        var second = offer.getItemCostB();
        if(emeralds<=64){var changed=new MerchantOffer(new ItemCost(Items.EMERALD,emeralds),second,result.copy(),offer.getUses(),offer.getMaxUses(),offer.getXp(),0f,offer.getDemand());changed.setSpecialPriceDiff(0);return changed;}
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
