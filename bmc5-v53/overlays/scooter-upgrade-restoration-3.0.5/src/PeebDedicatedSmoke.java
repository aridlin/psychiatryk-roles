package pl.aridlin.peebdedicatedqa;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.mojang.authlib.GameProfile;
import io.netty.buffer.Unpooled;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.ConnectionProtocol;
import net.minecraft.network.Connection;
import net.minecraft.network.PacketSendListener;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ClientInformation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.server.players.PlayerList;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.network.registration.NetworkRegistry;
import pl.aridlin.psychiatrykroles.peeb.PeebActionPayload;
import pl.aridlin.psychiatrykroles.peeb.PeebConfig;
import pl.aridlin.psychiatrykroles.peeb.PeebConfigEditPayload;
import pl.aridlin.psychiatrykroles.peeb.PeebConfigPayload;
import pl.aridlin.psychiatrykroles.peeb.PeebGrapple;
import pl.aridlin.psychiatrykroles.peeb.PeebMode;
import pl.aridlin.kukirin.Scooter;
import pl.aridlin.releaseqa.EndgameLoyaltyQA;
import pl.aridlin.releaseqa.ScooterUpgradeQA;
import pl.aridlin.psychiatrykroles.peeb.PeebStatePayload;

@Mod("peeb_dedicated_qa")
public final class PeebDedicatedSmoke {
    private final JsonObject result = new JsonObject();
    private final JsonArray checks = new JsonArray();
    private boolean success = true;
    private int motionPackets;
    private int lastMotionEntityId = -1;

    public PeebDedicatedSmoke() {
        NeoForge.EVENT_BUS.addListener(this::started);
        NeoForge.EVENT_BUS.addListener(this::stopped);
    }

