package pl.aridlin.psychiatrykroles;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

final class VoidDoorData extends SavedData {
    private static final String FILE_NAME = "psychiatryk_void_doors";
    private final Map<UUID, List<DoorPosition>> doors = new LinkedHashMap<>();

    record DoorPosition(String dimension, BlockPos pos) {}

    static VoidDoorData get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(VoidDoorData::load, VoidDoorData::new, FILE_NAME);
    }

    static VoidDoorData load(CompoundTag tag) {
        VoidDoorData data = new VoidDoorData();
        for (Tag raw : tag.getList("Doors", Tag.TAG_COMPOUND)) {
            CompoundTag entry = (CompoundTag) raw;
            try {
                UUID pair = UUID.fromString(entry.getString("Pair"));
                String dimension = entry.getString("Dimension");
                if (ResourceLocation.tryParse(dimension) == null) continue;
                data.addDoor(pair, new DoorPosition(dimension,
                    new BlockPos(entry.getInt("X"), entry.getInt("Y"), entry.getInt("Z"))));
            } catch (IllegalArgumentException ignored) {
            }
        }
        return data;
    }

    boolean addDoor(UUID pair, DoorPosition position) {
        if (pairAt(position.dimension(), position.pos()) != null) return false;
        List<DoorPosition> positions = doors.computeIfAbsent(pair, ignored -> new ArrayList<>());
        if (positions.contains(position) || positions.size() >= 2) return false;
        positions.add(position);
        setDirty();
        return true;
    }

    UUID pairAt(String dimension, BlockPos pos) {
        DoorPosition position = new DoorPosition(dimension, pos);
        for (var entry : doors.entrySet()) {
            if (entry.getValue().contains(position)) return entry.getKey();
        }
        return null;
    }

    List<DoorPosition> positions() {
        return doors.values().stream().flatMap(List::stream).toList();
    }

    boolean canPlace(UUID pair) {
        return doors.getOrDefault(pair, List.of()).size() < 2;
    }

    DoorPosition partner(String dimension, BlockPos pos) {
        DoorPosition current = new DoorPosition(dimension, pos);
        for (List<DoorPosition> positions : doors.values()) {
            if (positions.size() == 2 && positions.contains(current)) {
                return positions.get(0).equals(current) ? positions.get(1) : positions.get(0);
            }
        }
        return null;
    }

    boolean removeDoor(String dimension, BlockPos pos) {
        DoorPosition position = new DoorPosition(dimension, pos);
        var iterator = doors.entrySet().iterator();
        while (iterator.hasNext()) {
            List<DoorPosition> positions = iterator.next().getValue();
            if (positions.remove(position)) {
                if (positions.isEmpty()) iterator.remove();
                setDirty();
                return true;
            }
        }
        return false;
    }

    @Override public CompoundTag save(CompoundTag tag) {
        ListTag entries = new ListTag();
        doors.forEach((pair, positions) -> positions.forEach(position -> {
            CompoundTag entry = new CompoundTag();
            entry.putString("Pair", pair.toString());
            entry.putString("Dimension", position.dimension());
            entry.putInt("X", position.pos().getX());
            entry.putInt("Y", position.pos().getY());
            entry.putInt("Z", position.pos().getZ());
            entries.add(entry);
        }));
        tag.put("Doors", entries);
        return tag;
    }
}
