package pl.aridlin.kukirin;
/** Hot-reloaded biome list. Fines and damage are deliberately fixed at the requested values. */
public final class RentalParkingConfig {
 public static final java.util.Set<String> DEFAULT=java.util.Set.of();
 private static java.util.Set<String> biomes=DEFAULT;private static long checked,modified=-1;
 public static java.util.Set<String> biomes(){long now=System.nanoTime();if(now-checked<1_000_000_000L)return biomes;checked=now;
 try{var file=net.neoforged.fml.loading.FMLPaths.CONFIGDIR.get().resolve("goplanska-scooter/no-parking.properties");if(!java.nio.file.Files.exists(file)){java.nio.file.Files.createDirectories(file.getParent());java.nio.file.Files.writeString(file,"# Empty biomes disables no-parking zones; edits apply without restart.\nbiomes=\n");}long time=java.nio.file.Files.getLastModifiedTime(file).toMillis();if(time!=modified){var p=new java.util.Properties();try(var reader=java.nio.file.Files.newBufferedReader(file)){p.load(reader);}var next=new java.util.HashSet<String>();for(var value:p.getProperty("biomes",String.join(",",DEFAULT)).split(",")){if(value.isBlank())continue;var key=net.minecraft.resources.ResourceLocation.tryParse(value.trim());if(key==null)throw new IllegalArgumentException("Invalid biome "+value);next.add(key.toString());}biomes=java.util.Set.copyOf(next);modified=time;}}catch(Exception e){System.getLogger("RentalParkingConfig").log(System.Logger.Level.WARNING,"Keeping previous no-parking configuration: "+e.getMessage());}return biomes;}
 public static boolean forbidden(net.minecraft.world.level.Level level,net.minecraft.core.BlockPos pos){return level.getBiome(pos).unwrapKey().map(k->biomes().contains(k.location().toString())).orElse(false);}
 private RentalParkingConfig(){}
}
