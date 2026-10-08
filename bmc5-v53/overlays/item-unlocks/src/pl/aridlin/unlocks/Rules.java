package pl.aridlin.unlocks;

import com.google.gson.*;
import java.nio.file.*;
import java.time.*;
import java.util.*;

/** Persistent server policy. Dates always mean midnight in Poland, including DST. */
public final class Rules {
    public static final ZoneId ZONE = ZoneId.of("Europe/Warsaw");
    public static final Path FILE = Path.of("config/psychiatryk-item-unlocks.json");
    private static final Gson JSON = new GsonBuilder().setPrettyPrinting().create();
    private static volatile Map<String,Rule> rules = load();
    public record Rule(String name, String unlockAt, String profession) {
        public boolean locked(Instant now) {return unlockAt.equals("never") || (!unlockAt.equals("now") && now.isBefore(LocalDate.parse(unlockAt).atStartOfDay(ZONE).toInstant()));}
    }
    private static Map<String,Rule> defaults() {
        var m = new TreeMap<String,Rule>();
        add(m,"gardening_table","Florysta / Florist","2026-10-08","florist");
        add(m,"woodworking_table","Stolarz / Woodworker","2026-10-08","woodworker");
        add(m,"chiller","Lodziarz / Iceman","2026-10-08","iceman");
        add(m,"hunting_post","Łowca / Hunter","2026-10-08","hunter");
        add(m,"blueprint_table","Inżynier / Engineer","2026-10-10","engineer");
        add(m,"mining_bench","Górnik / Miner","2026-10-12","miner");
        add(m,"decayed_workbench","Netherolog / Netherologist","2026-10-14","netherian");
        add(m,"oceanography_table","Oceanograf / Oceanographer","2026-10-16","oceanographer");
        add(m,"gilded_station","Pozłacane stanowisko / Gilded Station","2026-10-18","ascended");
        add(m,"purpur_altar","Enderolog / Enderologist","2026-11-01","enderian");
        return m;
    }
    private static void add(Map<String,Rule> m,String id,String name,String date,String profession) {m.put("morevillagers:"+id,new Rule(name,date,"morevillagers:"+profession));}
    private static Map<String,Rule> load() {
        if(!Files.exists(FILE)) return Collections.unmodifiableMap(defaults());
        try {var m=new TreeMap<String,Rule>();var obj=JsonParser.parseString(Files.readString(FILE)).getAsJsonObject().getAsJsonObject("items");
            for(var e:obj.entrySet()){var r=JSON.fromJson(e.getValue(),Rule.class);validate(r);m.put(e.getKey(),r);} return Collections.unmodifiableMap(m);
        } catch(Exception e){throw new IllegalStateException("Invalid item unlock policy: "+FILE,e);}
    }
    private static void validate(Rule r){if(r==null||r.name()==null||r.unlockAt()==null)throw new IllegalArgumentException("Missing rule fields");r.locked(Instant.now());}
    public static Map<String,Rule> all(){return rules;}
    public static boolean locked(String id){return locked(id,Instant.now());}
    public static boolean locked(String id,Instant now){Rule r=rules.get(id);return r!=null&&r.locked(now);}
    public static void ensureSaved() throws Exception {if(!Files.exists(FILE))save(rules);}
    public static synchronized void set(String id,String date) throws Exception {
        var next=new TreeMap<>(rules);var old=next.get(id);var r=new Rule(old==null?id:old.name(),date,old==null?null:old.profession());validate(r);next.put(id,r);save(next);rules=Collections.unmodifiableMap(next);
    }
    private static void save(Map<String,Rule> next) throws Exception {
        Files.createDirectories(FILE.getParent());var root=new JsonObject();root.addProperty("timezone",ZONE.toString());root.add("items",JSON.toJsonTree(next));
        var tmp=FILE.resolveSibling(FILE.getFileName()+".tmp");Files.writeString(tmp,JSON.toJson(root)+"\n");
        try{Files.move(tmp,FILE,StandardCopyOption.REPLACE_EXISTING,StandardCopyOption.ATOMIC_MOVE);}catch(AtomicMoveNotSupportedException e){Files.move(tmp,FILE,StandardCopyOption.REPLACE_EXISTING);}
    }
    public static String fingerprint(){return rules.entrySet().stream().map(e->e.getKey()+"="+e.getValue().locked(Instant.now())).sorted().reduce("",(a,b)->a+"|"+b);}
}
