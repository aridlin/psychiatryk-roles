package pl.aridlin.psychiatrykroles.runtime;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Typed, bounded state carried by the existing optional runtime Snapshot envelope. */
public final class SignalSchema {
    private SignalSchema() {}
    public static final int VERSION=1, MAX_SIGNALS=16, MAX_CHANGES=16, MAX_MESSAGE=2048;
    public static final int MAX_HELLO=256, MAX_TEXT=48;
    public record Value(String kind,double number,boolean booleanValue,String text) {}
    /** A null value removes a key. */
    public record Change(String id,Value value) {}
    public record Message(String op,List<Change> changes) {}

    public static void id(String id){Schema.require(id!=null&&id.matches("[a-z0-9_.-]{1,32}"),"Signal ID");}
    public static String variable(String name){
        Schema.require(name!=null&&name.startsWith("signal."),"Signal variable prefix");
        String id=name.substring(7);id(id);return id;
    }
    private static void text(String value){
        Schema.require(value!=null&&value.length()<=MAX_TEXT,"Signal text limit");
        for(int i=0;i<value.length();i++)Schema.require(!Character.isISOControl(value.charAt(i)),"Signal text control");
    }
    private static void validate(Change change){
        Schema.require(change!=null,"Null signal change");id(change.id());
        Value value=change.value();if(value==null)return;
        Schema.require(value.kind()!=null,"Signal type missing");
        switch(value.kind()){
            case "number" -> Schema.require(Double.isFinite(value.number())&&Math.abs(value.number())<=1_000_000.0
                &&!value.booleanValue()&&value.text()==null,"Signal number limit");
            case "boolean" -> Schema.require(value.number()==0&&value.text()==null,"Signal boolean fields");
            case "text" -> {Schema.require(value.number()==0&&!value.booleanValue(),"Signal text fields");text(value.text());}
            default -> throw new IllegalArgumentException("Unknown signal type");
        }
    }
    public static Change valueNumber(String id,double value){var change=new Change(id,new Value("number",value,false,null));validate(change);return change;}
    public static Change valueBoolean(String id,boolean value){var change=new Change(id,new Value("boolean",0,value,null));validate(change);return change;}
    public static Change valueText(String id,String value){var change=new Change(id,new Value("text",0,false,value));validate(change);return change;}
    public static Change clear(String id){var change=new Change(id,null);validate(change);return change;}
    public static Message delta(List<Change> changes){
        Schema.require(changes!=null&&!changes.isEmpty()&&changes.size()<=MAX_CHANGES,"Signal change count");
        var seen=new HashSet<String>();for(var change:changes){validate(change);Schema.require(seen.add(change.id()),"Duplicate signal ID");}
        return new Message("delta",List.copyOf(changes));
    }
    public static Message reset(){return new Message("reset",List.of());}
    public static String encode(Message message){
        Schema.require(message!=null,"Missing signal message");
        JsonObject json=new JsonObject();json.addProperty("schema",VERSION);json.addProperty("channel","signal");json.addProperty("op",message.op());
        if("delta".equals(message.op())){
            delta(message.changes());JsonArray changes=new JsonArray();
            for(Change change:message.changes()){
                JsonObject item=new JsonObject();item.addProperty("id",change.id());Value value=change.value();
                if(value==null)item.addProperty("kind","clear");
                else {item.addProperty("kind",value.kind());switch(value.kind()){
                    case "number" -> item.addProperty("value",value.number());
                    case "boolean" -> item.addProperty("value",value.booleanValue());
                    case "text" -> item.addProperty("value",value.text());
                    default -> throw new IllegalArgumentException("Unknown signal type");
                }}changes.add(item);
            }json.add("changes",changes);
        }else Schema.require("reset".equals(message.op())&&message.changes()!=null&&message.changes().isEmpty(),"Signal operation");
        String raw=json.toString();limit(raw,MAX_MESSAGE);return raw;
    }
    private static void limit(String raw,int max){Schema.require(raw!=null&&raw.length()<=max&&raw.getBytes(StandardCharsets.UTF_8).length<=max,"Signal wire limit");}
    private static void keys(JsonObject object,Set<String> expected){Schema.require(object.keySet().equals(expected),"Signal fields");}
    private static String string(JsonElement value){Schema.require(value!=null&&value.isJsonPrimitive()&&value.getAsJsonPrimitive().isString(),"Signal string");return value.getAsString();}
    private static int integer(JsonElement value){
        Schema.require(value!=null&&value.isJsonPrimitive()&&value.getAsJsonPrimitive().isNumber(),"Signal integer");
        double number=value.getAsDouble();Schema.require(Double.isFinite(number)&&number==Math.rint(number)
            &&number>=Integer.MIN_VALUE&&number<=Integer.MAX_VALUE,"Signal integer");return (int)number;
    }
    private static void header(JsonObject object,String op){
        Schema.require(integer(object.get("schema"))==VERSION&&"signal".equals(string(object.get("channel")))&&op.equals(string(object.get("op"))),"Signal header");
    }
    public static boolean isSignal(String raw){
        if(raw==null||raw.length()>Schema.MAX_DOCUMENT)return false;
        try{JsonElement channel=Schema.object(raw,Schema.MAX_DOCUMENT).get("channel");return channel!=null&&channel.isJsonPrimitive()&&"signal".equals(channel.getAsString());}
        catch(RuntimeException malformed){return false;}
    }
    public static Message parse(String raw){
        limit(raw,MAX_MESSAGE);JsonObject object=Schema.object(raw,MAX_MESSAGE);String op=string(object.get("op"));header(object,op);
        if("reset".equals(op)){keys(object,Set.of("schema","channel","op"));return reset();}
        Schema.require("delta".equals(op),"Signal operation");keys(object,Set.of("schema","channel","op","changes"));
        JsonElement changes=object.get("changes");Schema.require(changes!=null&&changes.isJsonArray(),"Signal changes");
        JsonArray array=changes.getAsJsonArray();Schema.require(array.size()>0&&array.size()<=MAX_CHANGES,"Signal change count");
        var parsed=new java.util.ArrayList<Change>();
        for(JsonElement element:array){
            Schema.require(element.isJsonObject(),"Signal change object");JsonObject item=element.getAsJsonObject();
            String id=string(item.get("id")),kind=string(item.get("kind"));Change change;
            if("clear".equals(kind)){keys(item,Set.of("id","kind"));change=clear(id);}
            else {
                keys(item,Set.of("id","kind","value"));JsonElement value=item.get("value");
                Schema.require(value!=null&&value.isJsonPrimitive(),"Signal value");
                change=switch(kind){
                    case "number" -> {Schema.require(value.getAsJsonPrimitive().isNumber(),"Signal number type");yield valueNumber(id,value.getAsDouble());}
                    case "boolean" -> {Schema.require(value.getAsJsonPrimitive().isBoolean(),"Signal boolean type");yield valueBoolean(id,value.getAsBoolean());}
                    case "text" -> valueText(id,string(value));
                    default -> throw new IllegalArgumentException("Unknown signal type");
                };
            }parsed.add(change);
        }
        return delta(parsed);
    }
    public static String hello(){return "{\"schema\":1,\"channel\":\"signal\",\"op\":\"hello\",\"capability\":\"typed_state_v1\"}";}
    public static void parseHello(String raw){
        limit(raw,MAX_HELLO);JsonObject object=Schema.object(raw,MAX_HELLO);
        keys(object,Set.of("schema","channel","op","capability"));header(object,"hello");
        Schema.require("typed_state_v1".equals(string(object.get("capability"))),"Signal capability");
    }
}
