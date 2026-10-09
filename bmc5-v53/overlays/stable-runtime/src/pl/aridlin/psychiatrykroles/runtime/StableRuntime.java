package pl.aridlin.psychiatrykroles.runtime;
import net.neoforged.fml.common.Mod;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.fml.loading.FMLEnvironment;
import pl.aridlin.psychiatrykroles.runtime.block.RuntimeBlocks;
@Mod("psychiatryk_runtime")
public final class StableRuntime {
 public StableRuntime(IEventBus bus){bus.addListener(RuntimeNetwork::register);bus.addListener(AssetNetwork::register);RuntimeBlocks.register(bus);NeoForge.EVENT_BUS.register(new RuntimeServer());NeoForge.EVENT_BUS.register(new AssetServer());NeoForge.EVENT_BUS.register(new HudServer());NeoForge.EVENT_BUS.register(new SignalServer());if(FMLEnvironment.dist.isDedicatedServer())ServerFeatureLoader.install(bus,NeoForge.EVENT_BUS);}
}
