package pl.aridlin.psychiatrykroles.runtime;

import java.util.ServiceLoader;
import net.neoforged.bus.api.IEventBus;

public final class ServerFeatureLoader {
    public static void install(IEventBus modBus,IEventBus gameBus){
        for(ServerFeature feature:ServiceLoader.load(ServerFeature.class,ServerFeature.class.getClassLoader())){
            String name=feature.getClass().getName();
            if(!name.startsWith("pl.aridlin.psychiatrykroles.runtime.server."))
                throw new IllegalStateException("Server feature outside permitted namespace: "+name);
            feature.install(modBus,gameBus);
            System.out.println("[Psychiatryk Runtime] Installed server feature "+name);
        }
    }
}
