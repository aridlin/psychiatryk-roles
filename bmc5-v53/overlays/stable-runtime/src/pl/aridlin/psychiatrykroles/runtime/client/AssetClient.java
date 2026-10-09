package pl.aridlin.psychiatrykroles.runtime.client;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderSystem;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import pl.aridlin.psychiatrykroles.runtime.AssetNetwork;
import pl.aridlin.psychiatrykroles.runtime.AssetPrepQueue;
import pl.aridlin.psychiatrykroles.runtime.AssetSchema;

/** Client cache and render-thread preload. No URL fetch or resource-pack reload is involved. */
@EventBusSubscriber(modid="psychiatryk_runtime",value=Dist.CLIENT)
public final class AssetClient {
    private AssetClient() {}
    private static final Path CACHE=FMLPaths.GAMEDIR.get().resolve("cache/psychiatryk-runtime/assets");
    private static final long MAX_CACHE_BYTES=8L*1024*1024;
    private static final java.util.List<Runnable> readyHooks=new ArrayList<>();
    private static final List<Consumer<Progress>> progressHooks=new ArrayList<>();
    private static Object connection;
    private static ClientState current,candidate;
    private static final class Transfer {
        final AssetSchema.Entry entry;final byte[] bytes;int offset;
        Transfer(AssetSchema.Entry entry){this.entry=entry;bytes=new byte[entry.bytes()];}
    }
    private static final class ClientState {
        final AssetSchema.Manifest manifest;final long expires;
        final AssetPrepQueue<AssetSchema.Entry> cacheChecks;
        final Map<String,Transfer> missing=new HashMap<>();boolean ready;
        final Map<String,String> statuses=new HashMap<>();
        final Map<String,Integer> received=new HashMap<>();
        final Map<String,ResourceLocation> textures=new HashMap<>();
        final Map<String,Size> sizes=new HashMap<>();
        final Map<String,String> documents=new HashMap<>();
        final Set<ResourceLocation> registrations=new HashSet<>();
        ClientState(AssetSchema.Manifest manifest){
            this.manifest=manifest;expires=System.nanoTime()+AssetSchema.TIMEOUT_NANOS;
            cacheChecks=new AssetPrepQueue<>(manifest.assets());
            for(var entry:manifest.assets()){statuses.put(entry.id(),"waiting");received.put(entry.id(),0);}
        }
    }
    public record Size(int width,int height) {}
    public record ProgressItem(String id,String kind,int bytes,int received,String status) {}
    public record Progress(String revision,String phase,String activeId,int complete,int total,long receivedBytes,long totalBytes,
                           boolean previousAvailable,List<ProgressItem> items) {}
    public static void install(){AssetNetwork.clientManifest=AssetClient::manifest;AssetNetwork.clientChunk=AssetClient::chunk;}
    /** Register during client setup. Invoked after verification but before each Ready ACK. */
    public static void onReady(Runnable callback){
        if(current!=null||candidate!=null)throw new IllegalStateException("Register asset-ready hooks during client setup");
        readyHooks.add(java.util.Objects.requireNonNull(callback));
    }
    /** Register during client setup. Updates occur on manifest, cache, chunk and completion, never each frame. */
    public static void onProgress(Consumer<Progress> callback){
        if(current!=null||candidate!=null)throw new IllegalStateException("Register asset-progress hooks during client setup");
        progressHooks.add(java.util.Objects.requireNonNull(callback));
    }
    /** Renderers should use only these registered textures after ready() becomes true. */
    public static Optional<ResourceLocation> texture(String id){
        return current!=null&&current.ready?Optional.ofNullable(current.textures.get(id)):Optional.empty();
    }
    public static Optional<Size> dimensions(String id){
        return current!=null&&current.ready?Optional.ofNullable(current.sizes.get(id)):Optional.empty();
    }
    /** JSON is syntax checked and cached; feature engines must validate their own document schema. */
    public static Optional<String> json(String id){
        return current!=null&&current.ready?Optional.ofNullable(current.documents.get(id)):Optional.empty();
    }
    public static boolean ready(){return current!=null&&current.ready;}
    public static String revision(){return current==null?"":current.manifest.revision();}
    static void manifest(String raw){
        var mc=Minecraft.getInstance();var nextConnection=mc.getConnection();if(nextConnection==null)return;
        if(connection!=nextConnection&&(current!=null||candidate!=null))clearAll();
        connection=nextConnection;
        try{var incoming=AssetSchema.parseManifest(raw);
            abortCandidate();var state=new ClientState(incoming);candidate=state;
            progress(state,"checking","");
        }catch(Exception failure){fail("Asset manifest rejected");}
    }
    /** Runs only from ClientTickEvent.Post, so cache hashing and texture uploads have a strict frame budget. */
    private static void prepareCache(ClientState state){
        try{
            for(var entry:state.cacheChecks.nextBatch()){
                if(reuse(state,entry)){state.statuses.put(entry.id(),"cached");state.received.put(entry.id(),entry.bytes());progress(state,"checking",entry.id());continue;}
                byte[] cached=readCache(entry);
                if(cached!=null)try{register(state,entry,cached);state.statuses.put(entry.id(),"cached");state.received.put(entry.id(),entry.bytes());progress(state,"checking",entry.id());continue;}
                    catch(Exception ignored){/* Corrupt cache becomes a fresh request. */}
                state.missing.put(entry.id(),new Transfer(entry));state.statuses.put(entry.id(),"queued");progress(state,"checking",entry.id());
            }
            if(!state.cacheChecks.finish())return;
            if(state.missing.isEmpty()){complete();return;}
            progress(state,"transferring","");
            AssetNetwork.requestMissing(new AssetSchema.Request(AssetSchema.VERSION,state.manifest.revision(),new ArrayList<>(state.missing.keySet())));
        }catch(Exception failure){fail("Asset cache preparation failed");}
    }
    static void chunk(AssetNetwork.Chunk part){
        var state=candidate;if(state==null||state.ready||System.nanoTime()>state.expires)return;
        if(!state.manifest.revision().equals(part.revision()))return;
        var transfer=state.missing.get(part.id());if(transfer==null)return;
        if(part.offset()!=transfer.offset||part.bytes()==null||part.bytes().length==0
            ||part.bytes().length>AssetSchema.CHUNK||(long)part.offset()+part.bytes().length>transfer.bytes.length){fail("Invalid asset chunk");return;}
        System.arraycopy(part.bytes(),0,transfer.bytes,transfer.offset,part.bytes().length);transfer.offset+=part.bytes().length;
        state.received.put(part.id(),transfer.offset);state.statuses.put(part.id(),"transferring");progress(state,"transferring",part.id());
        if(transfer.offset!=transfer.bytes.length)return;
        try{
            state.statuses.put(part.id(),"verifying");progress(state,"verifying",part.id());
            validate(transfer.entry,transfer.bytes);register(state,transfer.entry,transfer.bytes);
            writeCache(transfer.entry,transfer.bytes);state.missing.remove(part.id());
            state.statuses.put(part.id(),"verified");progress(state,"transferring",part.id());
            if(state.missing.isEmpty())complete();
        }catch(Exception failure){fail("Asset verification failed");}
    }
    private static boolean reuse(ClientState state,AssetSchema.Entry entry){
        if(current==null||!current.ready)return false;
        for(var old:current.manifest.assets())if(old.kind().equals(entry.kind())
            &&old.sha256().equals(entry.sha256())&&old.bytes()==entry.bytes()){
            if(entry.kind().equals("json")){
                var text=current.documents.get(old.id());if(text==null)continue;
                state.documents.put(entry.id(),text);return true;
            }
            var texture=current.textures.get(old.id());if(texture==null)continue;
            AssetSchema.require(old.width()==entry.width()&&old.height()==entry.height(),"Reused texture dimensions");
            state.textures.put(entry.id(),texture);state.sizes.put(entry.id(),new Size(entry.width(),entry.height()));
            state.registrations.add(texture);return true;
        }
        return false;
    }
    private static void validate(AssetSchema.Entry entry,byte[] bytes){
        AssetSchema.require(bytes.length==entry.bytes()&&AssetSchema.sha256(bytes).equals(entry.sha256()),"Asset digest");
        if(entry.kind().equals("json")){AssetSchema.jsonText(bytes);return;}
        var size=AssetSchema.pngDimensions(bytes);
        AssetSchema.require(size[0]==entry.width()&&size[1]==entry.height(),"Asset dimensions changed");
    }
    private static void register(ClientState state,AssetSchema.Entry entry,byte[] bytes)throws IOException {
        validate(entry,bytes);
        if(entry.kind().equals("json")){state.documents.put(entry.id(),AssetSchema.jsonText(bytes));return;}
        AssetSchema.require(RenderSystem.isOnRenderThread(),"Asset registration off render thread");
        var location=ResourceLocation.fromNamespaceAndPath("psychiatryk_runtime","asset/"+entry.sha256());
        if(!state.registrations.contains(location)&&(current==null||!current.registrations.contains(location))){
            NativeImage image=NativeImage.read(bytes);
            if(image.getWidth()!=entry.width()||image.getHeight()!=entry.height()){image.close();throw new IOException("Decoded PNG dimensions differ");}
            DynamicTexture texture=null;
            try{texture=new DynamicTexture(image);Minecraft.getInstance().getTextureManager().register(location,texture);
            }catch(RuntimeException error){if(texture!=null)texture.close();else image.close();throw error;}
        }
        state.registrations.add(location);state.textures.put(entry.id(),location);
        state.sizes.put(entry.id(),new Size(entry.width(),entry.height()));
    }
    private static byte[] readCache(AssetSchema.Entry entry){
        var path=cachePath(entry);
        try{if(!Files.isRegularFile(path,LinkOption.NOFOLLOW_LINKS)||Files.size(path)!=entry.bytes())return null;
            byte[] bytes;try(var input=Files.newInputStream(path)){bytes=input.readNBytes(AssetSchema.MAX_FILE+1);}
            validate(entry,bytes);return bytes;
        }catch(Exception invalid){return null;}
    }
    private static void writeCache(AssetSchema.Entry entry,byte[] bytes){
        try{Files.createDirectories(CACHE);Path destination=cachePath(entry);
            Path temporary=Files.createTempFile(CACHE,"incoming-",".tmp");
            try{Files.write(temporary,bytes);
                try{Files.move(temporary,destination,StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING);}
                catch(java.nio.file.AtomicMoveNotSupportedException unsupported){Files.move(temporary,destination,StandardCopyOption.REPLACE_EXISTING);}
            }finally{Files.deleteIfExists(temporary);}
            pruneCache();
        }catch(IOException ignored){/* Loaded in memory; a read-only cache only causes a new request next join. */}
    }
    private static void pruneCache()throws IOException {
        var files=new ArrayList<Path>();long total=0;
        try(var stream=Files.list(CACHE)){for(var path:stream.toList()){
            if(!Files.isRegularFile(path,LinkOption.NOFOLLOW_LINKS)||!path.getFileName().toString().matches("[a-f0-9]{64}\\.(png|json)"))continue;
            files.add(path);total+=Files.size(path);
        }}
        if(total<=MAX_CACHE_BYTES)return;
        files.sort((a,b)->{try{return Files.getLastModifiedTime(a).compareTo(Files.getLastModifiedTime(b));}catch(IOException ignored){return 0;}});
        var active=new HashSet<String>();if(current!=null)for(var entry:current.manifest.assets())active.add(entry.sha256()+"."+entry.kind());
        if(candidate!=null)for(var entry:candidate.manifest.assets())active.add(entry.sha256()+"."+entry.kind());
        for(var path:files){if(total<=MAX_CACHE_BYTES)break;if(active.contains(path.getFileName().toString()))continue;
            long bytes=Files.size(path);Files.deleteIfExists(path);total-=bytes;
        }
    }
    private static void complete(){
        var state=candidate;if(state==null||!state.missing.isEmpty())return;
        if(System.nanoTime()>state.expires){fail("transfer timed out");return;}
        progress(state,"preparing","");
        var previous=current;state.ready=true;current=state;
        try{for(var callback:List.copyOf(readyHooks))callback.run();}
        catch(RuntimeException | LinkageError invalid){
            progress(state,"failed","");
            current=previous;abortCandidate();
            if(previous!=null)try{for(var callback:List.copyOf(readyHooks))callback.run();}catch(RuntimeException | LinkageError ignored){}
            notifyFailure("feature data rejected");return;
        }
        candidate=null;releaseExcept(previous,state.registrations);
        progress(state,"ready","");
        AssetNetwork.announceReady(new AssetSchema.Ready(AssetSchema.VERSION,state.manifest.revision()));
    }
    private static void fail(String reason){
        progress(candidate,"failed","");
        abortCandidate();notifyFailure(reason);
    }
    private static void progress(ClientState state,String phase,String activeId){
        if(progressHooks.isEmpty())return;
        if(state==null){var empty=new Progress("",phase,activeId,0,0,0,0,current!=null&&current.ready,List.of());
            for(var hook:List.copyOf(progressHooks))try{hook.accept(empty);}catch(RuntimeException ignored){}return;}
        var items=new ArrayList<ProgressItem>(state.manifest.assets().size());int completed=0;long receivedBytes=0,totalBytes=0;
        for(var entry:state.manifest.assets()){
            var status=state.statuses.get(entry.id());int received=state.received.getOrDefault(entry.id(),0);
            if(status.equals("cached")||status.equals("verified"))completed++;
            receivedBytes+=received;totalBytes+=entry.bytes();
            items.add(new ProgressItem(entry.id(),entry.kind(),entry.bytes(),received,status));
        }
        var snapshot=new Progress(state.manifest.revision(),phase,activeId,completed,items.size(),receivedBytes,totalBytes,
            current!=null&&current.ready,List.copyOf(items));
        for(var hook:List.copyOf(progressHooks))try{hook.accept(snapshot);}catch(RuntimeException ignored){}
    }
    private static void notifyFailure(String reason){
        var player=Minecraft.getInstance().player;
        if(player!=null)player.displayClientMessage(Component.literal(
            current==null?"Psychiatryk assets unavailable: "+reason:"Psychiatryk asset update rejected; previous assets remain active: "+reason),false);
    }
    private static void releaseExcept(ClientState state,Set<ResourceLocation> retained){
        if(state==null)return;var manager=Minecraft.getInstance().getTextureManager();
        for(var location:state.registrations)if(!retained.contains(location))manager.release(location);
    }
    private static void abortCandidate(){
        if(candidate==null)return;releaseExcept(candidate,current==null?Set.of():current.registrations);candidate=null;
    }
    private static void clearAll(){
        if(candidate!=null)releaseExcept(candidate,current==null?Set.of():current.registrations);
        releaseExcept(current,Set.of());candidate=null;current=null;progress(null,"disconnected","");
    }
    private static Path cachePath(AssetSchema.Entry entry){return CACHE.resolve(entry.sha256()+"."+entry.kind());}
    @SubscribeEvent public static void tick(ClientTickEvent.Post ignored){
        var mc=Minecraft.getInstance();if(connection!=mc.getConnection()){
            if(current!=null||candidate!=null)clearAll();
            else progress(null,"disconnected","");
            connection=mc.getConnection();
        }
        if(candidate!=null){
            if(System.nanoTime()>candidate.expires)fail("transfer timed out");
            else prepareCache(candidate);
        }
    }
}
