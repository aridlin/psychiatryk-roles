package pl.aridlin.psychiatrykroles.compat;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;

/** Older JEI operations represent one ingredient; newer JEI exposes this method. */
@Pseudo
@Mixin(targets = "mezz.jei.common.transfer.TransferOperation", remap = false)
public abstract class JeiUnitTransferMixin {
    public int count() { return 1; }
}
