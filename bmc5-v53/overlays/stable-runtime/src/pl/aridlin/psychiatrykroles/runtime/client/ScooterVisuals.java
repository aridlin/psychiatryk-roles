package pl.aridlin.psychiatrykroles.runtime.client;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.mojang.blaze3d.vertex.PoseStack;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.joml.Quaternionf;

/** Compiled, bounded model transforms. Existing G2Mesh stays cached on the GPU. */
public final class ScooterVisuals {
    private ScooterVisuals() {}
    private static final List<String> STAGES=List.of("pre","root","deck","steering","front_wheel","rear_wheel","trim");
    private static final ThreadLocal<Quaternionf> ROTATION=ThreadLocal.withInitial(Quaternionf::new);
    private record Snapshot(String revision,Map<String,List<Transform>> stages) {}
    private record Transform(String op,String axis,VisualExpressions.Expression x,VisualExpressions.Expression y,VisualExpressions.Expression z) {
        void apply(PoseStack pose,double[] vars){
            double ax=x==null?0:x.evaluate(vars),ay=y==null?0:y.evaluate(vars),az=z==null?0:z.evaluate(vars);
            switch(op){
                case "translate" -> pose.translate(Math.clamp(ax,-2,2),Math.clamp(ay,-2,2),Math.clamp(az,-2,2));
                case "scale" -> pose.scale((float)Math.clamp(ax,0.05,3),(float)Math.clamp(ay,0.05,3),(float)Math.clamp(az,0.05,3));
                case "rotate" -> {float radians=(float)Math.toRadians(Math.clamp(ax,-180,180));
                    float x=axis.equals("x")?1:0,y=axis.equals("y")?1:0,z=axis.equals("z")?1:0;
                    pose.mulPose(ROTATION.get().rotationAxis(radians,x,y,z));}
            }
        }
    }
    private static volatile Snapshot active=new Snapshot("",Map.of());
    public static void install(String raw,String revision){active=raw==null?new Snapshot(revision,Map.of()):parse(raw,revision);}
    private static Snapshot parse(String raw,String revision){
        var doc=JsonParser.parseString(raw).getAsJsonObject();
        if(!doc.has("schema")||doc.get("schema").getAsInt()!=1||doc.size()>2||!doc.has("transforms"))
            throw new IllegalArgumentException("Scooter visual schema");
        var source=doc.getAsJsonObject("transforms");
        if(source.size()>STAGES.size())throw new IllegalArgumentException("Scooter visual stage count");
        var result=new java.util.HashMap<String,List<Transform>>();int total=0;
        var budget=new VisualExpressions.Budget();
        for(var stage:source.entrySet()){
            if(!STAGES.contains(stage.getKey()))throw new IllegalArgumentException("Scooter visual stage");
            var json=stage.getValue().getAsJsonArray();
            if(json.size()>16||(total+=json.size())>48)throw new IllegalArgumentException("Scooter visual transform count");
            var steps=new ArrayList<Transform>();
            for(JsonElement element:json){var obj=element.getAsJsonObject();
                String op=obj.get("op").getAsString();
                if(op.equals("rotate")){
                    if(obj.size()!=3||!obj.has("degrees"))throw new IllegalArgumentException("Scooter rotation fields");
                    String axis=obj.get("axis").getAsString();if(!List.of("x","y","z").contains(axis))throw new IllegalArgumentException("Scooter rotation axis");
                    steps.add(new Transform(op,axis,VisualExpressions.compile(obj.get("degrees"),budget),null,null));
                }else if(op.equals("scale")||op.equals("translate")){
                    if(obj.size()!=4)throw new IllegalArgumentException("Scooter transform fields");
                    steps.add(new Transform(op,"",VisualExpressions.compile(obj.get("x"),budget),
                        VisualExpressions.compile(obj.get("y"),budget),VisualExpressions.compile(obj.get("z"),budget)));
                }else throw new IllegalArgumentException("Scooter transform operation");
            }
            result.put(stage.getKey(),List.copyOf(steps));
        }
        return new Snapshot(revision,Map.copyOf(result));
    }
    public static void apply(String stage,PoseStack pose,double[] variables){
        Snapshot current=active;
        if(!AssetClient.ready()||!current.revision().equals(AssetClient.revision()))return;
        var transforms=current.stages().get(stage);if(transforms==null)return;
        for(var transform:transforms)transform.apply(pose,variables);
    }
}
