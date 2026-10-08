package pl.aridlin.parties;

import com.mojang.authlib.GameProfile;
import java.util.*;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.scores.*;

/** Prepare all membership/leadership changes before touching native state. */
public final class PartyRosterAssignments {
 private PartyRosterAssignments() {}
 public record Prepared(int sharedParty,int separateParty,List<String> joinNames,
                        String separateName,UUID separateUuid,Set<Integer> removeLeaders) {}
 private static int group(Scoreboard board,Objective objective,String name){var score=board.getPlayerScoreInfo(ScoreHolder.forNameOnly(name),objective);return score==null?0:Math.max(0,score.value());}
 public static PartyData data(MinecraftServer server){return PartyData.get(server);}
 public static Prepared prepare(Scoreboard board,PartyData data,GameProfile leader,List<GameProfile> join,GameProfile separate,int expectedParty,int expectedColor){
  var objective=board.getObjective("goplanska_party");
  if(objective==null)throw new IllegalArgumentException("Existing native party objective required");
  int shared=group(board,objective,leader.getName());
  if(shared<=0||shared!=expectedParty||!leader.getId().equals(data.leaders.get(shared)))throw new IllegalArgumentException("Shared party/leader differs from the plan");
  int color=data.colors.getOrDefault(shared,shared==1?0xffdd33:0x55ffff);
  if(color!=(expectedColor&0xffffff))throw new IllegalArgumentException("Existing party color differs; roster will not recolor it");
  var joinNames=join.stream().map(GameProfile::getName).toList();Set<String> leavingNames=new HashSet<>(joinNames);leavingNames.add(separate.getName());
  Set<Integer> remove=new LinkedHashSet<>();
  for(GameProfile member:join){int old=group(board,objective,member.getName());
   if(old!=0&&old!=shared&&member.getId().equals(data.leaders.get(old))){
    boolean stranded=board.listPlayerScores(objective).stream().anyMatch(score->score.value()==old&&!leavingNames.contains(score.owner()));
    if(stranded)throw new IllegalArgumentException("A joining leader still has other party members");
    remove.add(old);
   }
  }
  int separateParty=group(board,objective,separate.getName());
  if(separateParty==shared||separateParty<=0||!separate.getId().equals(data.leaders.get(separateParty))){
   int maximum=Math.max(2,board.listPlayerScores(objective).stream().mapToInt(PlayerScoreEntry::value).max().orElse(2));
   if(maximum==Integer.MAX_VALUE)throw new IllegalArgumentException("Party ID space exhausted");
   separateParty=maximum+1;
   while(data.leaders.containsKey(separateParty)||data.colors.containsKey(separateParty)){if(separateParty==Integer.MAX_VALUE)throw new IllegalArgumentException("Party ID space exhausted");separateParty++;}
  }
  if(separateParty==shared)throw new IllegalArgumentException("Separate party cannot be shared party");
  return new Prepared(shared,separateParty,List.copyOf(joinNames),separate.getName(),separate.getId(),Set.copyOf(remove));
 }
 public static boolean matches(Scoreboard board,PartyData data,Prepared plan){
  var objective=board.getObjective("goplanska_party");if(objective==null)return false;
  return plan.joinNames().stream().allMatch(name->group(board,objective,name)==plan.sharedParty())
   &&group(board,objective,plan.separateName())==plan.separateParty()&&plan.separateUuid().equals(data.leaders.get(plan.separateParty()));
 }
 public static void apply(Scoreboard board,PartyData data,Prepared plan,Collection<UUID> joinIds){
  var objective=Objects.requireNonNull(board.getObjective("goplanska_party"));
  for(int old:plan.removeLeaders())data.leaders.remove(old);
  for(String name:plan.joinNames())board.getOrCreatePlayerScore(ScoreHolder.forNameOnly(name),objective).set(plan.sharedParty());
  board.getOrCreatePlayerScore(ScoreHolder.forNameOnly(plan.separateName()),objective).set(plan.separateParty());
  data.leaders.put(plan.separateParty(),plan.separateUuid());
  for(UUID id:joinIds)data.chatMode.remove(id);data.chatMode.remove(plan.separateUuid());
  data.setDirty();
 }
}
