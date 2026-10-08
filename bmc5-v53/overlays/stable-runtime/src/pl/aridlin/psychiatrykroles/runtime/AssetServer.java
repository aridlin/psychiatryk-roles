package pl.aridlin.psychiatrykroles.runtime;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import javax.imageio.ImageIO;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/** Loads bounded local PNG assets and transfers only hashes missing from a client's cache. */
public final class AssetServer {
    public static final Path DIRECTORY=Path.of("config/psychiatryk-runtime-assets");
    private static final int MAX_DIRECTORY_ENTRIES=256;
    private record Blob(AssetSchema.Entry entry,byte[] bytes) {}
    private record Snapshot(AssetSchema.Manifest manifest,Map<String,Blob> blobs) {}
    private static volatile Snapshot current=new Snapshot(AssetSchema.manifest(List.of()),Map.of());
    private static final Map<UUID,Pending> pending=new HashMap<>();
    private static final Set<UUID> deferred=new HashSet<>();
    private static final int MAX_CLIENT_PACKETS_PER_SECOND=8;
    private static final int MAX_TIMEOUT_RETRIES=2;
    private static final Map<UUID,long[]> packetRates=new HashMap<>();
    private static final class Transfer {final String id;int offset;Transfer(String id){this.id=id;}}
    static final class Pending {
        final String revision;final long expires;final int retries;final ArrayDeque<Transfer> queue=new ArrayDeque<>();
        boolean requested,ready;
        Pending(String revision,long now){this(revision,now,0);}
        Pending(String revision,long now,int retries){
            this.revision=revision;this.retries=retries;expires=now+AssetSchema.SERVER_TIMEOUT_NANOS;
        }
    }
    private record Retry(ServerPlayer player,int attempt) {}
    @SubscribeEvent public void started(ServerStartedEvent e){
        try{reload(e.getServer());}catch(Exception failure){
            System.err.println("[Psychiatryk Assets] Asset scan rejected; optional assets disabled: "+failure.getMessage());
            current=new Snapshot(AssetSchema.manifest(List.of()),Map.of());
        }
    }
    @SubscribeEvent public void login(PlayerEvent.PlayerLoggedInEvent e){
        if(e.getEntity() instanceof ServerPlayer p)sendManifest(p);
    }
    @SubscribeEvent public void logout(PlayerEvent.PlayerLoggedOutEvent e){
        UUID id=e.getEntity().getUUID();pending.remove(id);deferred.remove(id);packetRates.remove(id);
    }