    private void check(boolean passed, String description) {
        JsonObject check = new JsonObject();
        check.addProperty("passed", passed);
        check.addProperty("description", description);
        checks.add(check);
        success &= passed;
        System.out.println("PEEB_DEDICATED_QA " + (passed ? "PASS " : "FAIL ") + description);
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private void payload(ServerStartedEvent event, CustomPacketPayload sample, PacketFlow flow) {
        var codec = NetworkRegistry.getCodec(sample.type().id(), ConnectionProtocol.PLAY, flow);
        check(codec != null, "actual PLAY registry contains " + sample.type().id() + " " + flow);
        if (codec == null) return;
        var buffer = new RegistryFriendlyByteBuf(Unpooled.buffer(), event.getServer().registryAccess());
        try {
            ((StreamCodec) codec).encode(buffer, sample);
            Object decoded = codec.decode(buffer);
            check(sample.equals(decoded) && !buffer.isReadable(), "registered codec roundtrip " + sample.type().id());
        } finally {
            buffer.release();
        }
    }

    private void nativeTick(ServerPlayer player) throws Exception {
        player.getServer().getWorldData().overworldData().setGameTime(player.serverLevel().getGameTime() + 5);
        var method = PeebGrapple.class.getDeclaredMethod("tick", PlayerTickEvent.Post.class);
        method.setAccessible(true);
        method.invoke(null, new PlayerTickEvent.Post(player));
    }

    private void combatAndScooter(ServerStartedEvent event, ServerPlayer player) throws Exception {
        var level = event.getServer().overworld();
        var enemy = EntityType.ZOMBIE.create(level);
        if (enemy == null) throw new AssertionError("Zombie fixture factory returned null");
        enemy.setNoAi(true);
        enemy.setSilent(true);
        enemy.getAttribute(Attributes.ARMOR).setBaseValue(0.0);
        enemy.setPos(16.5, 20.0, 20.0);
        enemy.setHealth(20.0f);
        level.addFreshEntity(enemy);
        player.setPos(16.5, 20.0, 16.5);
        player.setYRot(0.0f);
        player.yBodyRot = 0.0f;
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(PeebMode.PEEB.get()));
        nativeTick(player);
        try {
            check(PeebGrapple.attach(player, new Vec3(16.5, 21.0, 19.7)), "native visible living enemy attachment accepted");
            for (int tick = 0; tick < 40; tick++) nativeTick(player);
            check(Math.abs(PeebGrapple.snapshot(player).ropeLength() - 2.0) < 1.0E-6, "native enemy winch settles before moving-target sync case");
            Vec3 before = PeebGrapple.snapshot(player).anchor().orElseThrow();
            enemy.setPos(16.5, 20.0, 21.0);
            check(PeebGrapple.validTether(player), "connection validation refreshes moving enemy before physics tick");
            nativeTick(player);
            Vec3 after = PeebGrapple.snapshot(player).anchor().orElseThrow();
            check(Math.abs(after.z - before.z - 1.0) < 1.0E-6 && PeebGrapple.validTether(player), "native anchor follows the living target position");
            var sessionsField = PeebGrapple.class.getDeclaredField("SESSIONS");
            sessionsField.setAccessible(true);
            var nativeSession = ((Map<?, ?>)sessionsField.get(null)).get(player.getUUID());
            var syncedField = nativeSession.getClass().getDeclaredField("lastSyncedAnchor");
            syncedField.setAccessible(true);
            check(after.equals(syncedField.get(nativeSession)), "settled winch broadcasts refreshed moving-target state after connection validation");
            enemy.discard();
            check(!PeebGrapple.validTether(player), "removed living target invalidates tether");
            nativeTick(player);
            check(PeebGrapple.snapshot(player).anchor().isEmpty(), "normal server tick releases removed living target");
        } finally {
            enemy.discard();
            PeebGrapple.release(player);
        }

        var damageTarget = EntityType.ZOMBIE.create(level);
        if (damageTarget == null) throw new AssertionError("Contact target factory returned null");
        damageTarget.setNoAi(true);
        damageTarget.setSilent(true);
        damageTarget.getAttribute(Attributes.ARMOR).setBaseValue(0.0);
        damageTarget.setPos(16.5, 20.0, 20.0);
        damageTarget.setHealth(20.0f);
        level.addFreshEntity(damageTarget);
        try {
            check(PeebGrapple.attach(player, new Vec3(16.5, 21.0, 19.7)), "fresh enemy hook accepted for contact damage");
            check(damageTarget.getHealth() == 20.0f, "distant enemy hook deals no immediate damage");
            player.setPos(16.5, 20.0, 18.3);
            nativeTick(player);
            long firstHit = level.getGameTime();
            check(Math.abs(damageTarget.getHealth() - 16.0f) < .001f, "actual native contact attack deals wooden-sword base damage of four");
            damageTarget.invulnerableTime = 0;
            nativeTick(player);
            check(Math.abs(damageTarget.getHealth() - 16.0f) < .001f, "held enemy hook never repeats contact damage");
            PeebGrapple.release(player);
            check(PeebGrapple.attach(player, new Vec3(16.5, 21.0, 19.7)), "early repeat enemy hook attaches normally");
            damageTarget.invulnerableTime = 0;
            nativeTick(player);
            check(Math.abs(damageTarget.getHealth() - 16.0f) < .001f, "release and reattach cannot bypass player twenty-tick attack cooldown");
            PeebGrapple.release(player);
            event.getServer().getWorldData().overworldData().setGameTime(firstHit + 25);
            check(PeebGrapple.attach(player, new Vec3(16.5, 21.0, 19.7)), "repeat enemy hook accepted after cooldown");
            damageTarget.invulnerableTime = 0;
            nativeTick(player);
            check(Math.abs(damageTarget.getHealth() - 12.0f) < .001f, "new contact hook damages again after cooldown");
            PeebGrapple.release(player);

            var scoreboard = event.getServer().getScoreboard();
            var team = scoreboard.addPlayerTeam("peebqa-allies");
            try {
                scoreboard.addPlayerToTeam(player.getScoreboardName(), team);
                scoreboard.addPlayerToTeam(damageTarget.getScoreboardName(), team);
                nativeTick(player);
                check(!PeebGrapple.attach(player, new Vec3(16.5, 21.0, 19.7)), "native attachment rejects a living allied target");
            } finally {
                scoreboard.removePlayerTeam(team);
            }
        } finally {
            damageTarget.discard();
            PeebGrapple.release(player);
        }
        result.addProperty("combat_fixture", "Real living entities and hurt(playerAttack,4); zero armor and cleared vanilla hurt immunity isolate Peeb's own cooldown");

        var entity = BuiltInRegistries.ENTITY_TYPE.get(ResourceLocation.parse("goplanska_kukirin:scooter")).create(level);
        if (!(entity instanceof Scooter scooter)) throw new AssertionError("Registered native Scooter factory failed");
        scooter.setPos(16.5, 20.0, 16.5);
        scooter.setNoAi(true);
        scooter.batteryState(72000, false, false);
        level.addFreshEntity(scooter);
        BlockPos wall = null;
        net.minecraft.world.level.block.state.BlockState previous = null;
        try {
            player.setPos(16.5, 20.0, 16.5);
            nativeTick(player);
            check(player.startRiding(scooter, true), "native scooter mount accepted while holding Peeb");
            scooter.positionRider(player);
            check(PeebGrapple.isScooterRider(player), "common helper identifies the controlling root scooter rider");
            double anchorY = Math.floor(player.getEyePosition().y) + .45;
            Vec3 vehicleAnchor = new Vec3(player.getX(), anchorY, 20.0);
            wall = BlockPos.containing(vehicleAnchor.add(0.0, 0.0, .001));
            previous = level.getBlockState(wall);
            level.setBlockAndUpdate(wall, Blocks.STONE.defaultBlockState());
            check(PeebGrapple.attach(player, vehicleAnchor), "native Peeb attachment accepted from a scooter");
            check(PeebGrapple.validTether(player), "actual mounted scooter session remains valid");
            scooter.setDeltaMovement(.85, 0.0, .1);
            scooter.setOnGround(true);
            player.setDeltaMovement(Vec3.ZERO);
            int packetsBefore = motionPackets;
            nativeTick(player);
            Vec3 velocity = scooter.getDeltaMovement();
            check(Math.abs(velocity.x - .85) < .001 && velocity.z > .1, "server scooter winch preserves tangential vehicle momentum and adds pull");
            check(player.getDeltaMovement().equals(Vec3.ZERO), "mounted server winch applies no duplicate impulse to rider");
            check(motionPackets > packetsBefore && lastMotionEntityId == scooter.getId(), "authoritative native motion packet addresses root scooter");
            player.stopRiding();
            PeebGrapple.release(player);
        } finally {
            player.stopRiding();
            scooter.discard();
            if (wall != null && previous != null) level.setBlockAndUpdate(wall, previous);
        }
    }

