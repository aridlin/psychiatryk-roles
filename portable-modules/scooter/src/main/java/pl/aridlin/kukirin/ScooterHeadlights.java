package pl.aridlin.kukirin;
/** Client-only optional dynamic-light adapter registration. Core does not depend on a lighting engine. */
public final class ScooterHeadlights {
 private static Runnable tick=()->{};
 public static void register(Runnable provider){tick=java.util.Objects.requireNonNull(provider);}
 public static void tick(){tick.run();}
 private ScooterHeadlights(){}
}
