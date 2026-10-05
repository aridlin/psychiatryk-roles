package pl.aridlin.kukirin;
import com.google.gson.Gson;
import java.nio.file.*;
/** Client comfort settings, reloaded once a second; malformed edits keep the last valid values. */
public final class ScooterClientOptions {
 public boolean mouseSteering=true,effectsEnabled=true,audioEnabled=true,headlightsEnabled=true,speedometerEnabled=true;
 public float fovBoostDegrees=10,cameraLeanDegrees=2,motorVolume=.22f,windVolume=.12f,musicVolume=.7f;
 private static ScooterClientOptions current=new ScooterClientOptions();private static long next;
 public static void save(){try{Files.writeString(net.neoforged.fml.loading.FMLPaths.CONFIGDIR.get().resolve("goplanska-scooter-client.json"),new com.google.gson.GsonBuilder().setPrettyPrinting().create().toJson(current));next=System.nanoTime()+1_000_000_000L;}catch(java.io.IOException e){throw new java.io.UncheckedIOException(e);}}
 public static ScooterClientOptions get(){
  long now=System.nanoTime();if(now<next)return current;next=now+1_000_000_000L;
  Path path=net.neoforged.fml.loading.FMLPaths.CONFIGDIR.get().resolve("goplanska-scooter-client.json");
  try{if(!Files.exists(path)){Files.writeString(path,new com.google.gson.GsonBuilder().setPrettyPrinting().create().toJson(current));return current;}
   var candidate=new Gson().fromJson(Files.readString(path),ScooterClientOptions.class);
   if(candidate==null||!Float.isFinite(candidate.fovBoostDegrees)||!Float.isFinite(candidate.cameraLeanDegrees)||!Float.isFinite(candidate.motorVolume)||!Float.isFinite(candidate.musicVolume)||!Float.isFinite(candidate.windVolume))return current;
   candidate.fovBoostDegrees=Math.clamp(candidate.fovBoostDegrees,0,25);candidate.cameraLeanDegrees=Math.clamp(candidate.cameraLeanDegrees,0,5);candidate.motorVolume=Math.clamp(candidate.motorVolume,0,1);candidate.musicVolume=Math.clamp(candidate.musicVolume,0,1);candidate.windVolume=Math.clamp(candidate.windVolume,0,1);current=candidate;
  }catch(Exception ignored){}return current;
 }
}
