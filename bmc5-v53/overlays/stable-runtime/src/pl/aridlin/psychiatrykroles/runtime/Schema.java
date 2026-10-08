package pl.aridlin.psychiatrykroles.runtime;

import com.google.gson.*;
import java.util.*;
import java.security.*;
import java.nio.charset.StandardCharsets;

/** Frozen envelope version 1. Add optional fields, never change the wire layout. */
public final class Schema {
    public static final int MAX_DOCUMENT=65536, MAX_ACTION=2048, VERSION=1;
    public static final Gson JSON=new GsonBuilder().setPrettyPrinting().create();
    public record Entry(String id,String label,String kind,String action,String target,String text,double min,double max,double step,int permission,Map<String,String> props){
        public Entry(String id,String label,String kind,String action,String target,String text,double min,double max,double step,int permission){this(id,label,kind,action,target,text,min,max,step,permission,Map.of());}
    }
    public record Menu(String title,int permission,List<Entry> entries){}
    public record Variant(String base,String name,List<String> lore,List<String> ingredients,int count,String action,String target,double amount,int cooldownSeconds,boolean consume){}
    public record Step(String op,String target,double amount){}
    public record Behavior(int permission,List<String> conditions,List<Step> steps){}
    public record Config(int schema,Map<String,Menu> menus,Map<String,Variant> items,Map<String,Behavior> behaviors,Map<String,String> hooks){
        public Config(int schema,Map<String,Menu> menus,Map<String,Variant> items){this(schema,menus,items,Map.of(),Map.of());}
    }
    public record Control(String id,String label,String kind,String text,double value,double min,double max,double step,Map<String,String> props){
        public Control(String id,String label,String kind,String text,double value,double min,double max,double step){this(id,label,kind,text,value,min,max,step,Map.of());}
    }
    public record View(int schema,String revision,String session,String menu,String title,List<Control> controls){}
    public record Action(int schema,String revision,String session,String menu,String control,String value){}
    public static JsonObject object(String raw,int maximum){
        if(raw==null||raw.length()>maximum)throw new IllegalArgumentException("Document exceeds limit");
        int depth=0;boolean string=false,escape=false;
        for(char c:raw.toCharArray()){if(string){if(escape)escape=false;else if(c=='\\')escape=true;else if(c=='\"')string=false;}else if(c=='\"')string=true;else if(c=='{'||c=='['){if(++depth>12)throw new IllegalArgumentException("Too deeply nested");}else if(c=='}'||c==']')depth--;}
        if(depth!=0||string)throw new IllegalArgumentException("Incomplete document");
        var json=JsonParser.parseString(raw);if(!json.isJsonObject())throw new IllegalArgumentException("Expected object");return json.getAsJsonObject();
    }
    public static Config config(String raw){
        var parsed=JSON.fromJson(object(raw,MAX_DOCUMENT),Config.class);
        var c=new Config(parsed.schema,parsed.menus,parsed.items,parsed.behaviors==null?Map.of():parsed.behaviors,parsed.hooks==null?Map.of():parsed.hooks);
        require(c.schema==VERSION,"Unsupported config schema");require(c.menus!=null&&c.menus.size()<=24&&c.menus.containsKey("main"),"Menus missing or excessive");require(c.items!=null&&c.items.size()<=128,"Items missing or excessive");
        require(c.behaviors.size()<=128,"Too many behaviors");
        require(c.hooks.size()<=16,"Too many event hooks");for(var hook:c.hooks.entrySet()){
            require(Set.of("join","respawn","dimension_change","block_break","kill","sneak","unsneak","interval_1s").contains(hook.getKey()),"Unknown event hook");
            require(c.behaviors.containsKey(hook.getValue()),"Event hook references missing behavior");
        }
        for(var behavior:c.behaviors.entrySet()){
            id(behavior.getKey());var b=behavior.getValue();require(b!=null,"Null behavior");permission(b.permission);require(b.conditions!=null&&b.conditions.size()<=12,"Invalid conditions");require(b.steps!=null&&b.steps.size()<=24,"Invalid behavior steps");
            for(String condition:b.conditions){text(condition,180);require(Set.of("sneaking","not_sneaking","creative","survival","on_ground","in_water").contains(condition)||condition.startsWith("dimension:")&&validRegistry(condition.substring(10)),"Unknown condition");}
            for(var step:b.steps){require(step!=null,"Null step");require(Set.of("message","command","function","effect","sound","particle","velocity","open","give").contains(step.op),"Unknown behavior operation");text(step.target==null?"":step.target,256);require(Double.isFinite(step.amount)&&step.amount>=0&&step.amount<=600,"Invalid operation amount");
                if(Set.of("function","effect","sound","particle","give").contains(step.op))registry(step.target);
                if(step.op.equals("open"))require(c.menus.containsKey(step.target),"Unknown menu target");
                if(Set.of("command","message").contains(step.op))require(step.target!=null&&!step.target.isBlank()&&!step.target.startsWith("/")&&!step.target.contains("\n")&&!step.target.contains("\r"),"Invalid behavior text");
                if(step.op.equals("velocity"))require(step.amount<=1.5,"Velocity too large");
            }
        }
        for(var m:c.menus.entrySet()){
            id(m.getKey());var menu=m.getValue();require(menu!=null,"Null menu");text(menu.title,80);permission(menu.permission);require(menu.entries!=null&&menu.entries.size()<=48,"Too many controls");var ids=new HashSet<String>();
            for(var e:menu.entries){require(e!=null,"Null control");id(e.id);require(ids.add(e.id),"Duplicate control");text(e.label,100);text(e.text==null?"":e.text,512);permission(e.permission);
                require(Set.of("button","number","toggle","label","text","heading","rect","progress","item","image","spacer").contains(e.kind),"Unknown control kind");require(Set.of("open","command","message","setting","behavior","none").contains(e.action),"Unknown action");text(e.target==null?"":e.target,256);props(e.props);
                if(e.action.equals("behavior"))require(c.behaviors.containsKey(e.target),"Unknown behavior target");
                if(Set.of("label","text","heading","rect","progress","item","image","spacer").contains(e.kind))require(e.action.equals("none"),"Display components cannot send actions");
                if(e.kind.equals("image"))require(e.props!=null&&e.props.containsKey("asset"),"Image needs preloaded asset ID");
                if(e.kind.equals("button"))require(!e.action.equals("setting"),"Button cannot edit setting");
                if(e.kind.equals("number")||e.kind.equals("toggle"))require(e.action.equals("setting"),"Editable control needs setting binding");
                if(e.action.equals("open"))require(c.menus.containsKey(e.target),"Missing target menu");
                if(e.action.equals("command"))require(e.target!=null&&!e.target.isBlank()&&!e.target.contains("\n")&&!e.target.startsWith("/"),"Invalid command");
                require(Double.isFinite(e.min)&&Double.isFinite(e.max)&&Double.isFinite(e.step),"Nonfinite control");
                if(e.kind.equals("number"))require(e.min<=e.max&&e.step>0&&e.max-e.min<=1e6,"Invalid number bounds");
                if(e.action.equals("setting"))require(e.permission>=2&&menu.permission>=2&&Settings.known(e.target),"Settings need operator menu and known binding");
            }
        }
        for(var entry:c.items.entrySet()){
            id(entry.getKey());var v=entry.getValue();require(v!=null,"Null variant");registry(v.base);text(v.name,100);require(v.lore!=null&&v.lore.size()<=8,"Lore too long");v.lore.forEach(s->text(s,200));require(v.ingredients!=null&&v.ingredients.size()<=9,"Recipe too large");v.ingredients.forEach(Schema::registry);require(v.count>=1&&v.count<=64,"Invalid item count");
            require(Set.of("none","message","effect","command","dash","behavior").contains(v.action),"Unknown item action");text(v.target==null?"":v.target,256);require(v.cooldownSeconds>=0&&v.cooldownSeconds<=86400,"Invalid cooldown");require(Double.isFinite(v.amount)&&v.amount>=0&&v.amount<=600,"Invalid item amount");
            if(v.action.equals("behavior"))require(c.behaviors.containsKey(v.target),"Unknown item behavior");
            if(v.action.equals("message")||v.action.equals("command"))require(v.target!=null&&!v.target.isBlank(),"Active item action requires target");
            if(v.action.equals("command"))require(!v.target.startsWith("/")&&!v.target.contains("\n")&&!v.target.contains("\r"),"Invalid item command");
            if(v.action.equals("dash"))require(v.amount<=1.5,"Dash exceeds safe velocity limit");
            if(v.action.equals("effect"))registry(v.target);
        }
        return c;
    }
    public static View view(String raw){
        var v=JSON.fromJson(object(raw,MAX_DOCUMENT),View.class);require(v.schema==VERSION,"Unsupported view schema");text(v.revision,64);text(v.session,64);id(v.menu);text(v.title,80);require(v.controls!=null&&v.controls.size()<=48,"Too many controls");var seen=new HashSet<String>();
        for(var c:v.controls){require(c!=null,"Null control");id(c.id);text(c.kind,32);require(seen.add(c.id),"Duplicate control");text(c.label,100);text(c.text==null?"":c.text,512);props(c.props);require(Double.isFinite(c.value)&&Double.isFinite(c.min)&&Double.isFinite(c.max)&&Double.isFinite(c.step),"Invalid number");if(c.kind.equals("number"))require(c.min<=c.max&&c.step>0,"Invalid number bounds");}
        return v;
    }
    public static Action action(String raw){var a=JSON.fromJson(object(raw,MAX_ACTION),Action.class);require(a.schema==VERSION,"Unsupported action schema");text(a.revision,64);text(a.session,64);id(a.menu);id(a.control);text(a.value==null?"":a.value,256);return a;}
    public static String revision(Config c){try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(JSON.toJson(c).getBytes(StandardCharsets.UTF_8)));}catch(Exception e){throw new IllegalStateException(e);}}
    public static void require(boolean ok,String message){if(!ok)throw new IllegalArgumentException(message);}
    public static void id(String s){require(s!=null&&s.matches("[a-z0-9_.-]{1,64}"),"Invalid identifier");}
    public static void registry(String s){require(s!=null&&s.length()<=180&&s.matches("[a-z0-9_.-]+:[a-z0-9_./-]+"),"Invalid registry identifier");}
    private static boolean validRegistry(String s){return s!=null&&s.length()<=180&&s.matches("[a-z0-9_.-]+:[a-z0-9_./-]+");}
    public static void text(String s,int max){require(s!=null&&s.length()<=max,"Text exceeds limit");}
    public static void props(Map<String,String> p){if(p==null)return;require(p.size()<=24,"Too many component properties");for(var e:p.entrySet()){
        require(e.getKey()!=null&&e.getKey().matches("[A-Za-z][A-Za-z0-9_]{0,31}"),"Invalid component property");text(e.getValue(),256);
        if(Set.of("x","y","w","h","value").contains(e.getKey())){double value=Double.parseDouble(e.getValue());require(Double.isFinite(value)&&value>=0&&value<=1,"Invalid component geometry");}
        if(e.getKey().equals("item"))registry(e.getValue());
        if(e.getKey().equals("asset"))AssetSchema.id(e.getValue());
        if(e.getKey().equals("color"))require(e.getValue().matches("#[0-9A-Fa-f]{8}"),"Invalid component color");
    }}
    private static void permission(int p){require(p>=0&&p<=4,"Invalid permission");}
}
