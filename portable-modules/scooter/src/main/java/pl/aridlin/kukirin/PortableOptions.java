package pl.aridlin.kukirin;
/** Conservative portable-pack defaults. Reloaded once per second; malformed files retain last good values. */
public record PortableOptions(boolean blockBreaking,boolean impactDamage,boolean invertedGravity,boolean rentalExplosions,String currency){
 private static PortableOptions current=new PortableOptions(false,false,false,false,"minecraft:iron_nugget");
 private static long checked,modified=-1;
 public static synchronized PortableOptions get(){long now=System.nanoTime();if(now-checked<1_000_000_000L)return current;checked=now;
  try{var f=net.neoforged.fml.loading.FMLPaths.CONFIGDIR.get().resolve("goplanska-scooter/portable.properties");if(!java.nio.file.Files.exists(f)){java.nio.file.Files.createDirectories(f.getParent());java.nio.file.Files.writeString(f,"# Conservative defaults; edits apply without a restart.\nblockBreaking=false\nimpactDamage=false\ninvertedGravity=false\nrentalExplosions=false\ncurrency=minecraft:iron_nugget\n");}
   long m=java.nio.file.Files.getLastModifiedTime(f).toMillis();if(m!=modified){var p=new java.util.Properties();try(var reader=java.nio.file.Files.newBufferedReader(f)){p.load(reader);}String currency=p.getProperty("currency","minecraft:iron_nugget").trim();if(net.minecraft.resources.ResourceLocation.tryParse(currency)==null)throw new IllegalArgumentException("Invalid currency ID");current=new PortableOptions(bool(p,"blockBreaking"),bool(p,"impactDamage"),bool(p,"invertedGravity"),bool(p,"rentalExplosions"),currency);modified=m;}}
  catch(Exception e){System.getLogger("PortableScooters").log(System.Logger.Level.WARNING,"Keeping previous portable settings: "+e.getMessage());}return current;}
 private static boolean bool(java.util.Properties p,String k){String v=p.getProperty(k,"false").trim();if(!v.equals("true")&&!v.equals("false"))throw new IllegalArgumentException("Invalid boolean "+k);return Boolean.parseBoolean(v);}
}
