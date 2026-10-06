package pl.aridlin.portablechams;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import pl.aridlin.portablechams.client.ChamsClient;

/** Entirely client-side. The JAR is ignored by the dedicated-server mod loader. */
@Mod(value = PortableChams.MOD_ID, dist = Dist.CLIENT)
public final class PortableChams {
    public static final String MOD_ID = "portable_chams";
    public PortableChams(IEventBus modBus) { ChamsClient.initialize(modBus); }
}
