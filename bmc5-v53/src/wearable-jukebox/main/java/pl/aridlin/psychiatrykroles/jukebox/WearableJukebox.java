package pl.aridlin.psychiatrykroles.jukebox;

import io.wispforest.accessories.api.AccessoriesAPI;
import io.wispforest.accessories.api.AccessoriesCapability;
import io.wispforest.accessories.api.Accessory;
import io.wispforest.accessories.api.slot.SlotReference;
import java.lang.ref.WeakReference;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;

/** The vanilla jukebox occupies the pack's existing functional Accessories back slot. */
public final class WearableJukebox {
    public static final String SLOT = "back";
    private static final String DATA = "PsychiatrykWearableJukebox";
    private static final Map<UUID, WeakReference<ItemStack>> LIVE_ITEMS = new HashMap<>();

    /** Cosmetic slots, the normal inventory and chest armour never count as equipped. */
    public static ItemStack equipped(LivingEntity entity) {
        if (!(entity instanceof Player)) return ItemStack.EMPTY;
        var capability = AccessoriesCapability.get(entity);
        if (capability == null) return ItemStack.EMPTY;
        var container = capability.getContainers().get(SLOT);
        if (container == null) return ItemStack.EMPTY;
        for (int index = 0; index < container.getSize(); index++) {
            var stack = container.getAccessories().getItem(index);
            if (stack.is(Items.JUKEBOX) && stack.getCount() == 1) return stack;
        }
        return ItemStack.EMPTY;
    }

    private static CompoundTag data(ItemStack stack) {
        return stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY)
                .copyTag().getCompound(DATA);
    }

    private static UUID storedToken(ItemStack stack) {
        var tag = data(stack);
        return tag.hasUUID("token") ? tag.getUUID("token") : null;
    }

    private static void requireJukebox(ItemStack stack) {
        if (stack == null || !stack.is(Items.JUKEBOX) || stack.getCount() != 1) {
            throw new IllegalArgumentException("A wearable source must be one vanilla jukebox");
        }
    }

    private static UUID bindFresh(ItemStack stack) {
        var previous = storedToken(stack);
        var reference = previous == null ? null : LIVE_ITEMS.get(previous);
        if (reference != null && reference.get() == stack) LIVE_ITEMS.remove(previous);
        if (LIVE_ITEMS.size() > 128) LIVE_ITEMS.values().removeIf(value -> value.get() == null);
        UUID token = UUID.randomUUID();
        CustomData.update(DataComponents.CUSTOM_DATA, stack, root -> {
            var tag = root.getCompound(DATA);
            tag.putUUID("token", token);
            root.put(DATA, tag);
        });
        LIVE_ITEMS.put(token, new WeakReference<>(stack));
        return token;
    }

    /** Server only: stores the current equip epoch while rejecting copied or rebound instances. */
    public static synchronized UUID token(ItemStack stack) {
        requireJukebox(stack);
        UUID token = storedToken(stack);
        var reference = token == null ? null : LIVE_ITEMS.get(token);
        if (reference != null && reference.get() == stack) return token;
        // A stored UUID never revives an earlier equip epoch. Re-equipping, restoring
        // an inventory bank or restarting the server acquires a fresh live binding.
        // Loop preference remains in the item's data independently of this token.
        return bindFresh(stack);
    }

    /** Does not create tokens or modify equipment while checking a running source. */
    public static synchronized boolean valid(ServerPlayer player, UUID token) {
        if (player == null || token == null || !player.isAlive() || player.isRemoved()
                || player.isSpectator() || player.server.getPlayerList().getPlayer(player.getUUID()) != player) {
            return false;
        }
        var stack = equipped(player);
        if (stack.isEmpty() || !token.equals(storedToken(stack))) return false;
        var reference = LIVE_ITEMS.get(token);
        return reference != null && reference.get() == stack;
    }

    public static boolean looping(ItemStack stack) {
        return stack != null && stack.is(Items.JUKEBOX) && data(stack).getBoolean("loop");
    }

    /** Server only: this setting follows the item and the existing testing inventory banks. */
    public static void looping(ItemStack stack, boolean enabled) {
        requireJukebox(stack);
        CustomData.update(DataComponents.CUSTOM_DATA, stack, root -> {
            var tag = root.getCompound(DATA);
            tag.putBoolean("loop", enabled);
            root.put(DATA, tag);
        });
    }

    private static synchronized void unbind(ItemStack stack, LivingEntity entity) {
        if (!stack.is(Items.JUKEBOX)) return;
        UUID token = storedToken(stack);
        if (token == null) return;
        var reference = LIVE_ITEMS.get(token);
        // Accessories supplies a copied previous stack and can defer callbacks
        // until after tick() or the picker has already bound the current instance.
        // Such a callback must not invalidate the same item that is still equipped.
        if (reference != null && reference.get() == equipped(entity)) return;
        LIVE_ITEMS.remove(token);
    }

    /** Checks the functional slot and its own visibility toggle, including cosmetic invocations. */
    public static boolean renderable(SlotReference reference) {
        if (reference == null || !SLOT.equals(reference.slotName())
                || !(reference.entity() instanceof Player player) || player.isSpectator()) return false;
        var container = reference.slotContainer();
        if (container == null || reference.slot() < 0 || reference.slot() >= container.getSize()
                || !container.shouldRender(reference.slot())) return false;
        var actual = container.getAccessories().getItem(reference.slot());
        return actual.is(Items.JUKEBOX) && actual.getCount() == 1;
    }

    private static final Accessory ACCESSORY = new Accessory() {
        @Override
        public boolean canEquip(ItemStack stack, SlotReference reference) {
            return reference != null && SLOT.equals(reference.slotName())
                    && reference.entity() instanceof Player && stack.is(Items.JUKEBOX)
                    && !reference.entity().isSpectator();
        }

        @Override
        public int maxStackSize(ItemStack stack) {
            return 1;
        }

        @Override
        public boolean canEquipFromUse(ItemStack stack, SlotReference reference) {
            return false;
        }

        @Override
        public void onEquip(ItemStack stack, SlotReference reference) {
            if (reference.entity() instanceof ServerPlayer && SLOT.equals(reference.slotName())
                    && stack.getCount() == 1) {
                token(stack);
            }
        }

        @Override
        public void onUnequip(ItemStack stack, SlotReference reference) {
            if (reference.entity() instanceof ServerPlayer && SLOT.equals(reference.slotName())) unbind(stack, reference.entity());
        }

        @Override
        public void tick(ItemStack stack, SlotReference reference) {
            if (reference.entity() instanceof ServerPlayer && SLOT.equals(reference.slotName())
                    && stack == equipped(reference.entity())) token(stack);
        }
    };

    @EventBusSubscriber(modid = "psychiatryk_roles", bus = EventBusSubscriber.Bus.MOD)
    public static final class Setup {
        @SubscribeEvent
        public static void setup(FMLCommonSetupEvent event) {
            event.enqueueWork(() -> AccessoriesAPI.registerAccessory(Items.JUKEBOX, ACCESSORY));
        }
    }

    @EventBusSubscriber(modid = "psychiatryk_roles")
    public static final class Lifecycle {
        @SubscribeEvent
        public static void stopped(ServerStoppedEvent event) {
            synchronized (WearableJukebox.class) {
                LIVE_ITEMS.clear();
            }
        }
    }

    private WearableJukebox() {}
}
