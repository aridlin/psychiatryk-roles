package pl.aridlin.psychiatrykroles.runtime;

import com.google.gson.Gson;
import com.google.gson.JsonParser;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.resources.ResourceLocation;
import pl.aridlin.psychiatrykroles.runtime.client.RuntimeItemVisuals;

public final class RuntimeItemVisualsTest {
    private static int checks;
    private static void check(boolean okay,String message){checks++;if(!okay)throw new AssertionError(message);}
    private static void rejects(Runnable action,String message){
        boolean failed=false;try{action.run();}catch(RuntimeException expected){failed=true;}
        check(failed,message);
    }
    public static void main(String[] args)throws Exception {
        var texture=ResourceLocation.parse("psychiatryk_runtime:asset/abc");
        String valid="{\"schema\":1,\"items\":[{\"id\":\"poker_chip\",\"asset\":\"items/chip\"}]}";
        var map=RuntimeItemVisuals.parse(valid,id->id.equals("items/chip")?texture:null);
        check(map.size()==1&&texture.equals(map.get("poker_chip")),"item texture mapping compiled");
        rejects(()->RuntimeItemVisuals.parse(valid,id->null),"missing preloaded image rejected");
        rejects(()->RuntimeItemVisuals.parse("{\"schema\":2,\"items\":[]}",id->texture),"schema version rejected");
        rejects(()->RuntimeItemVisuals.parse("{\"schema\":1,\"items\":[{\"id\":\"x\",\"asset\":\"../bad\"}]}",id->texture),"asset traversal rejected");
        rejects(()->RuntimeItemVisuals.parse("{\"schema\":1,\"items\":[{\"id\":\"x\",\"asset\":\"a\"},{\"id\":\"x\",\"asset\":\"b\"}]}",id->texture),"duplicate variant rejected");
        var many=new ArrayList<Map<String,String>>();for(int i=0;i<129;i++)many.add(Map.of("id","v"+i,"asset","items/shared"));
        rejects(()->RuntimeItemVisuals.parse(new Gson().toJson(Map.of("schema",1,"items",many)),id->texture),"variant count bounded");
        var model=JsonParser.parseString(Files.readString(Path.of(args[0]))).getAsJsonObject();
        check("builtin/entity".equals(model.get("parent").getAsString()),"static custom-renderer model baked at startup");

        var entries=new ArrayList<Schema.Entry>();
        var props=new LinkedHashMap<String,String>();for(int i=0;i<3;i++)props.put("p"+i,"x".repeat(172));
        for(int i=0;i<48;i++)entries.add(new Schema.Entry("e"+i,"L".repeat(100),"text","none","", "T".repeat(512),0,0,0,0,props));
        var config=new Schema.Config(1,Map.of("main",new Schema.Menu("M",0,entries)),Map.of(),Map.of(),Map.of());
        String compact=new Gson().toJson(config);
        check(compact.length()<=Schema.MAX_DOCUMENT,"oversize-view input is a valid-size config");
        var parsed=Schema.config(compact);
        var controls=new ArrayList<Schema.Control>();for(var e:parsed.menus().get("main").entries())
            controls.add(new Schema.Control(e.id(),e.label(),e.kind(),e.text(),0,e.min(),e.max(),e.step(),e.props()));
        var oversized=new Schema.View(1,"a".repeat(64),"b".repeat(36),"main","M",controls);
        check(Schema.JSON.toJson(oversized).length()>Schema.MAX_DOCUMENT,"valid config expands beyond wire cap");
        check(!RuntimeServer.viewFits(oversized),"server preflight rejects expanded view before send");
        check(RuntimeServer.viewFits(new Schema.View(1,"a".repeat(64),"b".repeat(36),"main","M",List.of())),"ordinary view admitted");
        System.out.println("RUNTIME_ITEM_VISUALS_PASS checks="+checks+" config="+compact.length()+" view="+Schema.JSON.toJson(oversized).length());
    }
}
