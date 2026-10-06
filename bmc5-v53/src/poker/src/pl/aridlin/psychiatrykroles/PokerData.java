package pl.aridlin.psychiatrykroles;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.core.HolderLookup;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

final class PokerData extends SavedData {
    private static final String FILE_NAME = "psychiatryk_poker";
    private final Map<String, PokerGame> tables = new LinkedHashMap<>();
    private final Map<UUID, Long> wallets = new LinkedHashMap<>();
    private final Map<UUID, Long> legacyWallets = new LinkedHashMap<>();
    private final Map<Long, StockLot> stock = new LinkedHashMap<>();
    private long nextStockId = 1, stockRevision;
    static final int MAX_LOTS = 4096;
    record StockView(long id, net.minecraft.world.item.ItemStack sample, long count, long unit) {}
    private static final class StockLot {
        final long id, unit; final net.minecraft.world.item.ItemStack sample; long count;
        StockLot(long id, net.minecraft.world.item.ItemStack sample, long count, long unit) {
            this.id=id;this.sample=sample.copyWithCount(1);this.count=count;this.unit=unit;
        }
    }

    private final java.util.Set<UUID> pendingGuiItems = new java.util.HashSet<>();

    static PokerData get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(
            new SavedData.Factory<>(PokerData::new, PokerData::load), FILE_NAME);
    }

    static PokerData load(CompoundTag tag, HolderLookup.Provider registries) {
        PokerData data = new PokerData();
        if(tag.getInt("EscrowVersion")>1)throw new IllegalStateException("Unsupported escrow data version");
        for (Tag raw : tag.getList("Tables", Tag.TAG_COMPOUND)) {
            PokerGame game = PokerGame.load((CompoundTag) raw);
            if (!game.id().isBlank()) data.tables.put(game.id(), game);
        }
        for (Tag raw : tag.getList("Wallets", Tag.TAG_COMPOUND)) {
            CompoundTag value = (CompoundTag) raw;
            if (value.hasUUID("Player") && value.getLong("Chips") > 0)
                (tag.getInt("EscrowVersion") >= 1 ? data.wallets : data.legacyWallets).put(value.getUUID("Player"), value.getLong("Chips"));
        }
        for (Tag raw : tag.getList("PendingGuiItems", Tag.TAG_STRING)) {
            try { data.pendingGuiItems.add(UUID.fromString(raw.getAsString())); }
            catch (IllegalArgumentException ignored) { }
        }
        for (Tag raw : tag.getList("LegacyWallets", Tag.TAG_COMPOUND)) {
            CompoundTag value=(CompoundTag)raw;
            if(value.hasUUID("Player")&&value.getLong("Chips")>0)data.legacyWallets.put(value.getUUID("Player"),value.getLong("Chips"));
        }
        for(Tag raw:tag.getList("EscrowStock", Tag.TAG_COMPOUND)) {
            if(registries==null)throw new IllegalStateException("Stock requires a registry-aware load");
            var value=(CompoundTag)raw;long id=value.getLong("Id"),count=value.getLong("Count"),unit=value.getLong("UnitEMC");
            var item=net.minecraft.world.item.ItemStack.parse(registries,value.getCompound("Item")).orElseThrow(()->new IllegalStateException("Cannot decode escrow item"));
            if(id<=0||count<=0||unit<=0||item.isEmpty()||data.stock.containsKey(id)||data.stock.size()>=MAX_LOTS)throw new IllegalStateException("Invalid escrow stock");
            Math.multiplyExact(count,unit);data.stock.put(id,new StockLot(id,item,count,unit));data.nextStockId=Math.max(data.nextStockId,Math.addExact(id,1));
        }
        if(tag.getInt("EscrowVersion")<1){for(var game:data.tables.values())game.quarantineLegacyReserve();data.setDirty();}
        data.checkBacking();
        return data;
    }

    static PokerData load(CompoundTag tag) { return load(tag, null); }

    Collection<PokerGame> tables() { return tables.values(); }
    PokerGame table(String id) { return tables.get(id.toLowerCase()); }

    PokerGame tableFor(UUID player) {
        return tables.values().stream().filter(table -> table.player(player) != null).findFirst().orElse(null);
    }

    long balance(UUID player) { return wallets.getOrDefault(player, 0L); }

    void credit(UUID player, long amount) {
        if (amount <= 0) throw new IllegalArgumentException("amount");
        long updated=Math.addExact(balance(player),amount);
        if(Math.addExact(liabilities(),amount)>stockValue())throw new IllegalStateException("Unbacked poker credit rejected");
        wallets.put(player,updated);setDirty();
    }

    boolean debit(UUID player, long amount) {
        if (amount <= 0 || balance(player) < amount) return false;
        long remaining = balance(player) - amount;
        if (remaining == 0) wallets.remove(player); else wallets.put(player, remaining);
        setDirty(); return true;
    }

    long stockRevision(){return stockRevision;}
    java.util.List<StockView> stock(){return stock.values().stream().map(l->new StockView(l.id,l.sample.copy(),l.count,l.unit)).toList();}
    long stockValue(){long value=0;for(var lot:stock.values())value=Math.addExact(value,Math.multiplyExact(lot.count,lot.unit));return value;}
    long liabilities(){long value=0;for(long balance:wallets.values())value=Math.addExact(value,balance);for(var game:tables.values())value=Math.addExact(value,game.redeemableReserve());return value;}
    void checkBacking(){if(liabilities()>stockValue())throw new IllegalStateException("Poker liabilities exceed deposited stock");}
    long deposit(UUID player,net.minecraft.world.item.ItemStack held,int count,long unit){
        if(held.isEmpty()||count<=0||count>held.getCount()||unit<=0)throw new IllegalArgumentException("Invalid deposit");
        long credit=Math.multiplyExact(unit,count),updated=Math.addExact(balance(player),credit);
        StockLot matching=null;for(var lot:stock.values())if(lot.unit==unit&&net.minecraft.world.item.ItemStack.isSameItemSameComponents(lot.sample,held)){matching=lot;break;}
        if(matching==null&&stock.size()>=MAX_LOTS)throw new IllegalStateException("Escrow stock is full");
        long updatedCount=matching==null?count:Math.addExact(matching.count,count);Math.multiplyExact(updatedCount,unit);Math.addExact(stockValue(),credit);
        if(matching==null&&nextStockId==Long.MAX_VALUE)throw new IllegalStateException("Escrow ID exhausted");
        var fresh=matching==null?new StockLot(nextStockId,held,count,unit):null;
        // No callbacks or fallible decoding after this commit; all mutations run on the server thread.
        if(matching==null){stock.put(nextStockId++,fresh);}else matching.count=updatedCount;
        held.shrink(count);wallets.put(player,updated);stockRevision++;setDirty();return credit;
    }
    int withdraw(net.minecraft.server.level.ServerPlayer player,long id,int count){
        if(!player.getServer().isSameThread())throw new IllegalStateException("Poker exchange requires server thread");
        return withdraw(player.getUUID(),id,count,player.getInventory().items);
    }
    /** Package-private transaction entry also drives real ItemStack conservation QA. */
    int withdraw(UUID player,long id,int count,java.util.List<net.minecraft.world.item.ItemStack> inventory){
        var lot=stock.get(id);if(lot==null||count<=0||count>2304||lot.count<count)return 0;
        long cost=Math.multiplyExact(lot.unit,count);if(balance(player)<cost)return 0;
        var plan=PokerInventoryPlan.plan(inventory,lot.sample,count);if(plan==null)return 0;
        long left=balance(player)-cost;
        if(left==0)wallets.remove(player);else wallets.put(player,left);
        lot.count-=count;if(lot.count==0)stock.remove(id);
        for(int i=0;i<inventory.size();i++)inventory.set(i,plan.get(i));
        stockRevision++;setDirty();return count;
    }

    PokerGame create(String id, UUID owner, String ownerName) {
        String normalized = id.toLowerCase();
        if (tables.containsKey(normalized)) throw new PokerGame.PokerException("table-exists");
        PokerGame game = new PokerGame(normalized, owner);
        game.join(owner, ownerName);
        tables.put(normalized, game);
        setDirty();
        return game;
    }

    boolean remove(String id) {
        boolean removed = tables.remove(id.toLowerCase()) != null;
        if (removed) setDirty();
        return removed;
    }

    void queueGuiItem(UUID player) { pendingGuiItems.add(player); setDirty(); }
    boolean hasPendingGuiItem(UUID player) { return pendingGuiItems.contains(player); }
    void deliveredGuiItem(UUID player) { pendingGuiItems.remove(player); setDirty(); }

    void changed() { setDirty(); }

    @Override public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        tag.putInt("EscrowVersion",1);
        ListTag lots=new ListTag();
        for(var lot:stock.values()){var value=new CompoundTag();value.putLong("Id",lot.id);value.putLong("Count",lot.count);value.putLong("UnitEMC",lot.unit);value.put("Item",lot.sample.save(registries));lots.add(value);}
        tag.put("EscrowStock",lots);
        ListTag old=new ListTag();legacyWallets.forEach((player,chips)->{var value=new CompoundTag();value.putUUID("Player",player);value.putLong("Chips",chips);old.add(value);});tag.put("LegacyWallets",old);
        ListTag values = new ListTag();
        tables.values().forEach(table -> values.add(table.save()));
        tag.put("Tables", values);
        ListTag walletTags = new ListTag();
        wallets.forEach((player, chips) -> { CompoundTag value = new CompoundTag(); value.putUUID("Player", player);
            value.putLong("Chips", chips); walletTags.add(value); });
        tag.put("Wallets", walletTags);
        ListTag deliveries = new ListTag();
        pendingGuiItems.forEach(id -> deliveries.add(net.minecraft.nbt.StringTag.valueOf(id.toString())));
        tag.put("PendingGuiItems", deliveries);
        return tag;
    }

    CompoundTag save(CompoundTag tag) { return save(tag, null); }
}
