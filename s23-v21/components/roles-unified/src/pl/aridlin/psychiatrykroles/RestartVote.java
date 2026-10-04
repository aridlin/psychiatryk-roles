package pl.aridlin.psychiatrykroles;
import java.util.*;
final class RestartVote {
 final UUID id=UUID.randomUUID();final Set<UUID> eligible;final Set<UUID> yes=new HashSet<>(),no=new HashSet<>();final String duration;final long deadline;
 RestartVote(Collection<UUID> players,String duration,long now){eligible=Set.copyOf(players);this.duration=duration;deadline=now+120_000_000_000L;if(eligible.isEmpty())throw new IllegalArgumentException("No eligible players");}
 int required(){return (eligible.size()+1)/2;}
 boolean cast(UUID player,boolean approve){if(!eligible.contains(player))return false;yes.remove(player);no.remove(player);(approve?yes:no).add(player);return true;}
 boolean passed(){return yes.size()>=required();}
 boolean finished(){return yes.size()+no.size()==eligible.size();}
}