    @SuppressWarnings("unchecked")
    private void retention(ServerStartedEvent event) throws Exception {
        var server = event.getServer();
        var level = server.overworld();
        var profile = new GameProfile(UUID.fromString("00000000-0000-4000-8000-000000000002"), "PeebDedicatedQA");
        var player = new ServerPlayer(server, level, profile, ClientInformation.createDefault());
        player.connection = new ServerGamePacketListenerImpl(server, new Connection(PacketFlow.SERVERBOUND), player,
            CommonListenerCookie.createInitial(profile, false)) {
            @Override public void send(Packet<?> packet) {
                if (packet instanceof ClientboundSetEntityMotionPacket motion) {
                    motionPackets++;
                    lastMotionEntityId = motion.getId();
                }
            }
            @Override public void send(Packet<?> packet, PacketSendListener listener) { send(packet); }
        };
        var membership = PlayerList.class.getDeclaredField("playersByUUID");
        membership.setAccessible(true);
        Map<UUID, ServerPlayer> players = (Map<UUID, ServerPlayer>) membership.get(server.getPlayerList());
        var sessionsField = PeebGrapple.class.getDeclaredField("SESSIONS");
        sessionsField.setAccessible(true);
        Map<UUID, ?> sessions = (Map<UUID, ?>) sessionsField.get(null);
        var support = new BlockPos(16, 21, 20);
        var occluder = new BlockPos(16, 21, 18);
        var supportBefore = level.getBlockState(support);
        var occluderBefore = level.getBlockState(occluder);
        Vec3 anchor = new Vec3(16.5, 21.45, 20.0);
        result.addProperty("retention_fixture", "Actual dedicated ServerPlayer and PeebGrapple methods; synthetic UUID membership and packet-send sink; no client connection");
        try {
            players.put(player.getUUID(), player);
            player.setGameMode(GameType.SURVIVAL);
            player.setPos(16.5, 20.0, 16.5);
            player.setYRot(0.0f);
            player.yBodyRot = 0.0f;
            player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(PeebMode.PEEB.get()));
            level.setBlockAndUpdate(support, Blocks.STONE.defaultBlockState());
            level.setBlockAndUpdate(occluder, Blocks.AIR.defaultBlockState());
            check(PeebGrapple.attach(player, anchor), "native visible in-range attachment accepted");
            check(PeebGrapple.validTether(player) && PeebGrapple.snapshot(player).anchor().isPresent(), "legitimate anchor initially retained");
            player.setYRot(170.0f);
            player.yBodyRot = 170.0f;
            level.setBlockAndUpdate(occluder, Blocks.STONE.defaultBlockState());
            var trace = level.clip(new ClipContext(player.getEyePosition(), anchor, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
            check(trace.getBlockPos().equals(occluder), "fixture has a real collider obscuring attached anchor");
            check(PeebGrapple.validTether(player), "rotated view and obscured attached anchor remain valid");
            nativeTick(player);
            check(PeebGrapple.snapshot(player).anchor().isPresent(), "normal server tick retains anchor after view rotation and occlusion");
            level.setBlockAndUpdate(support, Blocks.AIR.defaultBlockState());
            check(!PeebGrapple.validTether(player), "removed supporting collider invalidates tether");
            nativeTick(player);
            check(PeebGrapple.snapshot(player).anchor().isEmpty(), "normal server tick releases removed-support anchor");

            level.setBlockAndUpdate(support, Blocks.STONE.defaultBlockState());
            level.setBlockAndUpdate(occluder, Blocks.AIR.defaultBlockState());
            player.setYRot(0.0f);
            player.yBodyRot = 0.0f;
            check(PeebGrapple.attach(player, anchor), "new valid anchor accepted for range case");
            player.setPos(36.5, 20.0, 16.5);
            check(!PeebGrapple.validTether(player), "out-of-range tether invalidates");
            nativeTick(player);
            check(PeebGrapple.snapshot(player).anchor().isEmpty(), "normal server tick releases out-of-range anchor");

            player.setPos(16.5, 20.0, 16.5);
            check(PeebGrapple.attach(player, anchor), "new valid anchor accepted for mode-exit case");
            player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
            nativeTick(player);
            check(!sessions.containsKey(player.getUUID()) && PeebGrapple.snapshot(player).anchor().isEmpty(), "Peeb mode exit clears the authoritative session");

            player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(PeebMode.PEEB.get()));
            level.setBlockAndUpdate(occluder, Blocks.STONE.defaultBlockState());
            check(!PeebGrapple.attach(player, anchor), "new attachment through an obstructing collider remains rejected");
            level.setBlockAndUpdate(support, Blocks.AIR.defaultBlockState());
            level.setBlockAndUpdate(occluder, Blocks.AIR.defaultBlockState());
            combatAndScooter(event, player);
        } finally {
            PeebGrapple.release(player);
            sessions.remove(player.getUUID());
            players.remove(player.getUUID());
            level.setBlockAndUpdate(support, supportBefore);
            level.setBlockAndUpdate(occluder, occluderBefore);
        }
    }

