package pl.aridlin.psychiatrykroles;
import java.nio.file.*;
public class PhaseConfigCheck {
 public static void main(String[] args)throws Exception{
 var path=Files.createTempDirectory("phase-config-check").resolve("config.json");
 PhaseConfig.reload(path);if(!PhaseConfig.get().equals(new PhaseConfig.Settings(20,100)))throw new AssertionError("defaults");
 Files.writeString(path,"{\"spectatorSeconds\":2.5,\"cooldownSeconds\":8}");PhaseConfig.reload(path);if(!PhaseConfig.get().equals(new PhaseConfig.Settings(50,160)))throw new AssertionError("live reload");
 Files.writeString(path,"{\"spectatorSeconds\":-1,\"cooldownSeconds\":0}");PhaseConfig.reload(path);if(!PhaseConfig.get().equals(new PhaseConfig.Settings(50,160)))throw new AssertionError("bad config replaced valid settings");
 for(String d:new String[]{"NaN","Infinity","0","61"}){try{PhaseConfig.parse("{\"spectatorSeconds\":"+d+",\"cooldownSeconds\":5}");throw new AssertionError("accepted "+d);}catch(IllegalArgumentException expected){}}
 if(PhaseConfig.parse("{\"spectatorSeconds\":0.05,\"cooldownSeconds\":0}").durationTicks()!=1)throw new AssertionError("minimum");
 System.out.println("PHASE CONFIG PASS: defaults, live edits, invalid edit retention, bounds, zero cooldown");
 }
}
