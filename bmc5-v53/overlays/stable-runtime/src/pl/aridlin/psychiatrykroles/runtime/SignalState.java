package pl.aridlin.psychiatrykroles.runtime;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Objects;

/** One player's desired state and last delivered state; used only on the server thread. */
public final class SignalState {
    private final Map<String,SignalSchema.Value> desired=new LinkedHashMap<>();
    private final Map<String,SignalSchema.Value> sent=new LinkedHashMap<>();
    private boolean resetPending;
    public void set(SignalSchema.Change change){
        // This also validates externally constructed Change records.
        SignalSchema.delta(java.util.List.of(change));
        if(change.value()==null){desired.remove(change.id());return;}
        Schema.require(desired.containsKey(change.id())||desired.size()<SignalSchema.MAX_SIGNALS,"Signal state count");
        desired.put(change.id(),change.value());
    }
    public int size(){return desired.size();}
    public void clearAll(){desired.clear();}
    public void resetDelivery(){sent.clear();resetPending=true;}
    /** Returns at most one bounded packet; a caller may invoke it at most every four server ticks. */
    public SignalSchema.Message nextMessage(){
        if(resetPending){resetPending=false;return SignalSchema.reset();}
        var keys=new LinkedHashSet<String>();keys.addAll(sent.keySet());keys.addAll(desired.keySet());
        var changes=new ArrayList<SignalSchema.Change>();
        for(String id:keys){
            SignalSchema.Value current=desired.get(id);
            if(Objects.equals(current,sent.get(id)))continue;
            var change=new SignalSchema.Change(id,current);var candidate=new ArrayList<>(changes);candidate.add(change);
            if(candidate.size()>SignalSchema.MAX_CHANGES)break;
            try{SignalSchema.encode(SignalSchema.delta(candidate));}
            catch(IllegalArgumentException full){if(changes.isEmpty())throw full;break;}
            changes.add(change);
        }
        if(changes.isEmpty())return null;
        for(var change:changes){if(change.value()==null)sent.remove(change.id());else sent.put(change.id(),change.value());}
        return SignalSchema.delta(changes);
    }
}
