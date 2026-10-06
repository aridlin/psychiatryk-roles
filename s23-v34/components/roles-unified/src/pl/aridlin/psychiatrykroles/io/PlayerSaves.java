package pl.aridlin.psychiatrykroles.io;
import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import net.minecraft.nbt.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.storage.PlayerDataStorage;
/** Immutable player snapshots; file replacement retains vanilla's .dat_old backup. */
public final class PlayerSaves {
 private static final org.slf4j.Logger LOG=org.slf4j.LoggerFactory.getLogger("GoplanskaPlayerSaves");
 private static final Map<PlayerDataStorage,Owner> OWNERS=new IdentityHashMap<>();
 private record Snapshot(Path directory,String uuid,String name,CompoundTag tag){}
 private static synchronized Owner owner(PlayerDataStorage storage){return OWNERS.computeIfAbsent(storage,s->new Owner());}
 public static void save(PlayerDataStorage storage,File directory,Player player){
  // No live entity, level, registry or inventory is touched by the worker.
  var snapshot=new Snapshot(directory.toPath(),player.getStringUUID(),player.getName().getString(),player.saveWithoutId(new CompoundTag()).copy());
  owner(storage).submit(snapshot);
  // Keep Forge events on the caller/tick thread. Current pack has no SaveToFile handlers.
  // Listeners that require completed files can call await(storage) explicitly.
  net.neoforged.neoforge.event.EventHooks.firePlayerSavingEvent(player,directory,player.getStringUUID());
 }
 public static void await(PlayerDataStorage storage)throws IOException{Owner owner; synchronized(PlayerSaves.class){owner=OWNERS.get(storage);}if(owner!=null)owner.drain();}
 public static void flushAll()throws IOException{List<Owner> owners;synchronized(PlayerSaves.class){owners=List.copyOf(OWNERS.values());}IOException failure=null;for(var owner:owners)try{owner.drain();}catch(IOException e){if(failure==null)failure=e;else failure.addSuppressed(e);}if(failure!=null)throw failure;}
 public static int pendingJobs(){synchronized(PlayerSaves.class){return OWNERS.values().stream().mapToInt(o->o.pending.get()).sum();}}
 public static int failedOwners(){synchronized(PlayerSaves.class){return (int)OWNERS.values().stream().filter(o->o.failure!=null).count();}}
 public static void closeAll()throws IOException{flushAll();synchronized(PlayerSaves.class){OWNERS.values().forEach(o->o.executor.shutdown());OWNERS.clear();}}
 private static final class Owner {
  final java.util.concurrent.atomic.AtomicInteger pending=new java.util.concurrent.atomic.AtomicInteger();
  final ScheduledExecutorService executor=Executors.newSingleThreadScheduledExecutor(r->{var t=new Thread(r,"Goplanska-Player-Save-IO");t.setDaemon(true);return t;});
  final ArrayDeque<Snapshot> failed=new ArrayDeque<>();
  volatile Exception failure;
  boolean retryScheduled;
  long lastFailureLog;
  void submit(Snapshot snapshot){
   // Apply backpressure before accepting another snapshot; a failed drain must not grow the queue.
   if(pending.get()>=128)try{drain();}catch(IOException e){throw new UncheckedIOException(e);}
   pending.incrementAndGet();
   try{executor.execute(()->{failed.addLast(snapshot);process();});}catch(RejectedExecutionException e){pending.decrementAndGet();throw e;}
  }
  void process(){while(!failed.isEmpty()){
   var next=failed.peekFirst();
   try{write(next);failed.removeFirst();pending.decrementAndGet();if(failure!=null){LOG.info("Player save IO recovered; retained snapshots are being drained");failure=null;}}
   catch(Exception e){failure=e;long now=System.nanoTime();if(lastFailureLog==0||now-lastFailureLog>TimeUnit.SECONDS.toNanos(60)){lastFailureLog=now;LOG.error("Player save queued for retry; previous data preserved for {}",next.name(),e);}
    if(!retryScheduled){retryScheduled=true;executor.schedule(()->{retryScheduled=false;process();},2,TimeUnit.SECONDS);}return;
   }
  }}
  void drain()throws IOException{try{executor.submit(()->{process();if(!failed.isEmpty())throw new IOException("Unable to complete player save for "+failed.peekFirst().name()+"; "+failed.size()+" immutable snapshots retained for retry");return null;}).get();}catch(InterruptedException e){Thread.currentThread().interrupt();throw new IOException("Interrupted while waiting for player saves",e);}catch(ExecutionException e){throw new IOException("Player save flush failed",e.getCause());}}
  void write(Snapshot snapshot)throws IOException{
   Path temporary=Files.createTempFile(snapshot.directory(),snapshot.uuid()+"-",".dat");
   boolean completed=false;
   try{NbtIo.writeCompressed(snapshot.tag(),temporary);Path target=snapshot.directory().resolve(snapshot.uuid()+".dat"),old=snapshot.directory().resolve(snapshot.uuid()+".dat_old");net.minecraft.Util.safeReplaceFile(target,temporary,old);
    // safeReplaceFile logs failure rather than throwing. Confirm replacement succeeded.
    if(Files.exists(temporary)||!Files.isRegularFile(target))throw new IOException("Player data replacement did not complete: "+snapshot.uuid());completed=true;
   }finally{if(!completed)Files.deleteIfExists(temporary);}
  }
 }
}
