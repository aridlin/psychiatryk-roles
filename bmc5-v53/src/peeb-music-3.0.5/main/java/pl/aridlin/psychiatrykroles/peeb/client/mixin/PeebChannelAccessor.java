package pl.aridlin.psychiatrykroles.peeb.client.mixin;

import com.mojang.blaze3d.audio.Channel;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin({Channel.class})
public interface PeebChannelAccessor {
   @Accessor("source")
   int peeb$source();
}