    /** Validate a complete candidate before making it visible to players. Called on the server thread. */
    public static void reload(MinecraftServer server)throws IOException {
        var next=scan(DIRECTORY);
        if(next.manifest().revision().equals(current.manifest().revision())){
            current=next;
            long now=System.nanoTime();
            for(var player:server.getPlayerList().getPlayers())
                if(needsManifest(pending.get(player.getUUID()),next.manifest().revision(),now))sendManifest(player);
            System.out.println("[Psychiatryk Assets] unchanged revision="+next.manifest().revision().substring(0,12));
            return;
        }
        current=next;pending.clear();deferred.clear();
        for(var p:server.getPlayerList().getPlayers())sendManifest(p);
        System.out.println("[Psychiatryk Assets] revision="+next.manifest().revision().substring(0,12)+" assets="+next.blobs().size());
    }
    static boolean needsManifest(Pending state,String revision,long now){
        return state==null||!state.revision.equals(revision)||!state.ready&&now>state.expires;
    }
    private static Snapshot scan(Path directory)throws IOException {
        if(!Files.exists(directory))return new Snapshot(AssetSchema.manifest(List.of()),Map.of());
        Path root=directory.toRealPath();var blobs=new HashMap<String,Blob>();long totalBytes=0,totalPixels=0;
        try(var stream=Files.walk(root,5)){
            var paths=stream.iterator();int visited=0;
            while(paths.hasNext()){var path=paths.next();
                AssetSchema.require(++visited<=MAX_DIRECTORY_ENTRIES,"Too many runtime asset directory entries");
                if(!Files.isRegularFile(path,LinkOption.NOFOLLOW_LINKS))continue;
                String filename=path.getFileName().toString();
                String kind=filename.endsWith(".png")?"png":filename.endsWith(".json")?"json":null;
                if(kind==null)continue;
                AssetSchema.require(blobs.size()<AssetSchema.MAX_ASSETS,"Too many runtime assets");
                AssetSchema.require(path.toRealPath().startsWith(root),"Asset outside directory");
                var relative=root.relativize(path).toString().replace('\\','/');
                String id=relative.substring(0,relative.length()-kind.length()-1);AssetSchema.id(id);
                int limit=kind.equals("json")?AssetSchema.MAX_JSON_FILE:AssetSchema.MAX_FILE;
                AssetSchema.require(!blobs.containsKey(id)&&Files.size(path)<=limit,"Duplicate or oversized asset");
                byte[] bytes;try(var input=Files.newInputStream(path)){bytes=input.readNBytes(limit+1);}
                AssetSchema.require(bytes.length<=limit,"Asset changed during read");
                var size=kind.equals("png")?AssetSchema.pngDimensions(bytes):new int[]{0,0};
                totalBytes+=bytes.length;totalPixels+=(long)size[0]*size[1];
                AssetSchema.require(totalBytes<=AssetSchema.MAX_TOTAL&&totalPixels<=AssetSchema.MAX_PIXELS,"Asset directory budget");
                if(kind.equals("png")){
                    var decoded=ImageIO.read(new java.io.ByteArrayInputStream(bytes));
                    AssetSchema.require(decoded!=null&&decoded.getWidth()==size[0]&&decoded.getHeight()==size[1],
                        "PNG could not be decoded");
                }
                if(kind.equals("json"))AssetSchema.jsonText(bytes);
                var entry=new AssetSchema.Entry(id,kind,AssetSchema.sha256(bytes),bytes.length,size[0],size[1]);
                blobs.put(id,new Blob(entry,bytes));
            }
        }
        var entries=new ArrayList<AssetSchema.Entry>();for(var b:blobs.values())entries.add(b.entry());
        return new Snapshot(AssetSchema.manifest(entries),Map.copyOf(blobs));
    }
    public static void sendManifest(ServerPlayer p){
        sendManifest(p,0);
    }
    private static void sendManifest(ServerPlayer p,int attempt){
        if(!AssetNetwork.supported(p)){pending.remove(p.getUUID());deferred.add(p.getUUID());return;}
        deferred.remove(p.getUUID());
        var manifest=current.manifest();pending.put(p.getUUID(),new Pending(manifest.revision(),System.nanoTime(),attempt));
        AssetNetwork.sendManifest(p,manifest);
    }
    static int nextTimeoutRetry(Pending state,long now){
        return state!=null&&!state.ready&&now>state.expires&&state.retries<MAX_TIMEOUT_RETRIES
            ?state.retries+1:-1;
    }
    /** A cheap server-thread gate before either JSON parser; one normal Request+Ready uses two slots. */
    static boolean admit(UUID id,long now){
        var rate=packetRates.computeIfAbsent(id,ignored->new long[]{now,0});
        if(now<rate[0]||now-rate[0]>=1_000_000_000L){rate[0]=now;rate[1]=0;}
        return ++rate[1]<=MAX_CLIENT_PACKETS_PER_SECOND;
    }
    public static void request(ServerPlayer p,String raw){
        if(!admit(p.getUUID(),System.nanoTime()))return;
        try{var request=AssetSchema.parseRequest(raw);var state=pending.get(p.getUUID());var snapshot=current;
            if(state==null||state.ready||state.requested||System.nanoTime()>state.expires
                ||!state.revision.equals(request.revision())||!snapshot.manifest().revision().equals(request.revision()))return;
            for(String id:request.missing())AssetSchema.require(snapshot.blobs().containsKey(id),"Unknown requested asset");
            state.requested=true;for(String id:request.missing())state.queue.add(new Transfer(id));
        }catch(RuntimeException ignored){/* Malformed client requests cannot change transfer state. */}
    }
    public static void readyAck(ServerPlayer p,String raw){
        // Ready is a client assertion for visual timing only. Never use it as authorization.
        if(!admit(p.getUUID(),System.nanoTime()))return;
        try{var ready=AssetSchema.parseReady(raw);var state=pending.get(p.getUUID());
            if(state!=null&&!state.ready&&System.nanoTime()<=state.expires&&state.queue.isEmpty()
                &&state.revision.equals(ready.revision())&&current.manifest().revision().equals(ready.revision())){
                state.ready=true;
                try{RuntimeServer.assetsReady(p);}
                catch(RuntimeException integration){System.err.println("[Psychiatryk Assets] Ready callback failed: "+integration.getClass().getSimpleName());}
            }
        }catch(RuntimeException ignored){/* Invalid acknowledgments have no effect. */}
    }
    /** Feature handlers can fall back until the client has verified and registered every asset. */
    public static boolean ready(ServerPlayer p){
        var state=pending.get(p.getUUID());return state!=null&&state.ready&&state.revision.equals(current.manifest().revision());
    }
    public static String revision(){return current.manifest().revision();}
    public static boolean hasPng(String id){
        var blob=current.blobs().get(id);return blob!=null&&blob.entry().kind().equals("png");
    }
    @SubscribeEvent public void tick(ServerTickEvent.Post event){
        if(event.getServer().getTickCount()%20==0)for(UUID id:List.copyOf(deferred)){
            var player=event.getServer().getPlayerList().getPlayer(id);
            if(player==null)deferred.remove(id);
            else if(AssetNetwork.supported(player))sendManifest(player);
        }
        long now=System.nanoTime();var it=pending.entrySet().iterator();int globalChunks=4;
        var retries=new ArrayList<Retry>();
        while(it.hasNext()){
            var entry=it.next();var state=entry.getValue();var p=event.getServer().getPlayerList().getPlayer(entry.getKey());
            if(p==null){it.remove();continue;}
            if(!state.ready&&now>state.expires){
                int attempt=nextTimeoutRetry(state,now);it.remove();
                if(attempt>=0)retries.add(new Retry(p,attempt));
                continue;
            }
            if(state.ready||!state.revision.equals(current.manifest().revision()))continue;
            while(globalChunks>0&&!state.queue.isEmpty()){
                var transfer=state.queue.peek();var blob=current.blobs().get(transfer.id);
                if(blob==null){state.queue.clear();break;}
                int length=Math.min(AssetSchema.CHUNK,blob.bytes().length-transfer.offset);
                if(length<=0){state.queue.remove();continue;}
                AssetNetwork.sendChunk(p,new AssetNetwork.Chunk(state.revision,transfer.id,transfer.offset,
                    Arrays.copyOfRange(blob.bytes(),transfer.offset,transfer.offset+length)));
                transfer.offset+=length;globalChunks--;
                if(transfer.offset==blob.bytes().length)state.queue.remove();
            }
        }
        // Resend after iteration so replacing a pending entry cannot invalidate the iterator.
        for(var retry:retries)sendManifest(retry.player(),retry.attempt());
    }
}
