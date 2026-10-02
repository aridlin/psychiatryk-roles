package pl.aridlin.psychiatrykroles.mixin;

import io.netty.handler.codec.DecoderException;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.enchantment.Enchantment;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Modern Foundry 4.1.6 resolves enchantments through a global lookup while
 * decoding its modifier packet. The global lookup is unavailable on Netty's
 * thread before the client connection and world have been assigned. Hilt's
 * packet codec already supplies a RegistryFriendlyByteBuf containing the
 * server-synchronized registry, so use that registry at the decode site.
 */
@Pseudo
@Mixin(targets = "modernmods.modernfoundry.library.modifiers.UpdateModifiersPacket", remap = false)
abstract class ModernFoundryEnchantmentLookupMixin {
    @Redirect(
        method = "<init>(Lnet/minecraft/network/FriendlyByteBuf;)V",
        at = @At(value = "INVOKE", target = "Lmodernmods/modernfoundry/library/modifiers/UpdateModifiersPacket;getEnchantment(Lnet/minecraft/resources/ResourceLocation;)Lnet/minecraft/world/item/enchantment/Enchantment;", remap = false),
        require = 1
    )
    private static Enchantment psychiatrykRoles$decodeEnchantment(
        ResourceLocation id, FriendlyByteBuf buffer
    ) {
        if (!(buffer instanceof RegistryFriendlyByteBuf registryBuffer)) {
            throw new DecoderException("Modern Foundry modifier packet has no synchronized registry buffer");
        }
        HolderLookup.RegistryLookup<Enchantment> enchantments =
            registryBuffer.registryAccess().lookupOrThrow(Registries.ENCHANTMENT);
        return enchantments.get(ResourceKey.create(Registries.ENCHANTMENT, id))
            .map(holder -> holder.value())
            .orElseThrow(() -> new DecoderException("Unknown enchantment from Modern Foundry: " + id));
    }
}
