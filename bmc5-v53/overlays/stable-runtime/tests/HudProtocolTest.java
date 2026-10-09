import java.util.ArrayList;
import java.util.List;
import pl.aridlin.psychiatrykroles.runtime.HudSchema;

/** Bounded, server-authored HUD messages; no Minecraft client or OpenGL required. */
public final class HudProtocolTest {
    private static int checks;
    private static void check(boolean condition,String reason){checks++;if(!condition)throw new AssertionError(reason);}
    private static void rejects(Runnable action,String reason){
        boolean rejected=false;try{action.run();}catch(RuntimeException expected){rejected=true;}
        check(rejected,reason);
    }
    private static HudSchema.Node node(String id,String type,double x,double y,double w,double h,
                                       String text,double value,String item,String asset){
        return new HudSchema.Node(id,type,x,y,w,h,text,0xFFFFFFFF,0x80000000,value,item,asset);
    }
    public static void main(String[] args){
        var scene=new HudSchema.Scene("status",List.of(
            node("label","text",.03,.04,0,0,"Szybkiorzech",0,null,null),
            node("back","rect",.03,.08,.28,.025,null,0,null,null),
            node("health","progress",.03,.08,.28,.025,null,.75,null,null),
            node("icon","item",.30,.04,0,0,null,0,"minecraft:diamond",null),
            node("badge","image",.42,.04,.04,.07,null,0,null,"ui/hud/badge")));
        var replace=HudSchema.replace(scene);String raw=HudSchema.JSON.toJson(replace);
        check(HudSchema.isHud(raw),"HUD message recognized");
        check(HudSchema.parse(raw).equals(replace),"five-node HUD roundtrip");
        var clear=HudSchema.clear("status");
        check(HudSchema.parse(HudSchema.JSON.toJson(clear)).equals(clear),"clear roundtrip");
        check(!HudSchema.isHud("{\"schema\":1,\"channel\":\"menu\"}"),"menu snapshot stays separate");
        rejects(()->HudSchema.id("../escape"),"scene traversal denied");
        rejects(()->HudSchema.validate(new HudSchema.Scene("status",List.of(scene.nodes().get(0),scene.nodes().get(0)))),"duplicate nodes denied");
        rejects(()->HudSchema.validate(new HudSchema.Scene("status",List.of(node("bad","image",0,0,.2,.2,null,0,null,"../bad")))),"image traversal denied");
        rejects(()->HudSchema.validate(new HudSchema.Scene("status",List.of(node("bad","item",0,0,0,0,null,0,"bad item",null)))),"bad item ID denied");
        rejects(()->HudSchema.validate(new HudSchema.Scene("status",List.of(node("bad","progress",0,0,.1,.1,null,1.01,null,null)))),"progress overflow denied");
        rejects(()->HudSchema.validate(new HudSchema.Scene("status",List.of(node("bad","rect",.9,.1,.2,.1,null,0,null,null)))),"off-screen area denied");
        rejects(()->HudSchema.validate(new HudSchema.Scene("status",List.of(node("bad","text",Double.NaN,0,0,0,"x",0,null,null)))),"nonfinite coordinate denied");
        rejects(()->HudSchema.validate(new HudSchema.Scene("status",List.of(node("bad","button",0,0,0,0,null,0,null,null)))),"unknown node denied");
        var many=new ArrayList<HudSchema.Node>();for(int i=0;i<33;i++)many.add(node("n"+i,"text",0,0,0,0,"x",0,null,null));
        rejects(()->HudSchema.validate(new HudSchema.Scene("status",many)),"node count denied");
        var escaped=new ArrayList<HudSchema.Node>();for(int i=0;i<32;i++)escaped.add(node("e"+i,"text",0,0,0,0,"<".repeat(160),0,null,null));
        rejects(()->HudSchema.validate(new HudSchema.Scene("status",escaped)),"escaped wire size denied before scene storage");
        var unicode=new ArrayList<HudSchema.Node>();for(int i=0;i<32;i++)unicode.add(node("u"+i,"text",0,0,0,0,"界".repeat(160),0,null,null));
        rejects(()->HudSchema.validate(new HudSchema.Scene("status",unicode)),"UTF-8 wire size denied before scene storage");
        var bulkA=new ArrayList<HudSchema.Node>();var bulkB=new ArrayList<HudSchema.Node>();
        for(int i=0;i<32;i++){
            bulkA.add(node("a"+i,"text",0,0,0,0,"x",0,null,null));
            bulkB.add(node("b"+i,"text",0,0,0,0,"x",0,null,null));
        }
        var sceneA=new HudSchema.Scene("a",bulkA);var sceneB=new HudSchema.Scene("b",bulkB);
        HudSchema.validateCollection(List.of(sceneA,sceneB));check(true,"64 total nodes allowed");
        rejects(()->HudSchema.validateCollection(List.of(sceneA,sceneB,
            new HudSchema.Scene("c",List.of(node("c","text",0,0,0,0,"x",0,null,null))))),"65 total nodes denied");
        var nineItems=new ArrayList<HudSchema.Node>();for(int i=0;i<9;i++)nineItems.add(node("item"+i,"item",0,0,0,0,null,0,"minecraft:diamond",null));
        rejects(()->HudSchema.validateCollection(List.of(new HudSchema.Scene("items",nineItems))),"nine item draws denied");
        var images=new ArrayList<HudSchema.Node>();for(int i=0;i<17;i++)images.add(node("image"+i,"image",0,0,.01,.01,null,0,null,"ui/badge"));
        rejects(()->HudSchema.validateCollection(List.of(new HudSchema.Scene("images",images))),"seventeen image draws denied");
        rejects(()->HudSchema.parse(HudSchema.JSON.toJson(new HudSchema.Message(1,"hud","replace","different",scene))),"message/scene ID mismatch denied");
        rejects(()->HudSchema.parse(raw.replace("\"schema\":1","\"schema\":2")),"future schema denied");
        rejects(()->HudSchema.parse("[".repeat(20)+"]".repeat(20)),"deep message denied");
        rejects(()->HudSchema.parse("x".repeat(HudSchema.MAX_MESSAGE+1)),"oversize message denied");
        rejects(()->HudSchema.parse("{\"schema\":1,\"channel\":\"hud\",\"op\":\"clear\",\"id\":\"status\",\"scene\":{}}"),"clear with scene denied");
        System.out.println("HUD_PROTOCOL_PASS checks="+checks);
    }
}
