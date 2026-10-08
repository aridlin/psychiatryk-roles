package pl.aridlin.fixture;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.minecraft.resources.ResourceLocation;
import com.google.gson.JsonParser;
@Mod("psychiatryk_roles") class Fixture {
 public Fixture(){NeoForge.EVENT_BUS.addListener(this::started);}
 void started(ServerStartedEvent e){try{
  var cls=Class.forName("pl.aridlin.psychiatrykroles.migration.MigrationPolicy");
  var station=cls.getMethod("station",ResourceLocation.class);var recipe=cls.getMethod("allowRecipe",ResourceLocation.class,com.google.gson.JsonElement.class);
  if((boolean)station.invoke(null,ResourceLocation.parse("morevillagers:hunting_post")))throw new AssertionError("hunter locked");
  if(!(boolean)station.invoke(null,ResourceLocation.parse("morevillagers:purpur_altar")))throw new AssertionError("ender unlocked");
  if(!(boolean)recipe.invoke(null,ResourceLocation.parse("morevillagers:hunting_post"),JsonParser.parseString("{\"result\":{\"id\":\"morevillagers:hunting_post\"}}")))throw new AssertionError("hunter recipe filtered");
  if((boolean)recipe.invoke(null,ResourceLocation.parse("morevillagers:purpur_altar"),JsonParser.parseString("{\"result\":{\"id\":\"morevillagers:purpur_altar\"}}")))throw new AssertionError("ender recipe leaked");
  if(e.getServer().getCommands().getDispatcher().getRoot().getChild("itemlocks")==null)throw new AssertionError("menu command absent");
  var rules=Class.forName("pl.aridlin.unlocks.Rules");
  rules.getMethod("set",String.class,String.class).invoke(null,"minecraft:stone","never");
  if(!(boolean)station.invoke(null,ResourceLocation.parse("minecraft:stone")))throw new AssertionError("arbitrary lock failed");
  rules.getMethod("set",String.class,String.class).invoke(null,"minecraft:stone","now");
  if((boolean)station.invoke(null,ResourceLocation.parse("minecraft:stone")))throw new AssertionError("live unlock failed");
  var profile=new com.mojang.authlib.GameProfile(java.util.UUID.randomUUID(),"unlock_qa");
  var player=net.neoforged.neoforge.common.util.FakePlayerFactory.get(e.getServer().overworld(),profile);
  e.getServer().getPlayerList().op(profile);
  var menuClass=Class.forName("pl.aridlin.unlocks.Unlocks$Menu");
  var ctor=menuClass.getDeclaredConstructor(int.class,net.minecraft.world.entity.player.Inventory.class,String.class,int.class,String.class);ctor.setAccessible(true);
  var menu=(net.minecraft.world.inventory.AbstractContainerMenu)ctor.newInstance(7,player.getInventory(),"minecraft:stone",0,null);
  for(var click:net.minecraft.world.inventory.ClickType.values())if(click!=net.minecraft.world.inventory.ClickType.PICKUP){menu.clicked(0,0,click,player);if(!player.getInventory().isEmpty()||!menu.getCarried().isEmpty())throw new AssertionError("ghost item via "+click);}
  if(!menu.stillValid(player))throw new AssertionError("op menu denied");
  e.getServer().getPlayerList().deop(profile);
  if(menu.stillValid(player))throw new AssertionError("nonop menu allowed");
  System.out.println("UNLOCK_NATIVE_PASS: production MigrationPolicy transformed; Hunter recipe allowed; Enderologist blocked; GUI command registered");
 }catch(Throwable t){t.printStackTrace();System.out.println("UNLOCK_NATIVE_FAIL");}finally{e.getServer().halt(false);}}
}