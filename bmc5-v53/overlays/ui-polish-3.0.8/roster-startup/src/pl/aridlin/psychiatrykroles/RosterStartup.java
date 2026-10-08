package pl.aridlin.psychiatrykroles;

import com.google.gson.*;
import com.mojang.authlib.GameProfile;
import java.nio.file.*;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.*;
import java.nio.channels.FileChannel;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.*;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraft.world.scores.ScoreboardSaveData;
import net.neoforged.neoforge.common.IOUtilities;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import pl.aridlin.parties.PartyRosterAssignments;

/** A one-time, private native roster operation activated by the normal restart. */
@EventBusSubscriber(modid="psychiatryk_roles")
public final class RosterStartup {
 private static final Logger LOGGER=LoggerFactory.getLogger("Psychiatryk roster");
 public static final String PLAN_PATH="automodpack/server-only/roles-roster-3.0.8.json";
 public record Participant(String name,UUID uuid) {}
 public record Plan(Participant partyLeader,Participant contractor,List<Participant> join,Participant separateLeader,int expectedParty,int expectedColor) {}
 private RosterStartup() {}
 private static Participant participant(JsonObject object){
  String name=object.get("name").getAsString();UUID uuid=UUID.fromString(object.get("uuid").getAsString());
  if(!name.matches("[A-Za-z0-9_]{1,16}"))throw new IllegalArgumentException("Invalid cached Minecraft name");return new Participant(name,uuid);
 }
 public static Plan parse(String json){
  var object=JsonParser.parseString(json).getAsJsonObject();if(object.get("schema").getAsInt()!=1)throw new IllegalArgumentException("Unknown roster plan schema");
  var leader=participant(object.getAsJsonObject("partyLeader"));var contractor=participant(object.getAsJsonObject("contractor"));var separate=participant(object.getAsJsonObject("separateLeader"));
  List<Participant> join=new ArrayList<>();for(JsonElement raw:object.getAsJsonArray("join"))join.add(participant(raw.getAsJsonObject()));
  if(join.isEmpty()||!join.contains(contractor))throw new IllegalArgumentException("Contractor must be a joining member");
  Set<String> names=new HashSet<>();Set<UUID> ids=new HashSet<>();for(Participant person:java.util.stream.Stream.concat(java.util.stream.Stream.of(leader,separate),join.stream()).toList())
   if(!names.add(person.name().toLowerCase(Locale.ROOT))||!ids.add(person.uuid()))throw new IllegalArgumentException("Conflicting roster participants");
  String color=object.get("expectedColor").getAsString();if(!color.matches("(?i)[0-9a-f]{6}"))throw new IllegalArgumentException("Expected native RGB required");
  int party=object.get("expectedParty").getAsInt();if(party<=0)throw new IllegalArgumentException("Existing positive shared party required");
  return new Plan(leader,contractor,List.copyOf(join),separate,party,Integer.parseInt(color,16));
 }
 private static GameProfile cached(MinecraftServer server,Participant person){
  // UUID lookup only: never authenticate/network-resolve an arbitrary name.
  GameProfile profile=Objects.requireNonNull(server.getProfileCache()).get(person.uuid()).orElseThrow(()->new IllegalArgumentException("Requested profile is not cached"));
  if(!person.name().equals(profile.getName())||!person.uuid().equals(profile.getId()))throw new IllegalArgumentException("Cached Minecraft identity differs from plan");return profile;
 }
 private static String hash(byte[] bytes)throws Exception{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));}
 static boolean receiptApplied(Path receipt,String digest){
  if(!Files.isRegularFile(receipt))return false;
  try{var prior=JsonParser.parseString(Files.readString(receipt)).getAsJsonObject();return prior.has("planSha256")&&prior.has("status")&&prior.has("persistedNativeStateVerified")&&prior.get("persistedNativeStateVerified").getAsBoolean()&&digest.equals(prior.get("planSha256").getAsString())&&"applied".equals(prior.get("status").getAsString());}catch(Exception invalid){return false;}
 }
 private static void writeReceipt(Path receipt,JsonObject value)throws Exception{
  Files.createDirectories(receipt.getParent());try{Files.setPosixFilePermissions(receipt.getParent(),java.nio.file.attribute.PosixFilePermissions.fromString("rwx------"));}catch(UnsupportedOperationException ignored){}Path tmp=Files.createTempFile(receipt.getParent(),"roster-",".tmp");
  try{Files.writeString(tmp,new GsonBuilder().setPrettyPrinting().create().toJson(value)+"\n");try(var channel=FileChannel.open(tmp,StandardOpenOption.WRITE)){channel.force(true);}try{Files.move(tmp,receipt,StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING);}catch(AtomicMoveNotSupportedException ignored){Files.move(tmp,receipt,StandardCopyOption.REPLACE_EXISTING);}try(var directory=FileChannel.open(receipt.getParent(),StandardOpenOption.READ)){directory.force(true);}}finally{Files.deleteIfExists(tmp);}
 }
 /** Native SavedData writes are asynchronous and log IO failures instead of
  * propagating them. Join their IO worker, then compare every persisted tag
  * before committing the one-time receipt. This never saves chunks/world data.
  */
 static void durablyRecord(Path dataFolder,HolderLookup.Provider registries,SavedData roles,SavedData parties,SavedData scoreboard,Path receipt,JsonObject result)throws Exception{
  Map<String,SavedData> saved=new LinkedHashMap<>();saved.put("psychiatryk_roles",roles);saved.put("goplanska_parties",parties);saved.put(ScoreboardSaveData.FILE_ID,scoreboard);
  Map<String,CompoundTag> expected=new LinkedHashMap<>();
  for(var entry:saved.entrySet())expected.put(entry.getKey(),entry.getValue().save(new CompoundTag(),registries).copy());
  for(var entry:saved.entrySet()){entry.getValue().setDirty();entry.getValue().save(dataFolder.resolve(entry.getKey()+".dat").toFile(),registries);}
  IOUtilities.waitUntilIOWorkerComplete();
  for(var entry:expected.entrySet()){
   Path file=dataFolder.resolve(entry.getKey()+".dat");if(Files.isSymbolicLink(file)||!Files.isRegularFile(file))throw new java.io.IOException("Native roster data was not persisted");
   CompoundTag root=NbtIo.readCompressed(file,NbtAccounter.unlimitedHeap());
   if(!root.getCompound("data").equals(entry.getValue()))throw new java.io.IOException("Native roster data readback differs");
  }
  try(var directory=FileChannel.open(dataFolder,StandardOpenOption.READ)){directory.force(true);}
  result.addProperty("persistedNativeStateVerified",true);writeReceipt(receipt,result);
 }
 @SubscribeEvent public static void started(ServerStartedEvent event){
  Path file=FMLPaths.GAMEDIR.get().resolve(PLAN_PATH);if(!Files.isRegularFile(file))return;
  try{
   byte[] bytes=Files.readAllBytes(file);String digest=hash(bytes);Plan plan=parse(new String(bytes,java.nio.charset.StandardCharsets.UTF_8));
   Path receipt=file.getParent().resolve("roles-roster-receipts").resolve(digest+".json");
   // This assignment is one-time, not an ongoing party policy. Later manual
   // membership/color changes must survive subsequent daily restarts.
   if(receiptApplied(receipt,digest))return;
   MinecraftServer server=event.getServer();GameProfile leader=cached(server,plan.partyLeader()),contractor=cached(server,plan.contractor()),separate=cached(server,plan.separateLeader());
   List<GameProfile> join=new ArrayList<>();for(Participant person:plan.join())join.add(cached(server,person));
   NativeRosterRoles.validate(server.getPlayerList().isOp(contractor),NativeRosterRoles.builtinPatient(contractor));
   var roles=RoleData.get(server);var parties=PartyRosterAssignments.data(server);var prepared=PartyRosterAssignments.prepare(server.getScoreboard(),parties,leader,join,separate,plan.expectedParty(),plan.expectedColor());
   boolean correct=NativeRosterRoles.matches(roles,contractor.getId())&&PartyRosterAssignments.matches(server.getScoreboard(),parties,prepared);
   // All identity, permission, membership and color checks have completed.
   if(!correct){NativeRosterRoles.apply(roles,contractor.getId());PartyRosterAssignments.apply(server.getScoreboard(),parties,prepared,join.stream().map(GameProfile::getId).toList());}
   if(!NativeRosterRoles.matches(roles,contractor.getId())||!PartyRosterAssignments.matches(server.getScoreboard(),parties,prepared))throw new IllegalStateException("Native assignment readback differs");
   var result=new JsonObject();result.addProperty("schema",1);result.addProperty("planSha256",digest);result.addProperty("status","applied");result.addProperty("appliedAt",Instant.now().toString());result.addProperty("sharedParty",prepared.sharedParty());result.addProperty("separateParty",prepared.separateParty());result.addProperty("partyColorPreserved",String.format("%06x",plan.expectedColor()));result.addProperty("nativeStateVerified",true);
   var scoreboard=server.overworld().getDataStorage().computeIfAbsent(server.getScoreboard().dataFactory(),ScoreboardSaveData.FILE_ID);
   durablyRecord(server.getWorldPath(LevelResource.ROOT).resolve("data"),server.registryAccess(),roles,parties,scoreboard,receipt,result);
   LOGGER.info("Private roster plan applied and native state verified; receipt stored server-side");
  }catch(Exception failure){LOGGER.error("Private roster plan was not completed: {}",failure.getClass().getSimpleName()+": "+failure.getMessage());}
 }
}
