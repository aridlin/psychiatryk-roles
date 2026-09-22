package pl.aridlin.psychiatrykroles;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
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

    static PokerData get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(PokerData::load, PokerData::new, FILE_NAME);
    }

    static PokerData load(CompoundTag tag) {
        PokerData data = new PokerData();
        for (Tag raw : tag.getList("Tables", Tag.TAG_COMPOUND)) {
            PokerGame game = PokerGame.load((CompoundTag) raw);
            if (!game.id().isBlank()) data.tables.put(game.id(), game);
        }
        for (Tag raw : tag.getList("Wallets", Tag.TAG_COMPOUND)) {
            CompoundTag value = (CompoundTag) raw;
            if (value.hasUUID("Player") && value.getLong("Chips") > 0)
                data.wallets.put(value.getUUID("Player"), value.getLong("Chips"));
        }
        return data;
    }

    Collection<PokerGame> tables() { return tables.values(); }
    PokerGame table(String id) { return tables.get(id.toLowerCase()); }

    PokerGame tableFor(UUID player) {
        return tables.values().stream().filter(table -> table.player(player) != null).findFirst().orElse(null);
    }

    long balance(UUID player) { return wallets.getOrDefault(player, 0L); }

    void credit(UUID player, long amount) {
        if (amount <= 0) throw new IllegalArgumentException("amount");
        wallets.put(player, Math.addExact(balance(player), amount)); setDirty();
    }

    boolean debit(UUID player, long amount) {
        if (amount <= 0 || balance(player) < amount) return false;
        long remaining = balance(player) - amount;
        if (remaining == 0) wallets.remove(player); else wallets.put(player, remaining);
        setDirty(); return true;
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

    void changed() { setDirty(); }

    @Override public CompoundTag save(CompoundTag tag) {
        ListTag values = new ListTag();
        tables.values().forEach(table -> values.add(table.save()));
        tag.put("Tables", values);
        ListTag walletTags = new ListTag();
        wallets.forEach((player, chips) -> { CompoundTag value = new CompoundTag(); value.putUUID("Player", player);
            value.putLong("Chips", chips); walletTags.add(value); });
        tag.put("Wallets", walletTags);
        return tag;
    }
}
