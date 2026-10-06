package pl.aridlin.parties;
import java.util.*;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.*;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

public final class PartyData extends SavedData {
    public record Waypoint(int party, String name, String dimension, double x, double y, double z, UUID creator) {}
    final List<Waypoint> waypoints = new ArrayList<>();
    final Set<Integer> friendlyFire = new HashSet<>();
    final Set<UUID> chatMode = new HashSet<>();
    public static PartyData get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(new SavedData.Factory<>(PartyData::new, PartyData::load), "goplanska_parties");
    }
    public List<Waypoint> points(int party) { return party <= 0 ? List.of() : waypoints.stream().filter(w -> w.party == party).toList(); }
    static PartyData load(CompoundTag tag, HolderLookup.Provider lookup) {
        PartyData d = new PartyData();
        for (Tag raw : tag.getList("Waypoints", Tag.TAG_COMPOUND)) {
            CompoundTag t = (CompoundTag)raw;
            try {
                Waypoint w = new Waypoint(t.getInt("Party"),t.getString("Name"),t.getString("Dimension"),t.getDouble("X"),t.getDouble("Y"),t.getDouble("Z"),t.getUUID("Creator"));
                if (w.party > 0 && net.minecraft.resources.ResourceLocation.tryParse(w.dimension) != null && Double.isFinite(w.x) && Double.isFinite(w.y) && Double.isFinite(w.z)) d.waypoints.add(w);
            } catch (IllegalArgumentException ignored) {}
        }
        for (int id : tag.getIntArray("FriendlyFire")) if (id > 0) d.friendlyFire.add(id);
        for (Tag raw : tag.getList("ChatMode", Tag.TAG_STRING)) try { d.chatMode.add(UUID.fromString(raw.getAsString())); } catch (IllegalArgumentException ignored) {}
        return d;
    }
    @Override public CompoundTag save(CompoundTag tag, HolderLookup.Provider lookup) {
        ListTag points = new ListTag();
        for (Waypoint w : waypoints) {
            CompoundTag t = new CompoundTag(); t.putInt("Party",w.party);t.putString("Name",w.name);t.putString("Dimension",w.dimension);
            t.putDouble("X",w.x);t.putDouble("Y",w.y);t.putDouble("Z",w.z);t.putUUID("Creator",w.creator);points.add(t);
        }
        tag.put("Waypoints",points);tag.putIntArray("FriendlyFire",friendlyFire.stream().mapToInt(Integer::intValue).toArray());
        ListTag mode = new ListTag();chatMode.forEach(id -> mode.add(StringTag.valueOf(id.toString())));tag.put("ChatMode",mode);return tag;
    }
}
