import java.io.IOException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import pl.aridlin.psychiatrykroles.io.OrderedIoOwner;

public final class OwnerFailureQA {
    private static void check(boolean good, String reason) { if (!good) throw new AssertionError(reason); }
    public static void main(String[] args) throws Exception {
        OrderedIoOwner owner = new OrderedIoOwner("failure-qa");
        CountDownLatch started = new CountDownLatch(1), release = new CountDownLatch(1);
        AtomicInteger writes = new AtomicInteger(), allocation = new AtomicInteger(), following = new AtomicInteger();
        AtomicReference<Throwable> callFailure = new AtomicReference<>();
        owner.submit(() -> {
            int attempt = writes.incrementAndGet();
            if (attempt == 1) { started.countDown(); release.await(); throw new IOException("injected async failure"); }
            check(following.get() == 0, "later save overtook failed head");
            return null;
        });
        started.await();
        Thread call = new Thread(() -> {
            try { owner.call(() -> { allocation.incrementAndGet(); return 42; }); }
            catch (Throwable error) { callFailure.set(error); }
        });
        call.start();
        long deadline = System.nanoTime() + 2_000_000_000L;
        while (owner.pendingJobs() < 2 && System.nanoTime() < deadline) Thread.sleep(1);
        check(owner.pendingJobs() == 2, "allocation wasn't queued behind slow write");
        owner.submit(() -> { following.incrementAndGet(); return null; });
        release.countDown();
        call.join(2000);
        check(!call.isAlive() && callFailure.get() instanceof IOException, "waiting call didn't surface prefix failure");
        check(allocation.get() == 0, "cancelled allocation executed before retry");
        int pending = owner.pendingJobs();
        try { owner.call(() -> { allocation.incrementAndGet(); return 23; }); throw new AssertionError("failed prefix accepted call"); }
        catch (IOException expected) {}
        check(owner.pendingJobs() == pending, "rejected call remained secretly queued");
        Thread.sleep(2400);
        owner.barrier();
        check(writes.get() == 2 && following.get() == 1, "asynchronous retained jobs didn't recover in order");
        check(allocation.get() == 0, "cancelled/rejected allocation executed after recovery");
        AtomicInteger ownFailure = new AtomicInteger();
        try {
            owner.call(() -> { ownFailure.incrementAndGet(); throw new IOException("injected synchronous failure"); });
            throw new AssertionError("own synchronous failure not surfaced");
        } catch (IOException expected) {}
        Thread.sleep(2400);
        owner.barrier();
        check(ownFailure.get() == 1, "synchronous operation retried after caller left");
        check(owner.pendingJobs() == 0, "jobs remained after successful barrier");
        owner.shutdown();
        System.out.println("PASS: prefix-call rejection, queued allocation cancellation, async FIFO retry, no secret sync retry, clean shutdown");
    }
}
