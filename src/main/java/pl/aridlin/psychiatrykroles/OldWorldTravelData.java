package pl.aridlin.psychiatrykroles;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Per-admin return positions live in the current S23 world, never in the imported world. */
final class OldWorldTravelData extends SavedData {
    private static final String FILE_NAME = "psychiatryk_old_world_travel";
    private final Map<UUID, ReturnPosition> returns = new HashMap<>();

    record ReturnPosition(String dimension, double x, double y, double z, float yaw, float pitch) {
        boolean valid() {
            return dimension != null && !dimension.isBlank()
                && Double.isFinite(x) && Double.isFinite(y) && Double.isFinite(z)
                && Float.isFinite(yaw) && Float.isFinite(pitch)
                && Math.abs(x) <= 29_999_984.0 && Math.abs(z) <= 29_999_984.0
                && y >= -2048.0 && y <= 2048.0;
        }
    }

    static OldWorldTravelData get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(
            new SavedData.Factory<>(OldWorldTravelData::new, OldWorldTravelData::load), FILE_NAME);
    }

    static OldWorldTravelData load(CompoundTag tag, HolderLookup.Provider registries) {
        OldWorldTravelData data = new OldWorldTravelData();
        for (Tag raw : tag.getList("Returns", Tag.TAG_COMPOUND)) {
            CompoundTag entry = (CompoundTag) raw;
            try {
                UUID playerId = UUID.fromString(entry.getString("Player"));
                ReturnPosition position = new ReturnPosition(entry.getString("Dimension"),
                    entry.getDouble("X"), entry.getDouble("Y"), entry.getDouble("Z"),
                    entry.getFloat("Yaw"), entry.getFloat("Pitch"));
                if (position.valid()) data.returns.put(playerId, position);
            } catch (IllegalArgumentException ignored) {
            }
        }
        return data;
    }

    static OldWorldTravelData load(CompoundTag tag) { return load(tag, null); }

    ReturnPosition getReturn(UUID playerId) { return returns.get(playerId); }

    void remember(UUID playerId, ReturnPosition position) {
        if (!position.valid()) throw new IllegalArgumentException("Invalid return position");
        returns.put(playerId, position);
        setDirty();
    }

    void forget(UUID playerId) {
        if (returns.remove(playerId) != null) setDirty();
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        ListTag entries = new ListTag();
        returns.forEach((playerId, position) -> {
            CompoundTag entry = new CompoundTag();
            entry.putString("Player", playerId.toString());
            entry.putString("Dimension", position.dimension());
            entry.putDouble("X", position.x());
            entry.putDouble("Y", position.y());
            entry.putDouble("Z", position.z());
            entry.putFloat("Yaw", position.yaw());
            entry.putFloat("Pitch", position.pitch());
            entries.add(entry);
        });
        tag.put("Returns", entries);
        return tag;
    }

    CompoundTag save(CompoundTag tag) { return save(tag, null); }
}
