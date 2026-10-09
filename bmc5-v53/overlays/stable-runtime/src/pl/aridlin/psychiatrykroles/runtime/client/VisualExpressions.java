package pl.aridlin.psychiatrykroles.runtime.client;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.util.Map;

/** A bounded data expression, compiled on asset or scene update and evaluated without parsing per frame. */
public final class VisualExpressions {
    private VisualExpressions() {}
    public static final Map<String,Integer> VARIABLES=Map.ofEntries(
        Map.entry("time",0),Map.entry("speed",1),Map.entry("yaw",2),Map.entry("pitch",3),
        Map.entry("steering",4),Map.entry("lean",5),Map.entry("wheel",6),Map.entry("suspension",7),
        Map.entry("step",8),Map.entry("throttle",9),Map.entry("battery",10),Map.entry("heat",11),
        Map.entry("airborne",12),Map.entry("vertical_speed",13),Map.entry("landing_age",14),
        Map.entry("rocket",15),Map.entry("partial",16),
        Map.entry("health",17),Map.entry("max_health",18),Map.entry("food",19),
        Map.entry("gui_width",20),Map.entry("gui_height",21),Map.entry("scene_time",22));
    public static final int VARIABLE_COUNT=23;
    public interface Expression {double evaluate(double[] variables);}
    public static final class Budget {private int count;public int count(){return count;}}
    private static double finite(double value){return Double.isFinite(value)?Math.clamp(value,-1_000_000.0,1_000_000.0):0.0;}
    public static Expression compile(JsonElement element,Budget budget){return compile(element,budget,VARIABLES);}
    /** Each feature supplies its own variable whitelist; no expression can inspect other client state. */
    public static Expression compile(JsonElement element,Budget budget,Map<String,Integer> variables){
        return compile(element,budget,variables,0);
    }
    private static Expression compile(JsonElement element,Budget budget,Map<String,Integer> variables,int depth){
        if(element==null||element.isJsonNull()||depth>12||++budget.count>256)throw new IllegalArgumentException("Visual expression budget");
        if(element.isJsonPrimitive()&&element.getAsJsonPrimitive().isNumber()){
            double value=element.getAsDouble();if(!Double.isFinite(value)||Math.abs(value)>1_000_000.0)throw new IllegalArgumentException("Visual expression constant");
            return ignored->value;
        }
        if(!element.isJsonObject())throw new IllegalArgumentException("Visual expression object");
        JsonObject object=element.getAsJsonObject();
        if(object.has("var")){
            if(object.size()!=1)throw new IllegalArgumentException("Visual variable fields");
            Integer index=variables.get(object.get("var").getAsString());
            if(index==null)throw new IllegalArgumentException("Unknown visual variable");
            return values->index<values.length?finite(values[index]):0;
        }
        if(!object.has("op")||!object.has("args")||object.size()!=2)throw new IllegalArgumentException("Visual operation fields");
        String operation=object.get("op").getAsString();JsonArray json=object.getAsJsonArray("args");
        if(json.size()>4)throw new IllegalArgumentException("Visual operation arity");
        Expression[] args=new Expression[json.size()];for(int i=0;i<args.length;i++)args[i]=compile(json.get(i),budget,variables,depth+1);
        int min,max;switch(operation){
            case "neg","abs","sin","cos","floor","not" -> {min=1;max=1;}
            case "clamp","if" -> {min=3;max=3;}
            case "add","sub","mul","div","min","max","lt","le","gt","ge","eq","and","or","mod","pow" -> {min=2;max=2;}
            default -> throw new IllegalArgumentException("Unknown visual operation");
        }
        if(args.length<min||args.length>max)throw new IllegalArgumentException("Visual operation arity");
        return values->{
            double a=finite(args[0].evaluate(values));
            if(operation.equals("if"))return finite((a!=0?args[1]:args[2]).evaluate(values));
            if(operation.equals("and")&&a==0||operation.equals("or")&&a!=0)return operation.equals("or")?1:0;
            double b=args.length>1?finite(args[1].evaluate(values)):0;
            double c=operation.equals("clamp")?finite(args[2].evaluate(values)):0;
            return finite(switch(operation){
                case "neg" -> -a;case "abs" -> Math.abs(a);case "sin" -> Math.sin(a);case "cos" -> Math.cos(a);
                case "floor" -> Math.floor(a);case "not" -> a==0?1:0;
                case "add" -> a+b;case "sub" -> a-b;case "mul" -> a*b;case "div" -> b==0?0:a/b;
                case "mod" -> b==0?0:a%b;case "pow" -> Math.pow(a,Math.clamp(b,-8.0,8.0));
                case "min" -> Math.min(a,b);case "max" -> Math.max(a,b);
                case "lt" -> a<b?1:0;case "le" -> a<=b?1:0;case "gt" -> a>b?1:0;case "ge" -> a>=b?1:0;
                case "eq" -> a==b?1:0;case "and" -> b!=0?1:0;case "or" -> b!=0?1:0;
                case "clamp" -> Math.clamp(a,Math.min(b,c),Math.max(b,c));
                default -> 0;
            });
        };
    }
}
