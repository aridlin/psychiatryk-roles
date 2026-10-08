import java.nio.file.*;
import java.io.*;
import java.util.*;
import java.util.zip.*;
import java.lang.reflect.*;
import pl.skidam.automodpack_core.utils.FileInspection;
import pl.skidam.automodpack_loader_core.client.ModpackUtils;
import pl.skidam.automodpack_core.loader.LoaderManagerService.EnvironmentType;
public class AutoModpackDuplicateTest {
 static int checks;
 static void check(boolean value,String name){checks++;if(!value)throw new AssertionError(name);}
 static FileInspection.Mod mod(String id,Collection<String> provided,Path path){return new FileInspection.Mod(id,"fixture",provided,"fixture",path,EnvironmentType.UNIVERSAL,List.of());}
 public static void main(String[] args)throws Exception{
  Path base=Path.of(args[1]).toAbsolutePath();String toml;
  try(ZipFile z=new ZipFile(args[0])){toml=new String(z.getInputStream(z.getEntry("META-INF/neoforge.mods.toml")).readAllBytes(),java.nio.charset.StandardCharsets.UTF_8);}
  Method parser=FileInspection.class.getDeclaredMethod("getModInfoFromToml",BufferedReader.class,String.class);parser.setAccessible(true);
  String id=(String)parser.invoke(null,new BufferedReader(new StringReader(toml)),"modId");
  check("psychiatryk_peeb".equals(id),"actual merged TOML parser selects last modId");
  Collection<?> provides=(Collection<?>)parser.invoke(null,new BufferedReader(new StringReader(toml)),"provides");
  check(provides.isEmpty(),"actual merged metadata does not add provides aliases");
  List<FileInspection.Mod> local=List.of(mod("goplanska_kukirin",List.of(),base.resolve("local/goplanska-kukirin-1.0.0.jar")),mod("goplanska_party_markers",List.of(),base.resolve("local/goplanska-party-markers-fixture.jar")),mod("psychiatryk_peeb",List.of(),base.resolve("local/peeb-fixture.jar")));
  var managed=mod(id,List.of(),base.resolve("managed/psychiatryk_roles-3.0.0-bmc5.jar"));
  var duplicates=ModpackUtils.getDupeMods(base,Set.of(),local,List.of(managed),Set.of());
  check(duplicates.size()==1,"only matching selected modId detected");
  check("psychiatryk_peeb".equals(duplicates.get(managed).modID()),"selected primary duplicate detected");
  check(duplicates.values().stream().noneMatch(m->m.modID().equals("goplanska_kukirin")),"old KuKirin invisible to duplicate matcher");
  check(duplicates.values().stream().noneMatch(m->m.modID().equals("goplanska_party_markers")),"old markers invisible to duplicate matcher");
  var aliases=mod(id,List.of("goplanska_kukirin","goplanska_party_markers"),managed.modPath());
  check(ModpackUtils.getDupeMods(base,Set.of(),local,List.of(aliases),Set.of()).size()==1,"provides aliases do not fix initial primary-ID equality filter");
  check(ModpackUtils.getDupeMods(base,Set.of(),local.subList(0,2),List.of(managed),Set.of()).isEmpty(),"exact two named legacy standalone mods both missed");
  check(ModpackUtils.getDupeMods(base,Set.of(),List.of(mod(id,List.of(),base.resolve("local/aggregate.jar"))),List.of(managed),Set.of()).size()==1,"same aggregate selected ID covered");
  System.out.println("{\"success\":true,\"checks\":"+checks+",\"native_automodpack_4_0_6_duplicate_match_verified\":true,\"last_mod_id_only_verified\":true,\"two_legacy_components_missed_verified\":true,\"files_mutated\":false}");
 }
}
