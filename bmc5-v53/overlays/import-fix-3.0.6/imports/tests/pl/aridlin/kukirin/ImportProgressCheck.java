package pl.aridlin.kukirin;
import com.google.gson.*;
import com.sun.net.httpserver.HttpServer;
import java.lang.reflect.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.atomic.*;
import net.neoforged.fml.loading.FMLPaths;

public final class ImportProgressCheck {
 private static int checks;
 private static void check(boolean value,String message) {++checks;if(!value)throw new AssertionError(message);}
 private static JsonObject entry(String id) {
  JsonObject row=new JsonObject();row.addProperty("videoId",id);row.addProperty("sha256","a".repeat(64));
  row.addProperty("bytes",44);row.addProperty("durationTicks",20);row.addProperty("title",id);row.addProperty("artist","Fixture");return row;
 }
 private static JsonObject receipt(String status,JsonObject...entries) {
  JsonObject row=new JsonObject();row.addProperty("jobId","b950a897-9ba7-4ced-bb09-e0487b35098c");row.addProperty("status",status);
  JsonArray array=new JsonArray();for(JsonObject e:entries)array.add(e);row.add("entries",array);return row;
 }
 public static void main(String[] args)throws Exception {
  check(MusicImports.canonical("https://music.youtube.com/watch?v=MQAm6EvOWTw&list=LM",false).equals("https://music.youtube.com/watch?v=MQAm6EvOWTw"),"LM song context canonicalized");
  check(MusicImports.canonical("https://youtu.be/MQAm6EvOWTw?si=abc",false).endsWith("v=MQAm6EvOWTw"),"short song URL");
  try{MusicImports.canonical("https://example.invalid/watch?v=MQAm6EvOWTw",false);throw new AssertionError("external origin");}catch(IllegalArgumentException expected){++checks;}
  check(Arrays.stream(MusicImports.class.getDeclaredFields()).noneMatch(f->f.getName().equals("lastRequest")),"no minute cooldown map");
  Class<?> configType=Class.forName("pl.aridlin.kukirin.MusicImports$Configuration");
  Class<?> progressType=Class.forName("pl.aridlin.kukirin.MusicImports$Progress");
  Constructor<?> constructor=configType.getDeclaredConstructors()[0];constructor.setAccessible(true);
  Method importJob=MusicImports.class.getDeclaredMethod("importJob",configType,String.class,boolean.class,progressType);importJob.setAccessible(true);
  AtomicInteger mode=new AtomicInteger(),polls=new AtomicInteger();List<Integer> batches=new ArrayList<>();
  HttpServer server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
  server.createContext("/jobs", exchange->{
   boolean post=exchange.getRequestMethod().equals("POST");
   JsonObject row;
   if(mode.get()==1)row=receipt("complete",entry("MQAm6EvOWTw"));
   else if(post)row=receipt("running",entry("MQAm6EvOWTw"));
   else {polls.incrementAndGet();row=mode.get()==2?receipt("failed",entry("MQAm6EvOWTw")):receipt("complete",entry("MQAm6EvOWTw"),entry("bcdefghijkl"));}
   byte[] data=row.toString().getBytes(StandardCharsets.UTF_8);exchange.sendResponseHeaders(200,data.length);exchange.getResponseBody().write(data);exchange.close();
  });server.start();
  try {
   Object config=constructor.newInstance(URI.create("http://127.0.0.1:"+server.getAddress().getPort()),"synthetic-test-token");
   Object callback=java.lang.reflect.Proxy.newProxyInstance(progressType.getClassLoader(),new Class<?>[]{progressType},(obj,method,value)->{
    List<?> batch=(List<?>)value[0];batches.add(batch.size());
    if(batches.size()==1)check(polls.get()==0,"first readiness callback runs before later poll");
    return null;
   });
   importJob.invoke(null,config,"https://music.youtube.com/watch?v=MQAm6EvOWTw",true,callback);
   check(batches.equals(List.of(1,1)),"only new playlist rows published progressively");
   check(polls.get()==1,"completed after next response");
   mode.set(1);batches.clear();polls.set(0);
   importJob.invoke(null,config,"https://music.youtube.com/watch?v=MQAm6EvOWTw",false,callback);
   check(batches.equals(List.of(1))&&polls.get()==0,"cached POST immediately invokes ready without sleep or GET");
   mode.set(2);batches.clear();polls.set(0);
   try {importJob.invoke(null,config,"https://music.youtube.com/watch?v=MQAm6EvOWTw",true,callback);throw new AssertionError("later failure");}
   catch(InvocationTargetException expected){check(expected.getCause() instanceof java.io.IOException,"failed continuation reported");}
   check(batches.equals(List.of(1)),"first ready publication remains before continuation failure");
   // Local file reading must not enforce the old1MiB catalogue restriction.
   Path root=Files.createTempDirectory(Path.of(args[0]),"catalog-test-");FMLPaths.loadAbsolutePaths(root);
   Path catalogue=MusicImports.catalogFile();Files.createDirectories(catalogue.getParent());
   JsonObject large=new JsonObject();large.addProperty("padding","x".repeat(1100000));large.add("songs",new JsonArray());Files.writeString(catalogue,large.toString());
   Method read=MusicImports.class.getDeclaredMethod("readCatalog");read.setAccessible(true);Object value=read.invoke(null);
   check(value instanceof JsonObject &&Files.size(catalogue)>1048576,"large catalogue is readable");
   try(var paths=Files.walk(root)){for(Path p:paths.sorted(Comparator.reverseOrder()).toList())Files.delete(p);}
  } finally {server.stop(0);}
  System.out.println("{\"success\":true,\"assertions\":"+checks+",\"native_game_launched\":false}");
 }
}
