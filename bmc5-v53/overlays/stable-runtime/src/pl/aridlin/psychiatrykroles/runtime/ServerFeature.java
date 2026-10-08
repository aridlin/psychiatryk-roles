package pl.aridlin.psychiatrykroles.runtime;

import net.neoforged.bus.api.IEventBus;

/** Stable server extension point. Implementations live only in server releases. */
public interface ServerFeature {
    void install(IEventBus modBus,IEventBus gameBus);
}
