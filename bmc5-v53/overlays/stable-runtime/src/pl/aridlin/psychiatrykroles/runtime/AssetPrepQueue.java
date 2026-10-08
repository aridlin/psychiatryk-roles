package pl.aridlin.psychiatrykroles.runtime;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;

/** One bounded cache-preparation pass per manifest; a new manifest gets a new queue. */
public final class AssetPrepQueue<T> {
    public static final int PER_TICK=2;
    private final ArrayDeque<T> pending;
    private boolean finished;

    public AssetPrepQueue(List<T> entries){pending=new ArrayDeque<>(List.copyOf(entries));}

    public List<T> nextBatch(){
        var batch=new ArrayList<T>(PER_TICK);
        for(int i=0;i<PER_TICK&&!pending.isEmpty();i++)batch.add(pending.removeFirst());
        return batch;
    }

    /** True exactly once, after every cache entry has been checked. */
    public boolean finish(){
        if(finished||!pending.isEmpty())return false;
        finished=true;return true;
    }
}