    @SuppressWarnings("unchecked")
    private void started(ServerStartedEvent event) {
        result.addProperty("candidate_sha256", System.getProperty("peeb.dedicated.sha256"));
        result.addProperty("dedicated_server_started", true);
        result.addProperty("actual_mod_count", ModList.get().size());
        try {
            for (String id : List.of("psychiatryk_roles", "goplanska_kukirin", "goplanska_parties", "portable_chams", "psychiatryk_peeb")) {
                var container = ModList.get().getModContainerById(id);
                check(container.isPresent(), "dedicated ModList loaded " + id);
                if (container.isPresent()) {
                    String version = container.get().getModInfo().getVersion().toString();
                    result.addProperty(id + "_version", version);
                    check(version.equals("3.0.5-bmc5"), "loaded 3.0.5 metadata for " + id);
                }
            }
            var server = event.getServer();
            var itemId = ResourceLocation.parse("psychiatryk_peeb:peeb");
            check(BuiltInRegistries.ITEM.containsKey(itemId), "Peeb item registered on dedicated server");
            var holder = server.getRecipeManager().byKey(itemId);
            check(holder.isPresent(), "actual RecipeManager contains Peeb recipe");
            check(server.getRecipeManager().getRecipes().size() > 1000, "full BMC RecipeManager retained");
            result.addProperty("recipe_count", server.getRecipeManager().getRecipes().size());
            if (holder.isPresent()) {
                CraftingInput input = CraftingInput.of(3, 1, List.of(new ItemStack(Items.STRING), new ItemStack(Items.LEATHER), new ItemStack(Items.STICK)));
                Recipe<CraftingInput> recipe = (Recipe<CraftingInput>) holder.get().value();
                check(recipe.matches(input, server.overworld()), "Peeb recipe matches string, leather and stick");
                ItemStack output = recipe.assemble(input, server.registryAccess());
                check(output.getCount() == 1 && BuiltInRegistries.ITEM.getKey(output.getItem()).equals(itemId), "actual recipe assembles one registered Peeb");
            }
            payload(event, new PeebActionPayload(true, -4.25, 64.0, 12.5), PacketFlow.SERVERBOUND);
            payload(event, new PeebStatePayload(UUID.fromString("00000000-0000-4000-8000-000000000001"), 91, true,
                Optional.of(new Vec3(-4.25, 64.0, 12.5)), 6.0, .6f), PacketFlow.CLIENTBOUND);
            payload(event, PeebConfigPayload.of(false, PeebConfig.DEFAULT), PacketFlow.CLIENTBOUND);
            payload(event, new PeebConfigEditPayload(0, PeebConfig.DEFAULT), PacketFlow.SERVERBOUND);
            var command = server.getCommands().getDispatcher().getRoot().getChild("jukebox");
            check(command != null && command.getChild("stop") != null, "existing jukebox command registered");
            check(PeebConfig.server().valid(), "Peeb dedicated configuration loaded valid values");
            retention(event);
            JsonObject loyalty = EndgameLoyaltyQA.verify(server);
            result.add("endgame_loyalty", loyalty);
            check(loyalty.get("success").getAsBoolean(), "actual Elytra scooter factory and all eleven Loyalty checks passed");
            JsonObject upgrades = ScooterUpgradeQA.verify(server);
            result.add("scooter_upgrade_restoration", upgrades);
            check(upgrades.get("success").getAsBoolean(), "all loaded scooter modifications and held/worn Peeb armor checks passed");
        } catch (Throwable error) {
            success = false;
            result.addProperty("error", error.toString());
            error.printStackTrace();
        }
        result.add("checks", checks);
        result.addProperty("success", success);
        write();
    }

    private void stopped(ServerStoppedEvent event) {
        result.addProperty("server_stopped", true);
        write();
    }

    private void write() {
        try {
            Files.writeString(Path.of("dedicated-runtime.json"), new GsonBuilder().setPrettyPrinting().create().toJson(result) + "\n");
        } catch (Exception error) {
            throw new RuntimeException(error);
        }
    }
}
