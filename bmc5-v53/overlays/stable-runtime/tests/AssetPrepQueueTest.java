package pl.aridlin.psychiatrykroles.runtime;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;

/** A maximal manifest completes in sixteen ticks and starts its request once. */
public final class AssetPrepQueueTest {
    private static int checks;
    private static void check(boolean condition,String reason){checks++;if(!condition)throw new AssertionError(reason);}
    public static void main(String[] ignored){
        var entries=new ArrayList<String>();for(int i=0;i<AssetSchema.MAX_ASSETS;i++)entries.add("asset-"+i);
        var pass=new AssetPrepQueue<>(entries);var observed=new ArrayList<String>();
        for(int tick=0;tick<15;tick++){
            var batch=pass.nextBatch();check(batch.size()==2,"at most two cache checks per tick");
            observed.addAll(batch);check(!pass.finish(),"request waits for all cache checks");
        }
        observed.addAll(pass.nextBatch());check(pass.finish(),"request starts after final batch");
        check(!pass.finish()&&pass.nextBatch().isEmpty(),"no repeated request or cache check");
        check(observed.equals(entries)&&new HashSet<>(observed).size()==AssetSchema.MAX_ASSETS,"every asset checked exactly once");
        var retried=new AssetPrepQueue<>(List.of("asset-0","asset-1"));
        check(retried.nextBatch().equals(List.of("asset-0","asset-1"))&&retried.finish(),
            "a replacement manifest has independent preparation state");
        System.out.println("ASSET_PREP_QUEUE_PASS checks="+checks);
    }
}
