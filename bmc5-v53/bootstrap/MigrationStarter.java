import java.io.*;
import java.nio.file.*;
import java.security.*;
import java.util.*;
import java.util.zip.*;

public final class MigrationStarter {
 static final Path ROOT=Path.of("").toAbsolutePath();
 static String sha(Path p)throws Exception{var d=MessageDigest.getInstance("SHA-256");try(var i=Files.newInputStream(p)){byte[] b=new byte[1048576];for(int n;(n=i.read(b))>0;)d.update(b,0,n);}return HexFormat.of().formatHex(d.digest());}
 static void move(Path a,Path b)throws IOException{if(Files.exists(a)){Files.createDirectories(b.getParent());Files.move(a,b,StandardCopyOption.ATOMIC_MOVE);}}
 static void migrate()throws Exception {
  Path flag=ROOT.resolve("bmc5-activate.properties");if(!Files.exists(flag))return;
  var opts=new Properties();try(var r=Files.newBufferedReader(flag)){opts.load(r);}
  if(!"true".equals(opts.getProperty("localBackupVerified")))throw new IOException("Stopped local backup verification required");
  Path zip=ROOT.resolve("bmc5-payload.zip");if(!sha(zip).equals(opts.getProperty("sha256")))throw new IOException("Payload SHA mismatch");
  Path stage=ROOT.resolve("bmc5-verified-stage");if(Files.exists(stage))throw new IOException("Existing stage requires explicit recovery");Files.createDirectory(stage);
  var manifest=new Properties();
  try(var z=new ZipFile(zip.toFile())){
   try(var in=z.getInputStream(z.getEntry("manifest.properties"))){manifest.load(in);}
   var entries=z.entries();int verified=0;
   while(entries.hasMoreElements()){
    var e=entries.nextElement();String n=e.getName();if(n.equals("manifest.properties"))continue;
    Path dst=stage.resolve(n).normalize();if(!dst.startsWith(stage)||!n.startsWith("server/")&&!n.startsWith("host/"))throw new IOException("Unsafe path "+n);
    if(e.isDirectory())continue;Files.createDirectories(dst.getParent());try(var in=z.getInputStream(e)){Files.copy(in,dst);}
    if(!sha(dst).equals(manifest.getProperty(n)))throw new IOException("Entry SHA mismatch: "+n);verified++;
   }
   if(verified!=manifest.size())throw new IOException("Manifest completeness mismatch");
  }
  // Candidate addon is separately uploaded after its runtime checks.
  Path addon=ROOT.resolve("bmc5-addon.jar");if(!sha(addon).equals(opts.getProperty("addonSha256")))throw new IOException("Addon SHA mismatch");
  Files.copy(addon,stage.resolve("server/mods/psychiatryk_roles-3.0.0-bmc5.jar"));
  Files.copy(addon,stage.resolve("host/mods/psychiatryk_roles-3.0.0-bmc5.jar"));
  for(String side:List.of("server/mods","host/mods")){
   Path dir=stage.resolve(side);try(var fs=Files.list(dir)){for(Path f:fs.toList())if(f.getFileName().toString().startsWith("immersive-portals"))Files.delete(f);}
  }
  Path before=ROOT.resolve("bmc5-before-20261006");if(Files.exists(before))throw new IOException("Migration already has a rollback directory");Files.createDirectory(before);
  List<String> paths=List.of("world",".sable","mods","config","defaultconfigs","datapacks","libraries","server.properties","server-icon.png","goplanska-release.json","automodpack/host-modpack/main","automodpack/automodpack-server.json","automodpack/automodpack-content.json");
  var backed=new ArrayList<String>();var installed=new ArrayList<String>();
  try {
   for(String name:paths)if(Files.exists(ROOT.resolve(name))){move(ROOT.resolve(name),before.resolve(name));backed.add(name);}
   try(var children=Files.list(stage.resolve("server"))){for(Path p:children.toList()){String n=p.getFileName().toString();if(n.equals("automodpack"))continue;move(p,ROOT.resolve(n));installed.add(n);}}
   move(stage.resolve("server/automodpack/automodpack-server.json"),ROOT.resolve("automodpack/automodpack-server.json"));installed.add("automodpack/automodpack-server.json");
   move(stage.resolve("host"),ROOT.resolve("automodpack/host-modpack/main"));installed.add("automodpack/host-modpack/main");
   // TLS keys and fingerprints outside host-modpack are intentionally retained.
   Files.writeString(ROOT.resolve("bmc5-migration-applied.txt"),"BMC5 v53; fresh world seed46204253; backup local verified; "+java.time.Instant.now()+"\n");
   Files.move(flag,ROOT.resolve("bmc5-activation-applied.properties"));
   Files.delete(zip);Files.delete(addon);
   System.out.println("[Migration] Verified Better MC5 payload activated; old inventory remains in local backup.");
  } catch(Exception failure){
   for(String name:installed)move(ROOT.resolve(name),ROOT.resolve("bmc5-failed-activation/"+name));
   Collections.reverse(backed);for(String name:backed)move(before.resolve(name),ROOT.resolve(name));
   throw failure;
  }
 }
 public static void main(String[]args)throws Exception {
  migrate();
  try { PatchActivation.apply(ROOT, java.time.Instant.now()); }
  catch(IllegalStateException unsafeState) { throw unsafeState; }
  catch(Exception patchFailure) {
   System.err.println("[Patch] Scheduled addon patch rejected; verified current release retained: " + patchFailure.getMessage());
  }
  Path arg=ROOT.resolve("libraries/net/neoforged/neoforge/21.1.250/unix_args.txt");if(!Files.isRegularFile(arg))throw new IOException("Exact NeoForge21.1.250 runtime missing");
  String javaExe=Path.of(System.getProperty("java.home"),"bin/java").toString();
  var cmd=List.of(javaExe,"-Xms512M","-Xmx10G","-XX:+UseG1GC","-Djava.awt.headless=true","@"+arg,"nogui");
  Process child=new ProcessBuilder(cmd).directory(ROOT.toFile()).inheritIO().start();
  Runtime.getRuntime().addShutdownHook(new Thread(()->{if(child.isAlive()){child.destroy();try{child.waitFor(50,java.util.concurrent.TimeUnit.SECONDS);}catch(InterruptedException ignored){}}}));
  System.exit(child.waitFor());
 }
}
