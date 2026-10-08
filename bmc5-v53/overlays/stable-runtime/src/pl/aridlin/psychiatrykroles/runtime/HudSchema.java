package pl.aridlin.psychiatrykroles.runtime;

import com.google.gson.Gson;
import com.google.gson.JsonParser;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Frozen, declarative HUD scene transported inside the optional runtime Snapshot envelope. */
public final class HudSchema {
    private HudSchema() {}
    public static final int VERSION=1, MAX_MESSAGE=16*1024, MAX_SCENES=8, MAX_NODES=32;
    public static final int MAX_TOTAL_NODES=64, MAX_ITEMS=8, MAX_IMAGES=16;
    public static final Gson JSON=new Gson();
    public record Node(String id,String type,double x,double y,double w,double h,String text,int color,int background,
                       double value,String item,String asset) {}
    public record Scene(String id,List<Node> nodes) {}
    public record Message(int schema,String channel,String op,String id,Scene scene) {}
    private static boolean withinMessageLimit(String raw){
        return raw!=null&&raw.length()<=MAX_MESSAGE&&raw.getBytes(StandardCharsets.UTF_8).length<=MAX_MESSAGE;
    }

    public static boolean isHud(String raw){
        if(!withinMessageLimit(raw))return false;
        try{var parsed=Schema.object(raw,MAX_MESSAGE);var type=parsed.get("channel");
            return type!=null&&type.isJsonPrimitive()&&"hud".equals(type.getAsString());
        }catch(RuntimeException malformed){return false;}
    }
    public static Message replace(Scene scene){validate(scene);return new Message(VERSION,"hud","replace",scene.id(),scene);}
    public static Message clear(String id){id(id);return new Message(VERSION,"hud","clear",id,null);}
    public static Message parse(String raw){
        Schema.require(withinMessageLimit(raw),"HUD message limit");
        int depth=0;boolean quoted=false,escaped=false;
        for(char c:raw.toCharArray()){
            if(quoted){if(escaped)escaped=false;else if(c=='\\')escaped=true;else if(c=='"')quoted=false;}
            else if(c=='"')quoted=true;
            else if(c=='{'||c=='[')Schema.require(++depth<=8,"HUD depth");
            else if(c=='}'||c==']')Schema.require(--depth>=0,"HUD balance");
        }
        Schema.require(depth==0&&!quoted,"HUD document incomplete");
        var m=JSON.fromJson(JsonParser.parseString(raw),Message.class);
        Schema.require(m!=null&&m.schema()==VERSION&&"hud".equals(m.channel()),"HUD schema");id(m.id());
        Schema.require("replace".equals(m.op())||"clear".equals(m.op()),"HUD operation");
        if(m.op().equals("replace")){validate(m.scene());Schema.require(m.id().equals(m.scene().id()),"HUD scene ID mismatch");}
        else Schema.require(m.scene()==null,"Clear cannot contain a scene");
        return m;
    }
    public static void id(String value){Schema.require(value!=null&&value.matches("[a-z0-9_.-]{1,48}"),"HUD ID");}
    public static void validate(Scene scene){
        Schema.require(scene!=null,"Missing HUD scene");id(scene.id());
        Schema.require(scene.nodes()!=null&&scene.nodes().size()<=MAX_NODES,"HUD node limit");
        Set<String> used=new HashSet<>();
        for(var n:scene.nodes()){
            Schema.require(n!=null,"Null HUD node");id(n.id());Schema.require(used.add(n.id()),"Duplicate HUD node");
            Schema.require(n.type()!=null&&Set.of("text","rect","progress","item","image").contains(n.type()),"HUD node type");
            Schema.require(Double.isFinite(n.x())&&Double.isFinite(n.y())&&Double.isFinite(n.w())
                &&Double.isFinite(n.h())&&Double.isFinite(n.value()),"Nonfinite HUD coordinate");
            Schema.require(n.x()>=0&&n.y()>=0&&n.x()<=1&&n.y()<=1&&n.w()>=0&&n.h()>=0
                &&n.w()<=1-n.x()&&n.h()<=1-n.y(),"HUD coordinate outside screen");
            if(n.type().equals("rect")||n.type().equals("progress")||n.type().equals("image"))
                Schema.require(n.w()>0&&n.h()>0,"HUD area must be positive");
            Schema.require(n.text()==null||n.text().length()<=160,"HUD text limit");
            if(n.type().equals("text"))Schema.require(n.text()!=null,"HUD text missing");
            if(n.type().equals("progress"))Schema.require(n.value()>=0&&n.value()<=1,"HUD progress range");
            if(n.type().equals("item"))Schema.require(n.item()!=null&&n.item().length()<=180
                &&n.item().matches("[a-z0-9_.-]+:[a-z0-9_./-]+"),"HUD item ID");
            if(n.type().equals("image"))AssetSchema.id(n.asset());
        }
        // Gson may expand characters such as '<' to six-byte escapes; check the wire form
        // before a server caller stores the scene or queues it for a later send.
        Schema.require(withinMessageLimit(JSON.toJson(new Message(VERSION,"hud","replace",scene.id(),scene))),
            "HUD serialized message limit");
    }
    /** Check the complete player HUD after a candidate scene replaces its old version. */
    public static void validateCollection(Iterable<Scene> scenes){
        int sceneCount=0,nodeCount=0,itemCount=0,imageCount=0;
        for(var scene:scenes){
            validate(scene);sceneCount++;nodeCount+=scene.nodes().size();
            for(var node:scene.nodes()){
                if(node.type().equals("item"))itemCount++;
                else if(node.type().equals("image"))imageCount++;
            }
            Schema.require(sceneCount<=MAX_SCENES&&nodeCount<=MAX_TOTAL_NODES
                &&itemCount<=MAX_ITEMS&&imageCount<=MAX_IMAGES,"HUD total scene budget");
        }
    }
}
