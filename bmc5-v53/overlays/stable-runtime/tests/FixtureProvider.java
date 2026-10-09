package pl.aridlin.psychiatrykroles.runtime.server;
import pl.aridlin.psychiatrykroles.runtime.ServerFeature;
import net.neoforged.bus.api.IEventBus;
public final class FixtureProvider implements ServerFeature {
 public void install(IEventBus modBus,IEventBus gameBus){System.setProperty("psychiatryk.fixture.provider","installed");}
}
