package pl.aridlin.psychiatrykroles.runtime;

import java.util.HashMap;
import java.util.Map;

/** Bounded received state; a rejected delta cannot partially alter live values. */
public final class SignalValues {
    private Map<String,SignalSchema.Value> values=Map.of();
    public int size(){return values.size();}
    public void clear(){values=Map.of();}
    public void apply(SignalSchema.Message message){
        SignalSchema.encode(message);
        if("reset".equals(message.op())){values=Map.of();return;}
        var staged=new HashMap<>(values);
        for(var change:message.changes()){
            if(change.value()==null)staged.remove(change.id());else staged.put(change.id(),change.value());
        }
        Schema.require(staged.size()<=SignalSchema.MAX_SIGNALS,"Signal state count");
        values=Map.copyOf(staged);
    }
    /** Numeric HUD expressions read numbers and booleans; text is retained as typed state and evaluates to zero. */
    public double numeric(String id){
        var value=values.get(id);if(value==null)return 0;
        return switch(value.kind()){
            case "number" -> value.number();
            case "boolean" -> value.booleanValue()?1:0;
            default -> 0;
        };
    }
    public SignalSchema.Value get(String id){return values.get(id);}
}
