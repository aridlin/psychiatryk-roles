package pl.aridlin.psychiatrykroles;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.Collections;
import java.util.ArrayList;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

final class RoleData extends SavedData {
    private static final String FILE_NAME = "psychiatryk_roles";
    private final Set<UUID> patients = new LinkedHashSet<>();
    private final Set<String> codes = new LinkedHashSet<>();
    private final Map<UUID, TravelPosition> mainPositions = new LinkedHashMap<>();
    private final Map<UUID, TravelPosition> freedomPositions = new LinkedHashMap<>();
    private final Set<UUID> englishPlayers = new LinkedHashSet<>();
    private final Set<UUID> languagePlayers = new LinkedHashSet<>();
    private final Map<UUID, List<ChalkMarker>> chalkMarkers = new LinkedHashMap<>();
    private int baseSleepPercentage = -1;
    private final List<AuditEntry> auditLog = new ArrayList<>();

    record AuditEntry(long time, String actor, String action, String detail) {}

    record TravelPosition(String dimension, double x, double y, double z, float yaw, float pitch) {}

    record ChalkMarker(String dimension, double x, double y, double z, long expiresAt) {}

    record OwnedChalkMarker(UUID owner, ChalkMarker marker) {}

    static RoleData get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(RoleData::load, RoleData::new, FILE_NAME);
    }

    static RoleData load(CompoundTag tag) {
        RoleData data = new RoleData();
        ListTag patientTags = tag.getList("Patients", Tag.TAG_STRING);
        for (Tag value : patientTags) {
            try {
                data.patients.add(UUID.fromString(value.getAsString()));
            } catch (IllegalArgumentException ignored) {
            }
        }
        ListTag codeTags = tag.getList("Codes", Tag.TAG_STRING);
        for (Tag value : codeTags) {
            data.codes.add(value.getAsString());
        }
        loadPositions(tag.getList("MainPositions", Tag.TAG_COMPOUND), data.mainPositions);
        loadPositions(tag.getList("FreedomPositions", Tag.TAG_COMPOUND), data.freedomPositions);
        ListTag markerTags = tag.getList("ChalkMarkers", Tag.TAG_COMPOUND);
        for (Tag raw : markerTags) {
            CompoundTag value = (CompoundTag) raw;
            try {
                UUID owner = UUID.fromString(value.getString("Owner"));
                data.chalkMarkers.computeIfAbsent(owner, ignored -> new ArrayList<>()).add(new ChalkMarker(
                    value.getString("Dimension"), value.getDouble("X"), value.getDouble("Y"),
                    value.getDouble("Z"), value.getLong("ExpiresAt")
                ));
            } catch (IllegalArgumentException ignored) {
            }
        }
        ListTag englishTags = tag.getList("EnglishPlayers", Tag.TAG_STRING);
        for (Tag value : englishTags) {
            try {
                data.englishPlayers.add(UUID.fromString(value.getAsString()));
            } catch (IllegalArgumentException ignored) {
            }
        }
        ListTag languageTags = tag.getList("LanguagePlayers", Tag.TAG_STRING);
        for (Tag value : languageTags) {
            try {
                data.languagePlayers.add(UUID.fromString(value.getAsString()));
            } catch (IllegalArgumentException ignored) {
            }
        }
        if (tag.contains("BaseSleepPercentage", Tag.TAG_INT)) {
            data.baseSleepPercentage = tag.getInt("BaseSleepPercentage");
        }
        ListTag logTags = tag.getList("AuditLog", Tag.TAG_COMPOUND);
        for (Tag raw : logTags) {
            CompoundTag value = (CompoundTag) raw;
            data.auditLog.add(new AuditEntry(
                value.getLong("Time"), value.getString("Actor"),
                value.getString("Action"), value.getString("Detail")
            ));
        }
        return data;
    }

    private static void loadPositions(ListTag tags, Map<UUID, TravelPosition> destination) {
        for (Tag raw : tags) {
            CompoundTag value = (CompoundTag) raw;
            try {
                destination.put(UUID.fromString(value.getString("Player")), new TravelPosition(
                    value.getString("Dimension"), value.getDouble("X"), value.getDouble("Y"), value.getDouble("Z"),
                    value.getFloat("Yaw"), value.getFloat("Pitch")
                ));
            } catch (IllegalArgumentException ignored) {
            }
        }
    }

    boolean isPatient(UUID playerId) {
        return patients.contains(playerId);
    }

    void addPatient(UUID playerId) {
        if (patients.add(playerId)) {
            setDirty();
        }
    }

    void addCode(String code) {
        if (codes.add(code)) {
            setDirty();
        }
    }

    boolean consumeCode(String code) {
        boolean removed = codes.remove(code.toLowerCase());
        if (removed) {
            setDirty();
        }
        return removed;
    }

    Set<String> codes() {
        return Collections.unmodifiableSet(codes);
    }

    TravelPosition mainPosition(UUID playerId) {
        return mainPositions.get(playerId);
    }

    TravelPosition freedomPosition(UUID playerId) {
        return freedomPositions.get(playerId);
    }

    void setMainPosition(UUID playerId, TravelPosition position) {
        mainPositions.put(playerId, position);
        setDirty();
    }

    void setFreedomPosition(UUID playerId, TravelPosition position) {
        freedomPositions.put(playerId, position);
        setDirty();
    }

    boolean isEnglish(UUID playerId) {
        return englishPlayers.contains(playerId);
    }

    boolean hasLanguage(UUID playerId) {
        return languagePlayers.contains(playerId);
    }

    void setEnglish(UUID playerId, boolean english) {
        boolean changed = languagePlayers.add(playerId);
        changed |= english ? englishPlayers.add(playerId) : englishPlayers.remove(playerId);
        if (changed) {
            setDirty();
        }
    }

    void addChalkMarker(UUID playerId, ChalkMarker marker) {
        long now = System.currentTimeMillis();
        List<ChalkMarker> markers = chalkMarkers.computeIfAbsent(playerId, ignored -> new ArrayList<>());
        markers.removeIf(existing -> existing.expiresAt() <= now);
        while (markers.size() >= 5) {
            markers.remove(0);
        }
        markers.add(marker);
        setDirty();
    }

    List<OwnedChalkMarker> activeChalkMarkers(long now) {
        boolean changed = false;
        List<OwnedChalkMarker> result = new ArrayList<>();
        var iterator = chalkMarkers.entrySet().iterator();
        while (iterator.hasNext()) {
            var entry = iterator.next();
            changed |= entry.getValue().removeIf(marker -> marker.expiresAt() <= now);
            if (entry.getValue().isEmpty()) {
                iterator.remove();
                changed = true;
                continue;
            }
            entry.getValue().forEach(marker -> result.add(new OwnedChalkMarker(entry.getKey(), marker)));
        }
        if (changed) {
            setDirty();
        }
        return result;
    }

    int baseSleepPercentage(int currentValue) {
        if (baseSleepPercentage < 0) {
            baseSleepPercentage = currentValue;
            setDirty();
        }
        return baseSleepPercentage;
    }

    void addAudit(String actor, String action, String detail) {
        auditLog.add(new AuditEntry(System.currentTimeMillis(), actor, action, detail));
        while (auditLog.size() > 1000) {
            auditLog.remove(0);
        }
        setDirty();
    }

    List<AuditEntry> auditLog() {
        return List.copyOf(auditLog);
    }

    void clearAudit() {
        auditLog.clear();
        setDirty();
    }

    private static ListTag savePositions(Map<UUID, TravelPosition> positions) {
        ListTag tags = new ListTag();
        positions.forEach((player, position) -> {
            CompoundTag value = new CompoundTag();
            value.putString("Player", player.toString());
            value.putString("Dimension", position.dimension());
            value.putDouble("X", position.x());
            value.putDouble("Y", position.y());
            value.putDouble("Z", position.z());
            value.putFloat("Yaw", position.yaw());
            value.putFloat("Pitch", position.pitch());
            tags.add(value);
        });
        return tags;
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        ListTag patientTags = new ListTag();
        patients.stream().map(UUID::toString).sorted().map(StringTag::valueOf).forEach(patientTags::add);
        tag.put("Patients", patientTags);

        ListTag codeTags = new ListTag();
        codes.stream().sorted().map(StringTag::valueOf).forEach(codeTags::add);
        tag.put("Codes", codeTags);
        tag.put("MainPositions", savePositions(mainPositions));
        tag.put("FreedomPositions", savePositions(freedomPositions));
        ListTag markerTags = new ListTag();
        chalkMarkers.forEach((owner, markers) -> markers.forEach(marker -> {
            CompoundTag value = new CompoundTag();
            value.putString("Owner", owner.toString());
            value.putString("Dimension", marker.dimension());
            value.putDouble("X", marker.x());
            value.putDouble("Y", marker.y());
            value.putDouble("Z", marker.z());
            value.putLong("ExpiresAt", marker.expiresAt());
            markerTags.add(value);
        }));
        tag.put("ChalkMarkers", markerTags);
        ListTag englishTags = new ListTag();
        englishPlayers.stream().map(UUID::toString).sorted().map(StringTag::valueOf).forEach(englishTags::add);
        tag.put("EnglishPlayers", englishTags);
        ListTag languageTags = new ListTag();
        languagePlayers.stream().map(UUID::toString).sorted().map(StringTag::valueOf).forEach(languageTags::add);
        tag.put("LanguagePlayers", languageTags);
        tag.putInt("BaseSleepPercentage", baseSleepPercentage);
        ListTag logTags = new ListTag();
        for (AuditEntry entry : auditLog) {
            CompoundTag value = new CompoundTag();
            value.putLong("Time", entry.time());
            value.putString("Actor", entry.actor());
            value.putString("Action", entry.action());
            value.putString("Detail", entry.detail());
            logTags.add(value);
        }
        tag.put("AuditLog", logTags);
        return tag;
    }
}
