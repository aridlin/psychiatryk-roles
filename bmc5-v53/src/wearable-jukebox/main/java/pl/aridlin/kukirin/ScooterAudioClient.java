package pl.aridlin.kukirin;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.sounds.AudioStream;
import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import pl.aridlin.psychiatrykroles.jukebox.WearableJukebox;

@EventBusSubscriber(modid = "goplanska_kukirin", value = Dist.CLIENT)
public final class ScooterAudioClient {
    private static final class Transfer {
        final UUID session;
        final byte[] bytes;
        int offset;
        long touched;
        Transfer(ScooterAudioPacket packet) { session = packet.session(); bytes = new byte[packet.total()]; }
    }
    private record Ready(UUID session, ScooterWav.Pcm pcm, ClientLevel level, long expires) {}
    private static final Map<UUID, Transfer> transfers = new HashMap<>();
    private static final Map<UUID, Moving> sounds = new HashMap<>();
    private static final Map<UUID, UUID> decoding = new HashMap<>();
    private static final Map<UUID, Integer> discs = new HashMap<>();
    private static final Map<UUID, ScooterAudioUrlPacket> urls = new HashMap<>();
    private static final Map<UUID, Ready> ready = new HashMap<>();
    private static final Map<UUID, UUID> finished = new LinkedHashMap<>() {
        @Override protected boolean removeEldestEntry(Map.Entry<UUID, UUID> entry) { return size() > 256; }
    };
    private static ClientLevel lastLevel;

    public static void receive(ScooterAudioPacket packet) {
        var mc = Minecraft.getInstance();
        syncLevel(mc);
        UUID id = packet.scooter();
        if (packet.total() == 0) {
            if (packet.session().equals(finished.get(id))) finished.remove(id);
            var pending = urls.get(id);
            if (pending != null && pending.session().equals(packet.session())) urls.remove(id);
            var transfer = transfers.get(id);
            if (transfer != null && transfer.session.equals(packet.session())) transfers.remove(id);
            if (packet.session().equals(decoding.get(id))) decoding.remove(id);
            var waiting = ready.get(id);
            if (waiting != null && waiting.session.equals(packet.session())) ready.remove(id);
            var sound = sounds.get(id);
            if (sound != null && packet.session().equals(sound.session)) remove(id);
            return;
        }
        if (packet.total() < 44 || packet.total() > ScooterMusic.MAX_BYTES || packet.offset() < 0
            || packet.bytes().length == 0 || packet.bytes().length > 32768
            || (long) packet.offset() + packet.bytes().length > packet.total()) return;
        var transfer = transfers.get(id);
        if (packet.offset() == 0) {
            urls.remove(id);
            ready.remove(id);
            if (transfers.size() + decoding.size() >= 4 && !transfers.containsKey(id)) return;
            transfer = new Transfer(packet);
            transfers.put(id, transfer);
        }
        if (transfer == null || !transfer.session.equals(packet.session()) || transfer.offset != packet.offset()
            || transfer.bytes.length != packet.total()) return;
        System.arraycopy(packet.bytes(), 0, transfer.bytes, transfer.offset, packet.bytes().length);
        transfer.offset += packet.bytes().length;
        transfer.touched = System.nanoTime();
        if (transfer.offset != transfer.bytes.length) return;
        transfers.remove(id);
        decoding.put(id, packet.session());
        byte[] wav = transfer.bytes;
        var level = mc.level;
        CompletableFuture.supplyAsync(() -> {
            try { return ScooterWav.decode(wav); }
            catch (Exception error) { throw new java.util.concurrent.CompletionException(error); }
        }).whenComplete((pcm, error) -> mc.execute(() -> {
            if (!packet.session().equals(decoding.get(id)) || mc.level != level) return;
            decoding.remove(id);
            if (error != null) {
                org.slf4j.LoggerFactory.getLogger("ScooterAudio").warn("WAV decoding failed ({}).", error.getClass().getSimpleName());
                if (mc.player != null) mc.player.displayClientMessage(net.minecraft.network.chat.Component.literal("Scooter WAV could not be decoded."), false);
                return;
            }
            var entity = find(id);
            if (entity instanceof Scooter scooter) {
                remove(id);
                var sound = new Moving(scooter, packet.session(), pcm);
                sounds.put(id, sound);
                mc.getSoundManager().play(sound);
            }
        }));
    }

