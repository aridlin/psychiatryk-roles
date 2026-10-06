package pl.aridlin.scootercompatqa;

import java.nio.file.*;
import java.util.*;
import com.mojang.authlib.GameProfile;
import com.google.gson.*;
import com.notunanancyowen.spears.Spears;
import com.notunanancyowen.spears.packets.TriggerStabEffectsC2SPacket;
import net.minecraft.core.registries.Registries;
import net.minecraft.core.component.DataComponents;
import net.minecraft.resources.*;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.*;
import net.minecraft.world.entity.*;
import net.minecraft.world.item.*;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.phys.Vec3;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import pl.aridlin.kukirin.*;

/** Synthetic dedicated-server fixture; no production players, worlds or connections. */
@Mod("goplanska_scooter_compat_qa")
public final class ScooterCompatQA {
    private int ticks;
    private MinecraftServer server;
    private ServerLevel world;
    private final JsonArray checks = new JsonArray();
    private final List<Scooter> created = new ArrayList<>();
    public ScooterCompatQA() {
        NeoForge.EVENT_BUS.addListener(this::started);
        NeoForge.EVENT_BUS.addListener(this::tick);
        NeoForge.EVENT_BUS.addListener(net.neoforged.bus.api.EventPriority.LOWEST, false,
                net.neoforged.neoforge.event.server.ServerStoppedEvent.class, event -> {
                    try { Files.writeString(Path.of("scooter-compat-stopped.json"), "{\"stopped\":true}\n"); }
                    catch (Exception error) { throw new RuntimeException(error); }
                });
    }
    private void started(net.neoforged.neoforge.event.server.ServerStartedEvent event) {
        server = event.getServer(); world = server.overworld();
    }
    private void check(boolean good, String description) {
        var record = new JsonObject(); record.addProperty("passed", good); record.addProperty("description", description);
        checks.add(record); System.out.println("SCOOTER_COMPAT_QA " + (good ? "PASS " : "FAIL ") + description);
        if (!good) throw new AssertionError(description);
    }
    private ServerPlayer player(String label) throws Exception {
        var profile = new GameProfile(UUID.randomUUID(), label);
        var player = new ServerPlayer(server, world, profile, ClientInformation.createDefault());
        player.connection = FakePlayerFactory.get(world, profile).connection;
        var roles = Class.forName("pl.aridlin.psychiatrykroles.RoleData");
        var get = roles.getDeclaredMethod("get", MinecraftServer.class); get.setAccessible(true);
        var patient = roles.getDeclaredMethod("addPatient", UUID.class); patient.setAccessible(true);
        patient.invoke(get.invoke(null, server), player.getUUID());
        player.moveTo(100, 101, 100, 0, 0);
        player.getFoodData().setFoodLevel(20); player.getFoodData().setExhaustion(0);
        return player;
    }
    private Scooter scooter(ServerPlayer driver, ItemStack item) {
        var scooter = Kukirin.SCOOTER.get().create(world);
        scooter.setNoAi(true); scooter.setNoGravity(true); scooter.moveTo(100, 100, 100, 0, 0);
        scooter.setItemSlot(EquipmentSlot.FEET, item); world.addFreshEntity(scooter); created.add(scooter);
        check(driver.startRiding(scooter, true), "real player can mount the synthetic scooter");
        return scooter;
    }
    private ItemStack spear(int level) {
        var item = new ItemStack(net.minecraft.core.registries.BuiltInRegistries.ITEM.get(ResourceLocation.withDefaultNamespace("diamond_spear")));
        if (level > 0) item.enchant(world.registryAccess().lookupOrThrow(Registries.ENCHANTMENT)
                .getOrThrow(ResourceKey.create(Registries.ENCHANTMENT, ResourceLocation.withDefaultNamespace("lunge"))), level);
        return item;
    }
    private void stab(ServerPlayer player, EquipmentSlot slot) {
        var piercing = player.getItemBySlot(slot).get(Spears.PIERCING_WEAPON);
        if (piercing == null) throw new AssertionError("Spear component missing");
        piercing.stab(player, slot);
    }
    private Object state(Scooter scooter, String name) throws Exception {
        var field = Scooter.class.getDeclaredField(name); field.setAccessible(true);
        return scooter.getEntityData().get((net.minecraft.network.syncher.EntityDataAccessor) field.get(null));
    }
    private void noBoost(ServerPlayer player, Scooter scooter, ItemStack spear, String label) {
        int serial = scooter.lungeSerial(), damage = spear.getDamageValue();
        float exhaustion = player.getFoodData().getExhaustionLevel();
        stab(player, EquipmentSlot.MAINHAND);
        check(scooter.lungeSerial() == serial && spear.getDamageValue() == damage
                && player.getFoodData().getExhaustionLevel() == exhaustion, label);
    }
    private void run() throws Exception {
        world.getChunkAt(new net.minecraft.core.BlockPos(100, 100, 100));
        var driver = player("ScooterQA1"); var bike = scooter(driver, new ItemStack(Kukirin.ITEM.get()));
        var weapon = spear(2); driver.setItemSlot(EquipmentSlot.MAINHAND, weapon);
        check(weapon.get(Spears.PIERCING_WEAPON) != null && weapon.is(net.minecraft.tags.TagKey.create(Registries.ITEM, ResourceLocation.withDefaultNamespace("spears"))), "registered diamond spear and actual data tags are present");
        int before = bike.lungeSerial(); stab(driver, EquipmentSlot.MAINHAND);
        check(bike.lungeSerial() == before + 1, "normal PiercingWeapon.stab invokes one scooter boost");
        check(Math.abs((Float) state(bike, "LUNGE_TARGET") - 100 / 72d) < 1e-6
                && (Integer) state(bike, "LUNGE_DECAY") == 40, "real synchronized target is 100 km/h and taper is 40 ticks");
        check(weapon.getDamageValue() == 1 && Math.abs(driver.getFoodData().getExhaustionLevel() - 8) < 1e-5,
                "level II charges original durability once and original exhaustion once");
        check(driver.getDeltaMovement().equals(Vec3.ZERO), "player receives no direct spear impulse while riding");
        noBoost(driver, bike, weapon, "immediate normal stab cooldown consumes no extra costs");
        new TriggerStabEffectsC2SPacket(true).trigger(driver);
        check(bike.lungeSerial() == before + 1 && weapon.getDamageValue() == 1,
                "compat effects route on same cooldown does not duplicate boost or item cost");
        check(ScooterSteeringInput.mouseYaw(driver, 99) == driver.getYRot(), "common steering helper is safe with Shoulder Surfing absent on dedicated server");

        var other = player("ScooterQA2"); var otherBike = scooter(other, new ItemStack(Kukirin.ITEM.get()));
        var otherWeapon = spear(3); other.setItemSlot(EquipmentSlot.MAINHAND, otherWeapon);
        new TriggerStabEffectsC2SPacket(true).trigger(other);
        check(otherBike.lungeSerial() == 1 && otherWeapon.getDamageValue() == 1
                && Math.abs(other.getFoodData().getExhaustionLevel() - 12) < 1e-5,
                "compat effects route independently boosts and charges level III exactly once");

        var empty = player("ScooterQA3"); var emptyItem = new ItemStack(Kukirin.ITEM.get());
        CustomData.update(DataComponents.CUSTOM_DATA, emptyItem, tag -> tag.putInt(ScooterBattery.KEY, 0));
        var emptyBike = scooter(empty, emptyItem); var emptyWeapon = spear(1); empty.setItemSlot(EquipmentSlot.MAINHAND, emptyWeapon);
        noBoost(empty, emptyBike, emptyWeapon, "empty battery rejects boost without extra costs");

        var unenchanted = player("ScooterQA4"); var unenchantedBike = scooter(unenchanted, new ItemStack(Kukirin.ITEM.get()));
        var plain = spear(0); unenchanted.setItemSlot(EquipmentSlot.MAINHAND, plain);
        noBoost(unenchanted, unenchantedBike, plain, "unenchant spear keeps ordinary stab without scooter boost");

        var hungry = player("ScooterQA5"); var hungryBike = scooter(hungry, new ItemStack(Kukirin.ITEM.get()));
        var hungryWeapon = spear(1); hungry.setItemSlot(EquipmentSlot.MAINHAND, hungryWeapon); hungry.getFoodData().setFoodLevel(5);
        noBoost(hungry, hungryBike, hungryWeapon, "hunger cutoff rejects boost without extra costs");

        var wet = player("ScooterQA6"); var wetBike = scooter(wet, new ItemStack(Kukirin.ITEM.get()));
        var wetWeapon = spear(1); wet.setItemSlot(EquipmentSlot.MAINHAND, wetWeapon);
        var water = Entity.class.getDeclaredField("wasTouchingWater"); water.setAccessible(true); water.setBoolean(wet, true);
        noBoost(wet, wetBike, wetWeapon, "water guard rejects boost without extra costs"); water.setBoolean(wet, false);

        var rental = player("ScooterQA7"); var rentalBike = scooter(rental, ScooterRental.preset(0));
        var rentalWeapon = spear(1); rental.setItemSlot(EquipmentSlot.MAINHAND, rentalWeapon);
        noBoost(rental, rentalBike, rentalWeapon, "rental rejects private-scooter spear boost without extra costs");

        var pilot = player("ScooterQA8"); var passengerItem = new ItemStack(Kukirin.ITEM.get());
        CustomData.update(DataComponents.CUSTOM_DATA, passengerItem, tag -> tag.putBoolean("GoplanskaScooterPassenger", true));
        var twoSeat = scooter(pilot, passengerItem); var passenger = player("ScooterQA9");
        check(passenger.startRiding(twoSeat, true), "real passenger can mount saddle-upgraded scooter");
        var passengerWeapon = spear(1); passenger.setItemSlot(EquipmentSlot.MAINHAND, passengerWeapon);
        noBoost(passenger, twoSeat, passengerWeapon, "passenger attack cannot boost driver scooter or pay extra costs");

        var fast = player("ScooterQA10"); var fastBike = scooter(fast, new ItemStack(Kukirin.ITEM.get()));
        var fastWeapon = spear(1); fast.setItemSlot(EquipmentSlot.MAINHAND, fastWeapon); fastBike.setDeltaMovement(0, 0, 110 / 72d);
        noBoost(fast, fastBike, fastWeapon, "already above 100 km/h rejects unnecessary impulse and costs");

        var walker = player("ScooterQA11"); var walkingWeapon = spear(1); walker.setItemSlot(EquipmentSlot.MAINHAND, walkingWeapon);
        stab(walker, EquipmentSlot.MAINHAND);
        check(walkingWeapon.getDamageValue() == 1 && walker.getDeltaMovement().horizontalDistance() > .45,
                "unmounted normal spear Lunge retains original player impulse and cost");

        // A terrain limit must remain constant rather than compound velocity.
        var enchantedScooter = new ItemStack(Kukirin.ITEM.get());
        enchantedScooter.enchant(world.registryAccess().lookupOrThrow(Registries.ENCHANTMENT)
                .getOrThrow(ResourceKey.create(Registries.ENCHANTMENT, ResourceLocation.withDefaultNamespace("soul_speed"))), 3);
        bike.setItemSlot(EquipmentSlot.FEET, enchantedScooter); bike.setOnGround(true);
        var floor = bike.getOnPos(); var previousFloor = world.getBlockState(floor);
        world.setBlock(floor, net.minecraft.world.level.block.Blocks.SOUL_SOIL.defaultBlockState(), 3);
        double soulLimit = ScooterHandling.TOP_SPEED * 1.045;
        boolean constantSoulLimit = bike.getBlockStateOn().is(net.minecraft.tags.BlockTags.SOUL_SPEED_BLOCKS);
        for (int i = 0; i < 100; i++) constantSoulLimit &= Math.abs(bike.normalCruiseSpeed() - soulLimit) < 1e-9;
        bike.setOnGround(false);
        boolean airLimit = Math.abs(bike.normalCruiseSpeed() - ScooterHandling.TOP_SPEED) < 1e-9;
        bike.setOnGround(true); world.setBlock(floor, net.minecraft.world.level.block.Blocks.STONE.defaultBlockState(), 3);
        boolean ordinaryLimit = Math.abs(bike.normalCruiseSpeed() - ScooterHandling.TOP_SPEED) < 1e-9;
        world.setBlock(floor, previousFloor, 3);
        check(constantSoulLimit && airLimit && ordinaryLimit,
                "actual Soul Speed III terrain cap is constant across repeated evaluations and absent off soul ground or airborne");
    }
    private void tick(net.neoforged.neoforge.event.tick.ServerTickEvent.Post event) {
        if (server == null || ++ticks != 20) return;
        Throwable failure = null;
        try { run(); } catch (Throwable error) { failure = error; error.printStackTrace(); }
        for (var scooter : created) { for (var rider : List.copyOf(scooter.getPassengers())) rider.stopRiding(); scooter.discard(); }
        var report = new JsonObject(); report.addProperty("success", failure == null); report.add("checks", checks);
        report.addProperty("error", failure == null ? "" : failure.toString());
        try { Files.writeString(Path.of("scooter-compat-report.json"), new GsonBuilder().setPrettyPrinting().create().toJson(report)); }
        catch (Exception error) { error.printStackTrace(); }
        server.halt(false);
    }
}
