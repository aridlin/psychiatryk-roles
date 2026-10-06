package pl.aridlin.frostqa;

import com.google.gson.*;
import com.mojang.authlib.GameProfile;
import java.nio.file.*;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.*;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.*;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.phys.Vec3;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import pl.aridlin.kukirin.*;

/** Dedicated-server Frost Walker checks, no client or production connection. */
@Mod("goplanska_frost_qa")
public final class FrostQA {
 private MinecraftServer server;private ServerLevel world;
 private final JsonArray checks=new JsonArray();private boolean success=true,done;private int ticks;
 private BlockPos expiry;
 public FrostQA(){NeoForge.EVENT_BUS.addListener(this::start);NeoForge.EVENT_BUS.addListener(this::tick);NeoForge.EVENT_BUS.addListener(net.neoforged.bus.api.EventPriority.LOWEST,false,net.neoforged.neoforge.event.server.ServerStoppedEvent.class,e->{try{Files.writeString(Path.of("frost-qa-stopped.json"),"{\"stopped\":true}");}catch(Exception x){x.printStackTrace();}});}
 private void check(boolean passed,String description){var c=new JsonObject();c.addProperty("passed",passed);c.addProperty("description",description);checks.add(c);success&=passed;System.out.println("FROST_QA "+(passed?"PASS ":"FAIL ")+description);}
 private void start(net.neoforged.neoforge.event.server.ServerStartedEvent e){server=e.getServer();world=server.overworld();}
 private void pool(int minX,int maxX,int minZ,int maxZ,int y,boolean lava){
  for(int cx=Math.floorDiv(minX,16);cx<=Math.floorDiv(maxX,16);cx++)for(int cz=Math.floorDiv(minZ,16);cz<=Math.floorDiv(maxZ,16);cz++)world.getChunk(cx,cz);
  for(int x=minX;x<=maxX;x++)for(int z=minZ;z<=maxZ;z++){var p=new BlockPos(x,y,z);world.setBlock(p.below(),Blocks.STONE.defaultBlockState(),2);world.setBlock(p,lava?Blocks.LAVA.defaultBlockState():Blocks.WATER.defaultBlockState(),2);for(int h=1;h<8;h++)world.setBlock(p.above(h),Blocks.AIR.defaultBlockState(),2);}
 }
 private Scooter scooter(double x,double y,double z,int enchant){
  var s=Kukirin.SCOOTER.get().create(world);s.setNoAi(true);s.setNoGravity(true);s.moveTo(x,y,z,0,0);
  var item=new ItemStack(Kukirin.ITEM.get());item.enchant(world.registryAccess().lookupOrThrow(Registries.ENCHANTMENT).getOrThrow(ResourceKey.create(Registries.ENCHANTMENT,ResourceLocation.withDefaultNamespace("frost_walker"))),enchant);s.setItemSlot(EquipmentSlot.FEET,item);
  world.addFreshEntity(s);var profile=new GameProfile(UUID.randomUUID(),"FrostQA");var rider=new net.minecraft.server.level.ServerPlayer(server,world,profile,net.minecraft.server.level.ClientInformation.createDefault());rider.connection=FakePlayerFactory.get(world,profile).connection;allow(rider);rider.moveTo(x,y+1,z,0,0);check(rider.startRiding(s,true),"actual player mounts scooter with Frost Walker "+enchant);return s;
 }
 private void allow(net.minecraft.server.level.ServerPlayer p){try{var type=Class.forName("pl.aridlin.psychiatrykroles.RoleData");var get=type.getDeclaredMethod("get",MinecraftServer.class);get.setAccessible(true);var add=type.getDeclaredMethod("addPatient",UUID.class);add.setAccessible(true);add.invoke(get.invoke(null,server),p.getUUID());}catch(Exception e){throw new RuntimeException(e);}}
 private void discard(Scooter s){for(var rider:List.copyOf(s.getPassengers()))rider.stopRiding();s.discard();}
 private boolean crust(int x,int y,int z){return world.getBlockState(new BlockPos(x,y,z)).is(Kukirin.LAVA_CRUST.get());}
 private void run(){
  final int y=100;final double speed=200/72d;
  pool(0,43,-7,8,y,true);
  // Every discovery sample is already temporary ground, with liquid beside it.
  for(int x=0;x<=12;x++)world.setBlock(new BlockPos(x,y,0),Kukirin.LAVA_CRUST.get().defaultBlockState(),2);
  var lava=scooter(.5,y+1,.5,2);lava.setDeltaMovement(speed,0,0);
  long begin=System.nanoTime();ScooterFrostWalker.tick(lava);long elapsed=(System.nanoTime()-begin)/1_000_000;
  check(crust(14,y,1),"200 km/h lookahead extends existing crust by four travel ticks ("+elapsed+" ms)");
  check(crust(6,y,2),"middle of long swept capsule has no endpoint-circle gap");
  boolean continuous=true;
  for(int step=0;step<9;step++){lava.setDeltaMovement(speed,0,0);ScooterFrostWalker.tick(lava);lava.move(MoverType.SELF,new Vec3(speed,0,0));continuous&=crust((int)Math.floor(lava.getX()),y,(int)Math.floor(lava.getZ()));}
  check(continuous&&lava.getX()>24,"200 km/h scooter retains continuous next-tick support across nine actual moves");
  expiry=new BlockPos(14,y,1);check(world.getBlockTicks().hasScheduledTick(expiry,Kukirin.LAVA_CRUST.get()),"new lava crust retains scheduled source-lava expiry");discard(lava);
  pool(0,43,18,32,y,false);
  for(int x=0;x<=12;x++)world.setBlock(new BlockPos(x,y,24),Blocks.FROSTED_ICE.defaultBlockState(),2);
  var water=scooter(.5,y+1,24.5,2);water.setDeltaMovement(speed,0,0);ScooterFrostWalker.tick(water);
  check(world.getBlockState(new BlockPos(14,y,25)).is(Blocks.FROSTED_ICE),"existing Frosted Ice continues four-tick water path");discard(water);
  pool(0,23,40,50,y,true);
  var falling=scooter(2.5,y+3.5,45.5,2);falling.setDeltaMovement(speed,-1.5,0);ScooterFrostWalker.tick(falling);
  check(crust(5,y,45),"level II prepares source-lava landing surface before falling movement");discard(falling);
  pool(30,43,40,50,y,true);
  var deep=scooter(35.5,y+.5,45.5,2);deep.setDeltaMovement(speed,-.1,0);ScooterFrostWalker.tick(deep);
  check(world.getBlockState(new BlockPos(35,y,45)).is(Blocks.LAVA),"level II does not freeze source above a deeply submerged scooter");discard(deep);
  pool(0,23,58,68,y,true);
  var one=scooter(2.5,y+1,63.5,1);one.tickCount=2;one.setDeltaMovement(speed,0,0);ScooterFrostWalker.tick(one);
  check(crust(13,y,63),"level I near-surface rider receives bounded high-speed lookahead");discard(one);
  pool(30,43,58,68,y,true);
  var oneAir=scooter(35.5,y+3,63.5,1);oneAir.tickCount=2;oneAir.setDeltaMovement(speed,-1,0);ScooterFrostWalker.tick(oneAir);
  check(world.getBlockState(new BlockPos(35,y,63)).is(Blocks.LAVA),"level I retains near-surface limit and does not create airborne landing path");discard(oneAir);
  pool(0,23,76,86,y,true);
  world.setBlock(new BlockPos(8,y,80),Blocks.LAVA.defaultBlockState().setValue(LiquidBlock.LEVEL,3),2);world.setBlock(new BlockPos(9,y+1,80),Blocks.STONE.defaultBlockState(),2);
  var sourceOnly=scooter(2.5,y+1,80.5,2);sourceOnly.setDeltaMovement(speed,0,0);ScooterFrostWalker.tick(sourceOnly);
  check(world.getBlockState(new BlockPos(8,y,80)).is(Blocks.LAVA)&&world.getBlockState(new BlockPos(8,y,80)).getValue(LiquidBlock.LEVEL)==3,"flowing lava is not replaced");
  check(world.getBlockState(new BlockPos(9,y,80)).is(Blocks.LAVA),"covered source lava is not replaced");discard(sourceOnly);
  // A distant isolated chunk makes any lookahead chunk load measurable.
  // Install raw fixture sections so lava onPlace cannot pre-load neighboring chunks.
  var chunk=world.getChunk(2000,2000);
  for(int x=32000;x<=32015;x++)for(int z=32000;z<=32015;z++)for(int h=-1;h<=7;h++)chunk.getSection(chunk.getSectionIndex(y+h)).setBlockState(x&15,(y+h)&15,z&15,h<0?Blocks.STONE.defaultBlockState():h==0?Blocks.LAVA.defaultBlockState():Blocks.AIR.defaultBlockState());
  var edge=scooter(32013.5,y+1,32008.5,2);edge.setDeltaMovement(speed,0,0);
  var beyond=new BlockPos(32016,y,32008);boolean wasLoaded=world.hasChunkAt(beyond);System.out.println("FROST_QA boundary prepared next_loaded="+wasLoaded);int before=world.getChunkSource().getLoadedChunksCount();
  ScooterFrostWalker.tick(edge);int after=world.getChunkSource().getLoadedChunksCount();
  check(!wasLoaded&&!world.hasChunkAt(beyond)&&before==after,"lookahead at unloaded chunk boundary neither loads nor generates chunks ("+before+" -> "+after+")");discard(edge);
 }
 private void tick(net.neoforged.neoforge.event.tick.ServerTickEvent.Post e){if(server==null||done)return;try{if(++ticks==20)run();if(ticks==160){check(world.getBlockState(expiry).is(Blocks.LAVA),"temporary lava crust restores source lava after rider leaves and expiry ticks run");finish(null);}}catch(Throwable error){finish(error);}}
 private void finish(Throwable error){if(done)return;done=true;if(error!=null){success=false;error.printStackTrace();var c=new JsonObject();c.addProperty("error",error.toString());checks.add(c);}var r=new JsonObject();r.addProperty("success",success);r.add("checks",checks);try{Files.writeString(Path.of("frost-qa-report.json"),new GsonBuilder().setPrettyPrinting().create().toJson(r));}catch(Exception x){x.printStackTrace();}server.halt(false);}
}
