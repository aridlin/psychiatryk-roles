package pl.aridlin.fixture;
import pl.aridlin.psychiatrykroles.runtime.*;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.bus.api.EventPriority;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.inventory.*;
import net.minecraft.world.entity.player.Inventory;
import com.google.gson.*;
import java.nio.file.*;
import java.util.*;
@Mod("runtime_fixture") public class NativeFixture {
 static int checks;static String restore;static int watchTick;static boolean scheduled;
 static void check(boolean ok,String message){checks++;if(!ok)throw new AssertionError(message);}
 public NativeFixture(){NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST,this::started);NeoForge.EVENT_BUS.addListener(this::tick);}
 void started(ServerStartedEvent event){var server=event.getServer();try{
  var manager=server.getRecipeManager();var key=ResourceLocation.parse("psychiatryk_runtime:guide");
  check(manager.byKey(key).isPresent(),"default recipe installed");
  check("installed".equals(System.getProperty("psychiatryk.fixture.provider")),"server-only Java extension discovered and installed");
  check(VariantSignature.verify("guide","minecraft:paper",VariantSignature.sign("guide","minecraft:paper")),"genuine item signature");
  check(!VariantSignature.verify("other","minecraft:paper",VariantSignature.sign("guide","minecraft:paper")),"variant ID substitution rejected");
  check(!VariantSignature.verify("guide","minecraft:stone",VariantSignature.sign("guide","minecraft:paper")),"base item substitution rejected");
  check(server.getCommands().getDispatcher().getRoot().getChild("psychiatrykruntime")!=null,"commands registered");
  for(var entry:Settings.BINDINGS.entrySet()){
   var keyName=entry.getKey();var spec=entry.getValue();double old=Settings.read(keyName);double test=spec.toggle()?(old==0?1:0):spec.min();
   Settings.write(keyName,test);check(Settings.read(keyName)==test,"live native setting "+keyName);Settings.write(keyName,old);check(Settings.read(keyName)==old,"restore native setting "+keyName);
  }
  var raw=Files.readString(RuntimeServer.FILE);var doc=JsonParser.parseString(raw).getAsJsonObject();
  doc.getAsJsonObject("items").getAsJsonObject("guide").addProperty("name","Updated from server without client replacement");
  Files.writeString(RuntimeServer.FILE,doc.toString());RuntimeServer.reload(server);
  check(manager.byKey(key).orElseThrow().value().getResultItem(server.registryAccess()).getHoverName().getString().startsWith("Updated"),"live recipe update");
  Files.writeString(RuntimeServer.FILE,"{broken");boolean rejected=false;try{RuntimeServer.reload(server);}catch(Exception expected){rejected=true;}
  check(rejected,"invalid reload rejected");check(manager.byKey(key).orElseThrow().value().getResultItem(server.registryAccess()).getHoverName().getString().startsWith("Updated"),"last known good recipe preserved");
  doc.getAsJsonObject("items").getAsJsonObject("guide").addProperty("base","missing:invalid");Files.writeString(RuntimeServer.FILE,doc.toString());rejected=false;try{RuntimeServer.reload(server);}catch(Exception expected){rejected=true;}check(rejected,"unknown registry rejected before publish");
  Files.writeString(RuntimeServer.FILE,raw);RuntimeServer.reload(server);
  var profile=new com.mojang.authlib.GameProfile(UUID.randomUUID(),"runtime_qa");var player=net.neoforged.neoforge.common.util.FakePlayerFactory.get(server.overworld(),profile);server.getPlayerList().op(profile);
  var view=new Schema.View(1,"a".repeat(64),"token","main","Test",List.of(new Schema.Control("info","Info","label","text",0,0,0,0)));
  var ctor=FallbackMenu.class.getDeclaredConstructor(int.class,Inventory.class,Schema.View.class,int.class);ctor.setAccessible(true);var menu=(FallbackMenu)ctor.newInstance(7,player.getInventory(),view,0);
  for(var click:ClickType.values())for(int slot:new int[]{-999,0,45,53,54,89}){menu.clicked(slot,0,click,player);check(player.getInventory().isEmpty()&&menu.getCarried().isEmpty(),"no item extraction "+click+"/"+slot);}
  double unchanged=Settings.read("scooter.steering");
  for(String bad:List.of("{}","null","{broken",Schema.JSON.toJson(new Schema.Action(1,"a".repeat(64),"forged","scooter","steering","2"))))RuntimeServer.action(player,bad);
  check(Settings.read("scooter.steering")==unchanged,"malformed and forged actions inert");
  var field=RuntimeServer.class.getDeclaredField("sessions");field.setAccessible(true);@SuppressWarnings("unchecked") var sessions=(Map<UUID,Object>)field.get(null);
  var revisionField=RuntimeServer.class.getDeclaredField("revision");revisionField.setAccessible(true);String revision=(String)revisionField.get(null);
  var sessionType=Class.forName("pl.aridlin.psychiatrykroles.runtime.RuntimeServer$Session");var sessionCtor=sessionType.getDeclaredConstructor(String.class,String.class,String.class,long.class);sessionCtor.setAccessible(true);
  var realSession=sessionCtor.newInstance("accepted-token",revision,"scooter",System.nanoTime()+60_000_000_000L);sessions.put(player.getUUID(),realSession);
  RuntimeServer.action(player,Schema.JSON.toJson(new Schema.Action(1,revision,"accepted-token","scooter","steering","1.5")));
  check(Settings.read("scooter.steering")==1.5,"valid authenticated session changed live server setting");
  RuntimeServer.action(player,Schema.JSON.toJson(new Schema.Action(1,revision,"accepted-token","scooter","steering","1.8")));
  check(Settings.read("scooter.steering")==1.5,"replayed session action rejected");Settings.write("scooter.steering",unchanged);
  var liveRule=new Schema.Behavior(2,List.of(),List.of(new Schema.Step("message","Authorized live rule",0)));
  check(BehaviorEngine.run(liveRule,player),"authorized server behavior executes");
  server.getPlayerList().deop(profile);check(!player.createCommandSourceStack().hasPermission(2),"nonop fixture permissions");
  check(!BehaviorEngine.run(liveRule,player),"server behavior permission revoked live");
  check(!server.getCommands().getDispatcher().getRoot().getChild("psychiatrykruntime").canUse(player.createCommandSourceStack()),"admin command rejected to nonop");
  var admit=RuntimeServer.class.getDeclaredMethod("admit",UUID.class,long.class);admit.setAccessible(true);var id=UUID.randomUUID();long now=System.nanoTime();for(int i=0;i<8;i++)check((boolean)admit.invoke(null,id,now),"allowed rate");check(!(boolean)admit.invoke(null,id,now),"flood rejected");check((boolean)admit.invoke(null,id,now+1_000_000_001L),"rate recovers");
  var watched=JsonParser.parseString(raw).getAsJsonObject();watched.getAsJsonObject("items").getAsJsonObject("guide").addProperty("name","Watched live without command");
  restore=raw;watchTick=server.getTickCount();Files.writeString(RuntimeServer.FILE,watched.toString());scheduled=true;
 }catch(Throwable t){t.printStackTrace();System.out.println("RUNTIME_NATIVE_FAIL");}finally{if(!scheduled)server.halt(false);}}
 void tick(ServerTickEvent.Post event){if(!scheduled||event.getServer().getTickCount()-watchTick<50)return;scheduled=false;try{
   var server=event.getServer();var result=server.getRecipeManager().byKey(ResourceLocation.parse("psychiatryk_runtime:guide")).orElseThrow().value().getResultItem(server.registryAccess());
   check(result.getHoverName().getString().startsWith("Watched"),"automatic file watcher applied hot update without restart or command");
   Files.writeString(RuntimeServer.FILE,restore);RuntimeServer.reload(server);
   System.out.println("RUNTIME_NATIVE_PASS checks="+checks);Files.writeString(Path.of("PASS"),"checks="+checks);
  }catch(Throwable t){t.printStackTrace();System.out.println("RUNTIME_NATIVE_FAIL");}finally{event.getServer().halt(false);}}
}