    private static Entity find(UUID id) {
        var level = Minecraft.getInstance().level;
        if (level == null) return null;
        var player = level.getPlayerByUUID(id);
        if (player != null) return player;
        for (var entity : level.entitiesForRendering())
            if (entity instanceof Scooter && entity.getUUID().equals(id)) return entity;
        return null;
    }

    private static void remove(UUID id) {
        var old = sounds.remove(id);
        if (old != null) { old.cancel(); Minecraft.getInstance().getSoundManager().stop(old); }
    }

    @SubscribeEvent
    public static void tick(net.neoforged.neoforge.client.event.ClientTickEvent.Post event) {
        var mc = Minecraft.getInstance();
        syncLevel(mc);
        if (mc.level == null) return;
        long now = System.nanoTime();
        transfers.entrySet().removeIf(entry -> now - entry.getValue().touched > 30_000_000_000L);
        sounds.entrySet().removeIf(entry -> entry.getValue().isStopped());
        for (var id : new ArrayList<>(ready.keySet())) startReady(id);
        for (var entity : mc.level.entitiesForRendering()) if (entity instanceof Scooter scooter) {
            int serial = scooter.discSerial();
            if (discs.getOrDefault(scooter.getUUID(), 0) == serial) continue;
            discs.put(scooter.getUUID(), serial);
            var old = sounds.get(scooter.getUUID());
            if (old != null && old.pcm == null) remove(scooter.getUUID());
            if (!scooter.discPlaying()) continue;
            urls.remove(scooter.getUUID());
            ready.remove(scooter.getUUID());
            decoding.remove(scooter.getUUID());
            remove(scooter.getUUID());
            var stack = ScooterMusic.disc(scooter.getItemBySlot(net.minecraft.world.entity.EquipmentSlot.FEET), scooter);
            var playable = stack.get(net.minecraft.core.component.DataComponents.JUKEBOX_PLAYABLE);
            if (playable != null) playable.song().unwrap(scooter.registryAccess()).ifPresent(holder -> {
                var sound = new Moving(scooter, holder.value().soundEvent().value(), holder.value().lengthInTicks());
                sounds.put(scooter.getUUID(), sound);
                mc.getSoundManager().play(sound);
            });
        }
    }

    private static void syncLevel(Minecraft mc) {
        if (mc.level == lastLevel) return;
        for (var id : new ArrayList<>(sounds.keySet())) remove(id);
        transfers.clear(); decoding.clear(); discs.clear(); urls.clear(); ready.clear(); finished.clear();
        lastLevel = mc.level;
    }

