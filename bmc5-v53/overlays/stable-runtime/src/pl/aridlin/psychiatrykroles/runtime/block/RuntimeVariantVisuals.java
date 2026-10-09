package pl.aridlin.psychiatrykroles.runtime.block;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.resources.ResourceLocation;
import pl.aridlin.psychiatrykroles.runtime.AssetSchema;
import pl.aridlin.psychiatrykroles.runtime.client.AssetClient;

/** Strictly bounded interpretation of the live JSON asset blocks/runtime.json. */
public final class RuntimeVariantVisuals {
    private RuntimeVariantVisuals() {}
    public static final String ASSET_ID="blocks/runtime";
    public static final int MAX_VARIANTS=32,MAX_BOXES_PER_VARIANT=16,MAX_TOTAL_BOXES=256;
    public record Box(float x0,float y0,float z0,float x1,float y1,float z1,String texture,int red,int green,int blue,
                      ResourceLocation textureLocation) {
        public Box(float x0,float y0,float z0,float x1,float y1,float z1,String texture,int red,int green,int blue){
            this(x0,y0,z0,x1,y1,z1,texture,red,green,blue,null);
        }
    }
    public record Variant(String id,List<Box> boxes) {}
    private static volatile String parsedRevision="";
    private static volatile Map<String,Variant> parsed=Map.of();

    /** Parse and resolve all geometry on asset Ready, never on the first rendered block. */
    public static void install(){
        String revision=AssetClient.revision();
        String raw=AssetClient.json(ASSET_ID).orElse(null);
        if(raw==null){parsed=Map.of();parsedRevision=revision;return;}
        Map<String,Variant> source=parse(raw);var ready=new HashMap<String,Variant>();
        for(var entry:source.entrySet()){
            var boxes=new ArrayList<Box>(entry.getValue().boxes().size());
            for(var box:entry.getValue().boxes()){
                var texture=AssetClient.texture(box.texture()).orElseThrow(()->new IllegalArgumentException("Missing runtime block texture: "+box.texture()));
                boxes.add(new Box(box.x0(),box.y0(),box.z0(),box.x1(),box.y1(),box.z1(),box.texture(),
                    box.red(),box.green(),box.blue(),texture));
            }
            ready.put(entry.getKey(),new Variant(entry.getKey(),List.copyOf(boxes)));
        }
        parsed=Map.copyOf(ready);parsedRevision=revision;
    }

    /** Called only on the render thread; never holds texture objects beyond a manifest revision. */
    public static Variant get(String id){
        if(!AssetClient.ready())return null;
        return AssetClient.revision().equals(parsedRevision)?parsed.get(id):null;
    }
    public static Map<String,Variant> parse(String raw){
        AssetSchema.require(raw!=null&&raw.length()<=AssetSchema.MAX_JSON_FILE,"Runtime block document size");
        JsonElement value=JsonParser.parseString(raw);
        AssetSchema.require(value.isJsonObject(),"Runtime block document must be an object");
        JsonObject root=value.getAsJsonObject();
        AssetSchema.require(root.has("schema")&&root.get("schema").getAsInt()==1,"Runtime block schema");
        JsonArray definitions=array(root,"variants");
        AssetSchema.require(definitions.size()<=MAX_VARIANTS,"Runtime block variant limit");
        var result=new HashMap<String,Variant>();
        int total=0;
        for(JsonElement entry:definitions){
            AssetSchema.require(entry.isJsonObject(),"Runtime block variant object");
            JsonObject definition=entry.getAsJsonObject();
            String id=string(definition,"id");
            RuntimeVariantBlockEntity.requireVariantId(id);
            JsonArray boxes=array(definition,"boxes");
            AssetSchema.require(boxes.size()>0&&boxes.size()<=MAX_BOXES_PER_VARIANT,"Runtime block box limit");
            total+=boxes.size();AssetSchema.require(total<=MAX_TOTAL_BOXES,"Runtime block total box limit");
            var shapes=new ArrayList<Box>(boxes.size());
            Set<String> textures=new HashSet<>();
            for(JsonElement box:boxes){
                AssetSchema.require(box.isJsonObject(),"Runtime block box object");
                JsonObject shape=box.getAsJsonObject();
                float[] from=vector(shape,"from"),to=vector(shape,"to");
                for(int axis=0;axis<3;axis++)AssetSchema.require(from[axis]<to[axis],"Runtime block inverted box");
                String texture=string(shape,"texture");AssetSchema.id(texture);
                textures.add(texture);AssetSchema.require(textures.size()<=8,"Runtime block texture limit");
                String color=shape.has("color")?string(shape,"color"):"#ffffff";
                AssetSchema.require(color.matches("#[0-9a-fA-F]{6}"),"Runtime block color");
                int rgb=Integer.parseInt(color.substring(1),16);
                shapes.add(new Box(from[0]/16,from[1]/16,from[2]/16,to[0]/16,to[1]/16,to[2]/16,
                    texture,(rgb>>16)&255,(rgb>>8)&255,rgb&255));
            }
            AssetSchema.require(result.putIfAbsent(id,new Variant(id,List.copyOf(shapes)))==null,"Duplicate runtime block variant");
        }
        return Map.copyOf(result);
    }
    private static JsonArray array(JsonObject source,String key){
        AssetSchema.require(source.has(key)&&source.get(key).isJsonArray(),"Missing runtime block array: "+key);
        return source.getAsJsonArray(key);
    }
    private static String string(JsonObject source,String key){
        AssetSchema.require(source.has(key)&&source.get(key).isJsonPrimitive()&&source.getAsJsonPrimitive(key).isString(),
            "Missing runtime block string: "+key);
        return source.get(key).getAsString();
    }
    private static float[] vector(JsonObject source,String key){
        JsonArray data=array(source,key);
        AssetSchema.require(data.size()==3,"Runtime block vector length");
        float[] result=new float[3];
        for(int i=0;i<3;i++){
            JsonElement element=data.get(i);
            AssetSchema.require(element.isJsonPrimitive()&&element.getAsJsonPrimitive().isNumber(),"Runtime block vector number");
            double coordinate=element.getAsDouble();
            AssetSchema.require(Double.isFinite(coordinate)&&coordinate>=0&&coordinate<=16,"Runtime block coordinate bounds");
            result[i]=(float)coordinate;
        }
        return result;
    }
}
