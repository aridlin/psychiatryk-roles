package pl.aridlin.kukirin;

import com.notunanancyowen.spears.Spears;
import com.notunanancyowen.spears.enchantmentfx.ApplyExhaustion;
import com.notunanancyowen.spears.enchantmentfx.ApplyImpulse;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.enchantment.EnchantedItemInUse;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.effects.AllOf;
import net.minecraft.world.item.enchantment.effects.DamageItem;
import net.minecraft.world.item.enchantment.effects.EnchantmentEntityEffect;
import net.minecraft.world.item.enchantment.effects.PlaySoundEffect;

/** Optional Backported Spears bridge. The original attack and ordinary on-foot Lunge stay intact. */
public final class ScooterSpearLunge {
    private static final TagKey<net.minecraft.world.item.Item> SPEARS =
            TagKey.create(Registries.ITEM, ResourceLocation.withDefaultNamespace("spears"));
    private static final net.minecraft.resources.ResourceKey<net.minecraft.world.item.enchantment.Enchantment> LUNGE =
            net.minecraft.resources.ResourceKey.create(Registries.ENCHANTMENT,
                    ResourceLocation.withDefaultNamespace("lunge"));

    public static boolean afterAttack(LivingEntity entity, EquipmentSlot slot) {
        if (!(entity instanceof ServerPlayer player)
                || !(player.getVehicle() instanceof Scooter scooter)
                || scooter.getControllingPassenger() != player
                || player.isInWater() || player.isFallFlying()
                || player.getFoodData().getFoodLevel() <= 5
                || (slot != EquipmentSlot.MAINHAND && slot != EquipmentSlot.OFFHAND)) return false;
        var stack = player.getItemBySlot(slot);
        if (!stack.is(SPEARS) || stack.get(Spears.PIERCING_WEAPON) == null) return false;
        var enchantment = player.registryAccess().lookupOrThrow(Registries.ENCHANTMENT).get(LUNGE);
        if (enchantment.isEmpty()) return false;
        int level = EnchantmentHelper.getItemEnchantmentLevel(enchantment.get(), stack);
        if (level <= 0 || !enchantment.get().value().matchingSlot(slot)) return false;

        // Only bridge the installed Lunge shape: one compound effect containing its item cost,
        // exhaustion, impulse and sound. Unknown datapack effect shapes are left untouched.
        var conditionals = enchantment.get().value().getEffects(Spears.POST_PIERCING_ATTACK);
        if (conditionals.size() != 1
                || !(conditionals.getFirst().effect() instanceof AllOf.EntityEffects all)) return false;
        java.util.List<EnchantmentEntityEffect> costs = new java.util.ArrayList<>(3);
        int impulses = 0, damage = 0, exhaustion = 0, sounds = 0;
        for (var effect : all.effects()) {
            if (effect instanceof ApplyImpulse) { impulses++; continue; }
            if (effect instanceof DamageItem) damage++;
            else if (effect instanceof ApplyExhaustion) exhaustion++;
            else if (effect instanceof PlaySoundEffect) sounds++;
            else return false;
            costs.add(effect);
        }
        if (impulses != 1 || damage != 1 || exhaustion != 1 || sounds != 1) return false;
        if (!scooter.spearLunge(player)) return false;
        var used = new EnchantedItemInUse(stack, slot, player);
        for (var effect : costs)
            effect.apply(player.serverLevel(), level, used, player, player.position());
        return true;
    }

    private ScooterSpearLunge() {}
}
