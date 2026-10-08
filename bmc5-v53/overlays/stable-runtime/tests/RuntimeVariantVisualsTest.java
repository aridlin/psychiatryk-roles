package pl.aridlin.psychiatrykroles.runtime.block;

import java.util.Map;

/** Can run without a Minecraft world; all user-authored geometry is bounded before rendering. */
public final class RuntimeVariantVisualsTest {
    private static void ok(boolean value,String message){if(!value)throw new AssertionError(message);}
    private static void rejects(String raw){
        try{RuntimeVariantVisuals.parse(raw);throw new AssertionError("Accepted malformed runtime block document");}
        catch(IllegalArgumentException expected){}
    }
    public static void main(String[] args){
        String valid="""
            {"schema":1,"variants":[{"id":"wood/chair","boxes":[
              {"from":[0,0,0],"to":[16,2,16],"texture":"blocks/chair","color":"#805533"},
              {"from":[1,2,1],"to":[3,12,3],"texture":"blocks/chair"}]}]}
            """;
        Map<String,RuntimeVariantVisuals.Variant> parsed=RuntimeVariantVisuals.parse(valid);
        ok(parsed.size()==1&&parsed.get("wood/chair").boxes().size()==2,"Expected two bounded cuboids");
        var first=parsed.get("wood/chair").boxes().get(0);
        ok(first.x1()==1f&&first.y1()==0.125f&&first.red()==128,"Coordinates or tint changed");
        rejects("{\"schema\":2,\"variants\":[]}");
        rejects("{\"schema\":1,\"variants\":[{\"id\":\"x\",\"boxes\":[{\"from\":[0,0,0],\"to\":[17,1,1],\"texture\":\"a\"}]}]}");
        rejects("{\"schema\":1,\"variants\":[{\"id\":\"x\",\"boxes\":[{\"from\":[2,0,0],\"to\":[1,1,1],\"texture\":\"a\"}]}]}");
        rejects("{\"schema\":1,\"variants\":[{\"id\":\"x\",\"boxes\":[{\"from\":[0,0,0],\"to\":[1,1,1],\"texture\":\"a\"}]},{\"id\":\"x\",\"boxes\":[{\"from\":[0,0,0],\"to\":[1,1,1],\"texture\":\"a\"}]}]}");
        String box="{\"from\":[0,0,0],\"to\":[1,1,1],\"texture\":\"a\"}";
        rejects("{\"schema\":1,\"variants\":[{\"id\":\"x\",\"boxes\":["+String.join(",",java.util.Collections.nCopies(17,box))+"]}]}");
        System.out.println("RuntimeVariantVisualsTest passed");
    }
}
