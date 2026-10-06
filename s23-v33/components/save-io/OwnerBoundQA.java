import java.io.IOException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import pl.aridlin.psychiatrykroles.io.OrderedIoOwner;
public final class OwnerBoundQA {
 public static void main(String[] ignored) throws Exception {
  OrderedIoOwner owner = new OrderedIoOwner("bound-qa");
  CountDownLatch started = new CountDownLatch(1), release = new CountDownLatch(1);
  AtomicBoolean broken = new AtomicBoolean(true);
  AtomicInteger accepted = new AtomicInteger(), rejectedRan = new AtomicInteger();
  owner.submit(() -> { started.countDown(); release.await(); if (broken.get()) throw new IOException("persistent fault"); return null; });
  started.await();
  for (int i=0; i<1023; i++) owner.submit(() -> { accepted.incrementAndGet(); return null; });
  if (owner.pendingJobs()!=1024) throw new AssertionError("unexpected bound");
  release.countDown();
  long end=System.nanoTime()+2_000_000_000L;
  while(!owner.hasFailure()&&System.nanoTime()<end) Thread.sleep(1);
  if(!owner.hasFailure()) throw new AssertionError("failure not surfaced");
  for (int i=0; i<100; i++) {
   try { owner.submit(() -> { rejectedRan.incrementAndGet(); return null; }); throw new AssertionError("accepted beyond bound"); }
   catch(IOException expected) {}
  }
  if(owner.pendingJobs()!=1024) throw new AssertionError("faulted queue grew");
  broken.set(false);
  Thread.sleep(2400);
  owner.barrier();
  if(accepted.get()!=1023||rejectedRan.get()!=0) throw new AssertionError("rejected jobs executed or retained jobs lost");
  owner.shutdown();
  System.out.println("PASS: faulted queue bounded before enqueue, rejected jobs never execute, 1023 accepted saves recover in order");
 }
}
