import pl.aridlin.unlocks.Rules;
import java.time.*;
public class RulesTest {
 static int count;
 static void check(boolean condition){count++;if(!condition)throw new AssertionError("check "+count);}
 public static void main(String[]args)throws Exception{
  for(var e:Rules.all().entrySet()){
   Instant unlock=LocalDate.parse(e.getValue().unlockAt()).atStartOfDay(Rules.ZONE).toInstant();
   check(Rules.locked(e.getKey(),unlock.minusSeconds(1)));check(!Rules.locked(e.getKey(),unlock));check(!Rules.locked(e.getKey(),unlock.plusSeconds(1)));
  }
  check(!Rules.locked("morevillagers:hunting_post",Instant.parse("2026-10-08T20:00:00Z")));
  check(Rules.locked("morevillagers:purpur_altar",Instant.parse("2026-10-31T22:59:59Z")));
  check(!Rules.locked("morevillagers:purpur_altar",Instant.parse("2026-10-31T23:00:00Z")));
  check(!Rules.locked("minecraft:stone"));Rules.set("minecraft:stone","never");check(Rules.locked("minecraft:stone"));Rules.set("minecraft:stone","now");check(!Rules.locked("minecraft:stone"));
  boolean rejected=false;try{Rules.set("minecraft:stone","garbage");}catch(Exception e){rejected=true;}check(rejected);check(!Rules.locked("minecraft:stone"));
  check(java.nio.file.Files.readString(Rules.FILE).contains("minecraft:stone"));System.out.println("PASS "+count+" checks including midnight, DST, Hunter, arbitrary item and atomic edits");
 }
}