    /** Every completion is session- and level-guarded before it can start a sound. */
    public static void receive(ScooterAudioUrlPacket packet) {
        var mc = Minecraft.getInstance();
        syncLevel(mc);
        if (mc.level == null) return;
        UUID id = packet.source();
        if (packet.session().equals(finished.get(id))) return;
        finished.remove(id);
        var old = sounds.get(id);
        if (old != null && packet.session().equals(old.session)) { old.loopOverride = packet.loop(); return; }
        var pending = urls.get(id);
        if (pending != null && pending.session().equals(packet.session())) {
            urls.put(id, packet);
            startReady(id);
            return;
        }
        var active = new HashSet<UUID>(sounds.keySet());
        active.addAll(decoding.keySet()); active.addAll(transfers.keySet()); active.addAll(urls.keySet()); active.addAll(ready.keySet());
        if (active.size() >= 4 && !active.contains(id)) return;
        if (packet.block() && !(mc.level.getBlockEntity(packet.position()) instanceof net.minecraft.world.level.block.entity.JukeboxBlockEntity)) return;
        remove(id); transfers.remove(id); ready.remove(id);
        decoding.put(id, packet.session()); urls.put(id, packet);
        var level = mc.level;
        var cache = net.neoforged.fml.loading.FMLPaths.GAMEDIR.get().resolve("cache/goplanska-scooter/music");
        (packet.url().startsWith("bundled:") ? bundled(packet)
            : ScooterMusicHttpCache.fetch(cache, packet.url(), packet.bearer(), packet.sha256(), packet.bytes()))
            .thenApplyAsync(bytes -> {
                try { return ScooterWav.decode(bytes); }
                catch (Exception error) { throw new java.util.concurrent.CompletionException(error); }
            }).whenComplete((pcm, error) -> mc.execute(() -> {
                var current = urls.get(id);
                if (current == null || !current.session().equals(packet.session())
                    || !packet.session().equals(decoding.get(id)) || mc.level != level) return;
                decoding.remove(id);
                if (error != null) {
                    urls.remove(id);
                    finished.put(id, packet.session());
                    if (mc.player != null) mc.player.displayClientMessage(net.minecraft.network.chat.Component.literal("Song download failed; reopen Music to retry."), false);
                    return;
                }
                // Entity tracking and Accessories sync can arrive after the audio metadata.
                // Keep at most four decoded sessions for 30 seconds, then rely on the
                // server's five-second wearer metadata heartbeat for a cached retry.
                ready.put(id, new Ready(packet.session(), pcm, level, System.nanoTime() + 30_000_000_000L));
                startReady(id);
            }));
    }

    private static void startReady(UUID id) {
        var waiting = ready.get(id);
        if (waiting == null) return;
        var mc = Minecraft.getInstance();
        var packet = urls.get(id);
        if (packet == null || !waiting.session.equals(packet.session()) || mc.level != waiting.level
            || System.nanoTime() > waiting.expires) {
            ready.remove(id);
            if (packet != null && waiting.session.equals(packet.session())) urls.remove(id);
            return;
        }
        var entity = packet.block() ? null : find(id);
        if (!packet.block() && (entity == null || entity instanceof Player player && WearableJukebox.equipped(player).isEmpty())) return;
        if (packet.block() && !(waiting.level.getBlockEntity(packet.position()) instanceof net.minecraft.world.level.block.entity.JukeboxBlockEntity)) {
            ready.remove(id); urls.remove(id); return;
        }
        ready.remove(id); urls.remove(id);
        var sound = new Moving(id, entity, waiting.level, packet.block() ? packet.position() : null,
            waiting.session, waiting.pcm, packet.loop());
        sounds.put(id, sound);
        mc.getSoundManager().play(sound);
    }

