package pl.aridlin.psychiatrykroles;
import java.util.*;
public class RestartVoteTest {
 public static void main(String[] args){
 for(int n=1;n<=12;n++){var ids=new ArrayList<UUID>();for(int i=0;i<n;i++)ids.add(UUID.randomUUID());var v=new RestartVote(ids,"10m",0);if(v.required()!=(n+1)/2)throw new AssertionError();for(int i=0;i<v.required()-1;i++)v.cast(ids.get(i),true);if(v.passed())throw new AssertionError("Below 50% passed");v.cast(ids.get(v.required()-1),true);if(!v.passed())throw new AssertionError("50% failed");if(v.cast(UUID.randomUUID(),true))throw new AssertionError("Newcomer voted");v.cast(ids.get(0),true);if(v.yes.size()!=v.required())throw new AssertionError("Duplicate counted");v.cast(ids.get(0),false);if(v.passed())throw new AssertionError("Changed vote not removed");}
 System.out.println("PASS ceil(50%) for 1..12 players, duplicate votes, vote changes, outsider rejection");
 }
}
