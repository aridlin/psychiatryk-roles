package pl.aridlin.kukirin;
import java.nio.file.*;
import java.util.Properties;
/** Reloadable without restart; malformed edits retain the last valid configuration. */
public final class RentalConfig {
 public record Settings(boolean spawning,int perPlayer,int total,long price,int interval,double zombieChance,double doorChance){}
 private final Path file;private long lastModified=-1;private Settings settings=new Settings(false,4,40,10,600,.08,.02);
 public RentalConfig(Path file){this.file=file;}
 public void setSpawning(boolean enabled){get();try{String contents=Files.readString(file).replaceAll("(?m)^spawning=.*$","spawning="+enabled);Files.writeString(file,contents);lastModified=-1;}catch(java.io.IOException e){throw new java.io.UncheckedIOException(e);}}
 public Settings get(){try{if(!Files.exists(file)){Files.createDirectories(file.getParent());Files.writeString(file,"# Rental scooters; spawn only after explicitly enabling this module.\nspawning=false\nperPlayer=4\nmaxTotal=40\npriceChips=10\nspawnIntervalTicks=600\nzombieChance=0.08\nfrontDoorChance=0.02\n");}long modified=Files.getLastModifiedTime(file).toMillis();if(modified!=lastModified){var p=new Properties();try(var reader=Files.newBufferedReader(file)){p.load(reader);}String enabled=p.getProperty("spawning","false");if(!enabled.equals("true")&&!enabled.equals("false"))throw new IllegalArgumentException("spawning must be true or false");var next=new Settings(Boolean.parseBoolean(enabled),range(p,"perPlayer",4,0,12),range(p,"maxTotal",40,0,200),range(p,"priceChips",10,1,1000000),range(p,"spawnIntervalTicks",600,100,72000),chance(p,"zombieChance",.08),chance(p,"frontDoorChance",.02));settings=next;lastModified=modified;}}catch(Exception error){System.getLogger("RentalConfig").log(System.Logger.Level.WARNING,"Keeping previous rental configuration: "+error.getMessage());}return settings;}
 private static int range(Properties p,String key,int fallback,int min,int max){int value=Integer.parseInt(p.getProperty(key,""+fallback));if(value<min||value>max)throw new IllegalArgumentException(key+" outside range");return value;}
 private static double chance(Properties p,String key,double fallback){double value=Double.parseDouble(p.getProperty(key,""+fallback));if(!Double.isFinite(value)||value<0||value>1)throw new IllegalArgumentException(key+" outside 0..1");return value;}
}