    private static CompletableFuture<byte[]> bundled(ScooterAudioUrlPacket packet) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                String name = packet.url().substring(8);
                if (!name.equals("chiki_ride.wav") && !name.equals("night_motor.wav")) throw new java.io.IOException();
                byte[] bytes;
                try (var stream = ScooterAudioClient.class.getResourceAsStream("/data/goplanska_kukirin/music/" + name)) {
                    if (stream == null) throw new java.io.IOException();
                    bytes = stream.readNBytes(ScooterMusic.MAX_BYTES + 1);
                }
                if (!ScooterMusicHttpCache.valid(bytes, packet.sha256(), packet.bytes())) throw new java.io.IOException();
                return bytes;
            } catch (Exception error) {
                throw new java.util.concurrent.CompletionException(new java.io.IOException("Bundled song unavailable"));
            }
        });
    }

    /** Mono spatial BLOCKS audio, with position updated independently of reverb processing. */
    public static final class Moving extends AbstractTickableSoundInstance {
        final UUID source;
        final Entity entity;
        final Scooter scooter;
        final ClientLevel level;
        final BlockPos block;
        final UUID session;
        final ScooterWav.Pcm pcm;
        final int duration;
        Boolean loopOverride;
        int age;
        private volatile boolean active = true;

        Moving(Scooter scooter, UUID session, ScooterWav.Pcm pcm) {
            this(scooter.getUUID(), scooter, (ClientLevel) scooter.level(), null, session, pcm, null);
        }
        Moving(UUID source, Entity entity, ClientLevel level, BlockPos block, UUID session, ScooterWav.Pcm pcm, Boolean loop) {
            super(Kukirin.WAV.get(), SoundSource.BLOCKS, net.minecraft.util.RandomSource.create());
            this.source = source; this.entity = entity; this.scooter = entity instanceof Scooter value ? value : null;
            this.level = level; this.block = block; this.session = session; this.pcm = pcm; loopOverride = loop;
            duration = (int) Math.ceil(pcm.samples().length / pcm.format().getFrameRate() / pcm.format().getFrameSize() * 20);
            relative = false; looping = false; position(); volume = ScooterClientOptions.get().musicVolume;
        }
        Moving(Scooter scooter, net.minecraft.sounds.SoundEvent event, int duration) {
            super(event, SoundSource.BLOCKS, net.minecraft.util.RandomSource.create());
            source = scooter.getUUID(); entity = scooter; this.scooter = scooter; level = (ClientLevel) scooter.level();
            block = null; session = null; pcm = null; this.duration = duration;
            relative = false; looping = false; position(); volume = ScooterClientOptions.get().musicVolume;
        }
        private void position() {
            if (entity instanceof Player player) {
                double yaw = Math.toRadians(player.yBodyRot);
                x = player.getX() + Math.sin(yaw) * .24;
                y = player.getY() + player.getBbHeight() * .62;
                z = player.getZ() - Math.cos(yaw) * .24;
            } else if (entity != null) {
                x = entity.getX(); y = entity.getY() + .7; z = entity.getZ();
            } else {
                x = block.getX() + .5; y = block.getY() + .5; z = block.getZ() + .5;
            }
        }
        void cancel() { active = false; stop(); }
        public boolean isEntitySource() { return entity != null; }
        /** Safe to inspect on the sound executor; cancellation publishes before channel release. */
        public boolean physicsActive() { return active; }

        @Override public void tick() {
            if (isStopped() || sounds.get(source) != this) { cancel(); return; }
            boolean valid = entity != null ? !entity.isRemoved() && entity.level() == level
                && (!(entity instanceof Player player) || player.isAlive() && !WearableJukebox.equipped(player).isEmpty())
                : level.getBlockEntity(block) instanceof net.minecraft.world.level.block.entity.JukeboxBlockEntity;
            if (Minecraft.getInstance().level != level || !valid) { cancel(); return; }
            if (++age >= duration) {
                cancel();
                boolean again = loopOverride != null ? loopOverride : scooter != null && ScooterMusic.looping(scooter);
                if (pcm != null && again) {
                    var next = new Moving(source, entity, level, block, session, pcm, loopOverride);
                    sounds.put(source, next);
                    Minecraft.getInstance().getSoundManager().queueTickingSound(next);
                } else if (pcm != null) {
                    // Wearer metadata heartbeats recover missing tracking, but must
                    // not replay a completed non-looping track in the same session.
                    finished.put(source, session);
                }
                return;
            }
            position();
            volume = ScooterClientOptions.get().audioEnabled ? ScooterClientOptions.get().musicVolume : 0;
        }
        @Override public CompletableFuture<AudioStream> getStream(net.minecraft.client.sounds.SoundBufferLibrary buffers,
            net.minecraft.client.resources.sounds.Sound sound, boolean looping) {
            return pcm == null ? super.getStream(buffers, sound, looping) : CompletableFuture.completedFuture(new PcmStream(pcm));
        }
    }

    static final class PcmStream implements AudioStream {
        private final ScooterWav.Pcm pcm;
        private int offset;
        PcmStream(ScooterWav.Pcm pcm) { this.pcm = pcm; }
        public javax.sound.sampled.AudioFormat getFormat() { return pcm.format(); }
        public java.nio.ByteBuffer read(int size) {
            int length = Math.min(size, pcm.samples().length - offset);
            var buffer = java.nio.ByteBuffer.allocateDirect(length);
            buffer.put(pcm.samples(), offset, length).flip(); offset += length; return buffer;
        }
        public void close() { offset = pcm.samples().length; }
    }
    private ScooterAudioClient() {}
}
