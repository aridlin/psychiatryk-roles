package pl.aridlin.psychiatrykroles.jukebox.mixin;

import com.mojang.blaze3d.audio.Channel;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(Channel.class)
public interface WearableChannelAccessor {
    @Accessor("source")
    int jukebox$getSource();
}
