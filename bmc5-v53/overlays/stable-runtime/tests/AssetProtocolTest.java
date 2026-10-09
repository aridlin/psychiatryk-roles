import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.netty.buffer.Unpooled;
import java.awt.image.BufferedImage;
import java.lang.reflect.InvocationTargetException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import javax.imageio.ImageIO;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.RegistryFriendlyByteBuf;
import pl.aridlin.psychiatrykroles.runtime.AssetNetwork;
import pl.aridlin.psychiatrykroles.runtime.AssetSchema;
import pl.aridlin.psychiatrykroles.runtime.AssetServer;

/** Protocol and filesystem adversarial checks; requires no Minecraft client or OpenGL. */
public final class AssetProtocolTest {
    private static int checks;
    private static void check(boolean value,String why){checks++;if(!value)throw new AssertionError(why);}
    private interface Throwing {void run()throws Exception;}
    private static void rejects(Throwing run,String why){boolean rejected=false;try{run.run();}catch(Exception expected){rejected=true;}check(rejected,why);}
    private static Object scan(Path directory)throws Exception {
        var method=AssetServer.class.getDeclaredMethod("scan",Path.class);method.setAccessible(true);
        try{return method.invoke(null,directory);}catch(InvocationTargetException e){throw (Exception)e.getCause();}
    }
    private static AssetSchema.Manifest scannedManifest(Path directory)throws Exception {
        var result=scan(directory);var method=result.getClass().getDeclaredMethod("manifest");method.setAccessible(true);
        return (AssetSchema.Manifest)method.invoke(result);
    }
    public static void main(String[] args)throws Exception {
        var image=new BufferedImage(2,3,BufferedImage.TYPE_INT_ARGB);
        var bytes=new java.io.ByteArrayOutputStream();check(ImageIO.write(image,"png",bytes),"PNG writer");
        byte[] png=bytes.toByteArray();var dimensions=AssetSchema.pngDimensions(png);
        check(dimensions[0]==2&&dimensions[1]==3,"PNG dimensions");
        var entry=new AssetSchema.Entry("ui/poker/card","png",AssetSchema.sha256(png),png.length,2,3);
        byte[] json="{\"schema\":1,\"clips\":[{\"name\":\"wave\"}]}".getBytes(StandardCharsets.UTF_8);
        var jsonEntry=new AssetSchema.Entry("ui/poker/clips","json",AssetSchema.sha256(json),json.length,0,0);
        var manifest=AssetSchema.manifest(List.of(entry,jsonEntry));
        check(AssetSchema.parseManifest(AssetSchema.JSON.toJson(manifest)).equals(manifest),"mixed manifest roundtrip");
        check(AssetSchema.jsonText(json).contains("wave"),"JSON data verified");
        var request=new AssetSchema.Request(1,manifest.revision(),List.of(entry.id()));
        check(AssetSchema.parseRequest(AssetSchema.JSON.toJson(request)).equals(request),"request roundtrip");
        var ready=new AssetSchema.Ready(1,manifest.revision());
        check(AssetSchema.parseReady(AssetSchema.JSON.toJson(ready)).equals(ready),"ready roundtrip");
        rejects(()->AssetSchema.id("../escape"),"traversal denied");
        rejects(()->AssetSchema.id("/absolute"),"absolute denied");
        rejects(()->AssetSchema.id("ui\\backslash"),"backslash denied");
        rejects(()->AssetSchema.id("ui//gap"),"empty segment denied");
        rejects(()->AssetSchema.manifest(List.of(entry,entry)),"duplicate ID denied");
        rejects(()->AssetSchema.manifest(List.of(new AssetSchema.Entry("bad","png","0".repeat(64),AssetSchema.MAX_FILE+1,1,1))),"oversize file denied");
        rejects(()->AssetSchema.manifest(List.of(new AssetSchema.Entry("bad","png","0".repeat(64),1,513,1))),"oversize width denied");
        rejects(()->AssetSchema.manifest(List.of(new AssetSchema.Entry("bad","json","0".repeat(64),1,1,1))),"JSON texture dimensions denied");
        rejects(()->AssetSchema.manifest(List.of(new AssetSchema.Entry("bad","script","0".repeat(64),1,0,0))),"unrecognized asset type denied");
        rejects(()->AssetSchema.jsonText("{broken".getBytes(StandardCharsets.UTF_8)),"malformed JSON asset denied");
        rejects(()->AssetSchema.jsonText(new byte[]{(byte)0xC3,(byte)0x28}),"invalid UTF-8 denied");
        rejects(()->AssetSchema.jsonText(("[".repeat(30)+"]".repeat(30)).getBytes(StandardCharsets.UTF_8)),"deep JSON asset denied");
        var thirtyThree=new ArrayList<AssetSchema.Entry>();
        for(int i=0;i<33;i++)thirtyThree.add(new AssetSchema.Entry("id"+i,"png","0".repeat(64),1,1,1));
        rejects(()->AssetSchema.manifest(thirtyThree),"asset count denied");
        var altered=JsonParser.parseString(AssetSchema.JSON.toJson(manifest)).getAsJsonObject();altered.addProperty("revision","0".repeat(64));
        rejects(()->AssetSchema.parseManifest(altered.toString()),"manifest tampering denied");
        rejects(()->AssetSchema.parseManifest("[".repeat(100)+"]".repeat(100)),"nested manifest denied");
        rejects(()->AssetSchema.parseRequest("{".repeat(100)+"}".repeat(100)),"nested request denied");
        rejects(()->AssetSchema.parseRequest(AssetSchema.JSON.toJson(new AssetSchema.Request(1,manifest.revision(),List.of("x","x")))),"duplicate request denied");
        byte[] invalidSize=png.clone();invalidSize[16]=0;invalidSize[17]=0;invalidSize[18]=2;invalidSize[19]=1;
        rejects(()->AssetSchema.pngDimensions(invalidSize),"large compressed PNG header denied");
        byte[] invalidSignature=png.clone();invalidSignature[0]=0;
        rejects(()->AssetSchema.pngDimensions(invalidSignature),"PNG signature denied");

        for(int size:new int[]{1,97,AssetSchema.CHUNK}){
            var chunk=new AssetNetwork.Chunk(manifest.revision(),entry.id(),17,new byte[size]);
            var buf=new RegistryFriendlyByteBuf(Unpooled.buffer(),RegistryAccess.EMPTY);
            try{AssetNetwork.Chunk.CODEC.encode(buf,chunk);var decoded=AssetNetwork.Chunk.CODEC.decode(buf);
                check(decoded.revision().equals(chunk.revision())&&decoded.id().equals(chunk.id())&&decoded.offset()==17
                    &&decoded.bytes().length==size&&buf.readableBytes()==0,"chunk codec "+size);
            }finally{buf.release();}
        }
        var oversized=new RegistryFriendlyByteBuf(Unpooled.buffer(),RegistryAccess.EMPTY);
        try{rejects(()->AssetNetwork.Chunk.CODEC.encode(oversized,new AssetNetwork.Chunk(manifest.revision(),entry.id(),0,new byte[AssetSchema.CHUNK+1])),"oversize chunk encoder");}
        finally{oversized.release();}
        var malformed=new RegistryFriendlyByteBuf(Unpooled.buffer(),RegistryAccess.EMPTY);
        try{malformed.writeUtf(manifest.revision(),64);malformed.writeUtf(entry.id(),80);malformed.writeVarInt(0);malformed.writeByteArray(new byte[AssetSchema.CHUNK+1]);
            rejects(()->AssetNetwork.Chunk.CODEC.decode(malformed),"oversize chunk decoder");}
        finally{malformed.release();}

        Path directory=Files.createTempDirectory("psychiatryk-asset-scan-");Files.createDirectories(directory.resolve("ui"));
        Files.write(directory.resolve("ui/card.png"),png);
        Files.write(directory.resolve("ui/clips.json"),json);
        var scanned=scannedManifest(directory);check(scanned.assets().size()==2&&scanned.assets().stream().anyMatch(e->e.id().equals("ui/card")&&e.kind().equals("png"))
            &&scanned.assets().stream().anyMatch(e->e.id().equals("ui/clips")&&e.kind().equals("json")),"mixed bounded directory scan");
        var outside=Files.createTempFile("psychiatryk-asset-outside-",".png");Files.write(outside,png);
        Files.createSymbolicLink(directory.resolve("outside.png"),outside);
        check(scannedManifest(directory).assets().size()==2,"symlink ignored");
        Files.write(directory.resolve("bad name.png"),png);rejects(()->scan(directory),"unsafe filename denied");
        Files.delete(directory.resolve("bad name.png"));
        Files.write(directory.resolve("broken.json"),"{broken".getBytes(StandardCharsets.UTF_8));
        rejects(()->scan(directory),"invalid JSON rejected before publish");
        Files.delete(directory.resolve("broken.json"));
        Files.write(directory.resolve("truncated.png"),java.util.Arrays.copyOf(png,33));
        rejects(()->scan(directory),"truncated PNG rejected before publish");
        Files.delete(directory.resolve("truncated.png"));
        Files.write(directory.resolve("oversize.png"),new byte[AssetSchema.MAX_FILE+1]);
        rejects(()->scan(directory),"oversized disk file denied");
        Path noisy=Files.createTempDirectory("psychiatryk-asset-noisy-");
        for(int i=0;i<257;i++)Files.writeString(noisy.resolve("ignored-"+i+".txt"),"x");
        rejects(()->scan(noisy),"unrelated directory entries bounded");
        System.out.println("ASSET_PROTOCOL_PASS checks="+checks);
    }
}
