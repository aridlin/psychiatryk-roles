package pl.aridlin.unlocks;
import com.google.gson.*;
import java.net.URI;
import java.net.http.*;
import java.nio.file.*;
import java.time.*;
import java.util.*;
/** Sends only the public item calendar; no player data or server configuration. */
public final class StatusPublisher {
    private static final HttpClient HTTP=HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
    private static volatile boolean busy;
    public static void publish(){
        if(busy)return;
        Path config=Path.of("config/psychiatryk-unlocks-publish.properties");if(!Files.exists(config))return;
        try{var p=new Properties();try(var reader=Files.newBufferedReader(config)){p.load(reader);}
            var root=new JsonObject();root.addProperty("timezone",Rules.ZONE.toString());root.addProperty("serverUpdatedAt",Instant.now().toString());root.addProperty("active",true);root.add("items",new Gson().toJsonTree(Rules.all()));
            var req=HttpRequest.newBuilder(URI.create(p.getProperty("url"))).timeout(Duration.ofSeconds(10)).header("Authorization","Bearer "+p.getProperty("token")).header("Content-Type","application/json").POST(HttpRequest.BodyPublishers.ofString(root.toString())).build();
            busy=true;HTTP.sendAsync(req,HttpResponse.BodyHandlers.discarding()).whenComplete((r,e)->{busy=false;if(e!=null||r.statusCode()!=200)org.slf4j.LoggerFactory.getLogger("PsychiatrykUnlocks").warn("Timeline publication failed; next minute will retry");});
        }catch(Exception e){org.slf4j.LoggerFactory.getLogger("PsychiatrykUnlocks").warn("Timeline publisher config could not be loaded");}
    }
}
