package pl.aridlin.psychiatrykroles;
import java.nio.file.*;
import com.google.gson.*;
/** Reads edits at most once per second. Invalid edits retain the last valid values. */
public final class PhaseConfig {
 public record Settings(int durationTicks,int cooldownTicks){}
 public static final Path FILE=Path.of("config/psychiatryk-phase.json");
 private static Settings current=new Settings(20,100);
 private static long nextCheck; private static String lastText="";
 public static Settings get(){long now=System.currentTimeMillis();if(now>=nextCheck){nextCheck=now+1000;reload(FILE);}return current;}
 public static Settings parse(String text){var o=JsonParser.parseString(text).getAsJsonObject();double duration=o.get("spectatorSeconds").getAsDouble(),cooldown=o.get("cooldownSeconds").getAsDouble();if(!Double.isFinite(duration)||!Double.isFinite(cooldown)||duration<0.05||duration>60||cooldown<0||cooldown>3600)throw new IllegalArgumentException("spectatorSeconds must be 0.05..60; cooldownSeconds 0..3600");return new Settings((int)Math.ceil(duration*20),(int)Math.ceil(cooldown*20));}
 static void reload(Path file){try{if(!Files.exists(file)){Files.createDirectories(file.getParent());Files.writeString(file,"{\n  \"spectatorSeconds\": 1.0,\n  \"cooldownSeconds\": 5.0\n}\n");}String text=Files.readString(file);if(text.equals(lastText))return;lastText=text;current=parse(text);System.out.println("[Phase Charm] Loaded duration="+current.durationTicks()+" ticks, cooldown="+current.cooldownTicks()+" ticks");}catch(Exception ex){System.err.println("[Phase Charm] Config unchanged: "+ex.getMessage());}}
}
