package pl.aridlin.psychiatrykroles.runtime.client;

import com.google.gson.JsonParser;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;
import net.minecraft.core.component.DataComponents;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import pl.aridlin.psychiatrykroles.runtime.AssetSchema;
import pl.aridlin.psychiatrykroles.runtime.Schema;

/** Client display-only mapping. Server actions still validate their own signed variant tags. */
public final class RuntimeItemVisuals {
    private RuntimeItemVisuals() {}
    public static final String DOCUMENT="items/runtime";
    public static final int MAX_VARIANTS=128;
    private static volatile Map<String,ResourceLocation> textures=Map.of();
    private static volatile String revision="";

    /** Runs from AssetClient's ready hook, after all PNGs are verified and registered. */
    public static void install(){
        String raw=AssetClient.json(DOCUMENT).orElse(null);
        Map<String,ResourceLocation> next=raw==null?Map.of():parse(raw,
            id->AssetClient.texture(id).orElseThrow(()->new IllegalArgumentException("Missing item texture: "+id)));
        textures=next;
        revision=AssetClient.revision();
    }

    /** Pure parser for native regression tests; never invoked in the item render loop. */
    public static Map<String,ResourceLocation> parse(String raw,Function<String,ResourceLocation> resolve){
        AssetSchema.require(raw!=null&&raw.length()<=AssetSchema.MAX_JSON_FILE,"Item visual document size");
        var root=JsonParser.parseString(raw).getAsJsonObject();
        AssetSchema.require(root.size()==2&&root.has("schema")&&root.get("schema").getAsInt()==1
            &&root.has("items")&&root.get("items").isJsonArray(),"Item visual schema");
        var entries=root.getAsJsonArray("items");
        AssetSchema.require(entries.size()<=MAX_VARIANTS,"Item visual count");
        var result=new HashMap<String,ResourceLocation>();
        for(var element:entries){
            AssetSchema.require(element.isJsonObject(),"Item visual entry");
            var entry=element.getAsJsonObject();
            AssetSchema.require(entry.size()==2&&entry.has("id")&&entry.has("asset")
                &&entry.get("id").isJsonPrimitive()&&entry.get("asset").isJsonPrimitive()
                &&entry.get("id").getAsJsonPrimitive().isString()
                &&entry.get("asset").getAsJsonPrimitive().isString(),"Item visual fields");
            String id=entry.get("id").getAsString(),asset=entry.get("asset").getAsString();
            Schema.id(id);AssetSchema.id(asset);
            ResourceLocation texture=resolve.apply(asset);
            AssetSchema.require(texture!=null&&result.putIfAbsent(id,texture)==null,
                "Missing or duplicate item visual");
        }
        return Map.copyOf(result);
    }

    public static ResourceLocation texture(ItemStack stack){
        if(!AssetClient.ready()||!revision.equals(AssetClient.revision()))return null;
        var data=stack.get(DataComponents.CUSTOM_DATA);
        if(data==null)return null;
        // getUnsafe is read only here. copyTag() would clone NBT on every item render.
        String id=data.getUnsafe().getString("psychiatrykRuntimeVariant");
        return id.length()<=64?textures.get(id):null;
    }
}
