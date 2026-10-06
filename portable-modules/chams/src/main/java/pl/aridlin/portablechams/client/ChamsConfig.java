package pl.aridlin.portablechams.client;

import com.google.gson.*;
import java.nio.file.*;
import org.slf4j.LoggerFactory;

/** Atomic, bounded client preferences. Reloaded twice per second without a game restart. */
public final class ChamsConfig {
    public record Settings(boolean enabled, double maxDistance, int maxTargets, double dotSpacing, int outlineRadius, double opacity) {}
    private static final Settings DEFAULTS = new Settings(true, 64, 16, 14, 2, 1);
    private static final Path FILE = Path.of("config/portable-chams.json");
    private static Settings settings = DEFAULTS;
    private static String previous, rejected;
    private static long nextPoll;
    private ChamsConfig() {}
    public static Settings get() { return settings; }
    public static Settings parse(String text) {
        JsonObject j = JsonParser.parseString(text).getAsJsonObject();
        boolean enabled = j.has("enabled") ? booleanValue(j, "enabled") : true;
        return new Settings(enabled, number(j,"maxDistance",64,4,128), integer(j,"maxTargets",16,1,32), number(j,"dotSpacing",14,4,64), integer(j,"outlineRadius",2,1,6), number(j,"opacity",1,0,1));
    }
    private static boolean booleanValue(JsonObject j,String key) {
        var v=j.get(key); if(!v.isJsonPrimitive()||!v.getAsJsonPrimitive().isBoolean())throw new IllegalArgumentException(key+" must be boolean");return v.getAsBoolean();
    }
    private static double number(JsonObject j,String key,double fallback,double min,double max) {
        if(!j.has(key))return fallback;var v=j.get(key);if(!v.isJsonPrimitive()||!v.getAsJsonPrimitive().isNumber())throw new IllegalArgumentException(key+" must be numeric");double n=v.getAsDouble();if(!Double.isFinite(n)||n<min||n>max)throw new IllegalArgumentException(key+" outside "+min+".."+max);return n;
    }
    private static int integer(JsonObject j,String key,int fallback,int min,int max) {
        double n=number(j,key,fallback,min,max);if(n!=Math.rint(n))throw new IllegalArgumentException(key+" must be integer");return (int)n;
    }
    public static void poll() {
        long now=System.nanoTime();if(now<nextPoll)return;nextPoll=now+500_000_000L;
        String text=null;
        try {
            if(!Files.exists(FILE)){Files.createDirectories(FILE.getParent());Files.writeString(FILE,new GsonBuilder().setPrettyPrinting().create().toJson(DEFAULTS)+"\n");}
            text=Files.readString(FILE);if(text.equals(previous)||text.equals(rejected))return;
            var next=parse(text);settings=next;previous=text;rejected=null;
        } catch(Exception error) {
            if(text!=null)rejected=text;
            LoggerFactory.getLogger("PortableChams").warn("Invalid client settings; retaining previous values",error);
        }
    }
}
