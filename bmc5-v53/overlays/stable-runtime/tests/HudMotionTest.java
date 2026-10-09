package pl.aridlin.fixture;

import com.google.gson.JsonParser;
import java.util.ArrayList;
import java.util.List;
import pl.aridlin.psychiatrykroles.runtime.HudSchema;
import pl.aridlin.psychiatrykroles.runtime.client.HudMotion;
import pl.aridlin.psychiatrykroles.runtime.client.VisualExpressions;

/** Protocol and render-math checks without launching Minecraft or allocating a game client. */
public final class HudMotionTest {
    private static int checks;
    private static void check(boolean value,String label){checks++;if(!value)throw new AssertionError(label);}
    private static void reject(Runnable action,String label){
        boolean rejected=false;try{action.run();}catch(RuntimeException expected){rejected=true;}
        check(rejected,label);
    }
    private static final String HEALTH="{\"op\":\"div\",\"args\":[{\"var\":\"health\"},{\"var\":\"max_health\"}]}";
    private static final String MOVING="{\"op\":\"gt\",\"args\":[{\"var\":\"speed\"},0.2]}";
    private static HudSchema.Node node(String id,String visible,String x,String y,String value){
        return new HudSchema.Node(id,"progress",.1,.2,.3,.04,null,0xFFFFFFFF,0x44000000,.5,
            null,null,visible,x,y,value);
    }
    private static VisualExpressions.Expression compile(String raw){
        return VisualExpressions.compile(JsonParser.parseString(raw),new VisualExpressions.Budget());
    }
    public static void main(String[] args){
        var animated=node("health",MOVING,"{\"op\":\"add\",\"args\":[0.1,{\"op\":\"mul\",\"args\":[{\"var\":\"time\"},0.02]}]}",
            null,HEALTH);
        var scene=new HudSchema.Scene("stats",List.of(animated));
        String wire=HudSchema.JSON.toJson(HudSchema.replace(scene));
        check(HudSchema.parse(wire).scene().nodes().get(0).equals(animated),"animated scene roundtrips");
        var legacy=new HudSchema.Node("old","text",.1,.1,0,0,"old",-1,0,0,null,null);
        String oldWire=HudSchema.JSON.toJson(HudSchema.replace(new HudSchema.Scene("legacy",List.of(legacy))));
        check(!oldWire.contains("visibleWhen")&&!oldWire.contains("xRule"),"old scene wire unchanged");
        check(HudSchema.parse(oldWire).scene().nodes().get(0).equals(legacy),"old scene roundtrips");
        double[] vars=new double[VisualExpressions.VARIABLE_COUNT];
        vars[0]=2;vars[1]=.3;vars[17]=8;vars[18]=20;
        check(HudMotion.visible(compile(MOVING),vars),"movement shows node");
        vars[1]=0;check(!HudMotion.visible(compile(MOVING),vars),"movement hides node");
        check(Math.abs(HudMotion.progress(compile(HEALTH),0,vars)-.4)<1e-9,"live health progress");
        check(HudMotion.position(compile("2"),.1,.3,vars)==.7,"animated x clipped to node width");
        check(HudMotion.position(compile("-1"),.1,.3,vars)==0,"negative x clipped");
        check(HudMotion.progress(compile("8"),0,vars)==1,"progress clipped");
        check(HudMotion.position(null,.1,.3,vars)==.1,"legacy static fallback");
        HudSchema.validate(new HudSchema.Scene("timer",List.of(node("a",null,"{\"var\":\"scene_time\"}",null,null))));
        vars[22]=.25;
        check(HudMotion.position(compile("{\"var\":\"scene_time\"}"),.1,.3,vars)==.25,
            "scene-local animation time");
        reject(()->HudSchema.validate(new HudSchema.Scene("s",List.of(node("a","{\"op\":\"exec\",\"args\":[1]}",null,null,null)))),"remote code denied");
        reject(()->HudSchema.validate(new HudSchema.Scene("s",List.of(node("a","{\"var\":\"private_field\"}",null,null,null)))),"unknown client state denied");
        reject(()->HudSchema.validate(new HudSchema.Scene("s",List.of(node("a","{\"var\":\"battery\"}",null,null,null)))),"feature-specific state denied");
        reject(()->HudSchema.validate(new HudSchema.Scene("s",List.of(node("a","1"," ".repeat(1025),null,null)))),"long expression denied");
        var costly=new ArrayList<HudSchema.Node>();
        for(int i=0;i<22;i++)costly.add(node("n"+i,MOVING,null,null,null));
        reject(()->HudSchema.validate(new HudSchema.Scene("costly",costly)),"per-scene expression budget");
        var shared=new ArrayList<HudSchema.Node>();
        for(int i=0;i<16;i++)shared.add(node("n"+i,MOVING,null,null,null));
        HudSchema.validateCollection(List.of(new HudSchema.Scene("a",shared),new HudSchema.Scene("b",shared)));
        check(true,"two 48-node expression scenes accepted");
        reject(()->HudSchema.validateCollection(List.of(new HudSchema.Scene("a",shared),
            new HudSchema.Scene("b",shared),new HudSchema.Scene("c",shared))),"aggregate expression budget");
        System.out.println("HUD_MOTION_PASS checks="+checks);
    }
}
