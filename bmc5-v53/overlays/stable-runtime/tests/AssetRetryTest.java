package pl.aridlin.psychiatrykroles.runtime;

/** An equal-revision reload retries only absent, expired, or stale transfer state. */
public final class AssetRetryTest {
    private static int checks;
    private static void check(boolean condition,String reason){checks++;if(!condition)throw new AssertionError(reason);}
    public static void main(String[] args){
        String revision="a".repeat(64),other="b".repeat(64);
        check(AssetServer.needsManifest(null,revision,100),"missing transfer retries");
        var pending=new AssetServer.Pending(revision,100);
        check(!AssetServer.needsManifest(pending,revision,pending.expires-1),"in-flight transfer preserved");
        check(!AssetServer.needsManifest(pending,revision,pending.expires),"deadline inclusive for existing transfer");
        check(AssetServer.needsManifest(pending,revision,pending.expires+1),"expired transfer retries");
        pending.ready=true;
        check(!AssetServer.needsManifest(pending,revision,pending.expires+1),"ready player never reset on same revision");
        check(AssetServer.needsManifest(pending,other,pending.expires+1),"stale revision retries even if marked ready");
        pending.ready=false;
        check(AssetServer.nextTimeoutRetry(pending,pending.expires)==-1,"no resend at inclusive deadline");
        check(AssetServer.nextTimeoutRetry(pending,pending.expires+1)==1,"first timed out transfer resends");
        var second=new AssetServer.Pending(revision,pending.expires+1,1);
        check(AssetServer.nextTimeoutRetry(second,second.expires+1)==2,"second timed out transfer resends");
        var finalAttempt=new AssetServer.Pending(revision,second.expires+1,2);
        check(AssetServer.nextTimeoutRetry(finalAttempt,finalAttempt.expires+1)==-1,"third failed attempt stops automatically");
        finalAttempt.ready=true;
        check(AssetServer.nextTimeoutRetry(finalAttempt,finalAttempt.expires+1)==-1,"ready transfer never resends");
        check(AssetSchema.TIMEOUT_NANOS>AssetSchema.SERVER_TIMEOUT_NANOS,
            "client keeps transfer active until the server retry manifest can arrive");
        System.out.println("ASSET_RETRY_PASS checks="+checks);
    }
}
