package pl.aridlin.psychiatrykroles.music;
import java.util.Map;
import com.google.common.collect.Multimap;
import net.minecraft.sounds.SoundSource;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.client.sounds.ChannelAccess;
import net.minecraft.client.sounds.SoundEngine;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
@Mixin(SoundEngine.class)
public interface SoundEngineMusicAccessor {
    @Accessor("instanceBySource") Multimap<SoundSource, SoundInstance> psychiatryk$musicBySource();
    @Accessor("instanceToChannel") Map<SoundInstance, ChannelAccess.ChannelHandle> psychiatryk$musicChannels();
}
