package pl.aridlin.kukirin;

import java.io.*;
import java.net.*;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;
import java.util.concurrent.*;

/** Bounded, content-addressed client download cache. No URL, token, or filenames are logged. */
public final class ScooterMusicHttpCache {
 public static final long MAX_CACHE_BYTES=128L*1024*1024;
 private static final ThreadPoolExecutor downloads=new ThreadPoolExecutor(2,2,0,TimeUnit.SECONDS,new ArrayBlockingQueue<>(8),r->{Thread t=new Thread(r,"Scooter song download");t.setDaemon(true);return t;},new ThreadPoolExecutor.AbortPolicy());
 private static final Map<String,CompletableFuture<byte[]>> inFlight=new HashMap<>();
 public static void validateUri(URI uri){
  boolean test=Boolean.getBoolean("goplanska.music.allowLocalHttp")&&"http".equals(uri.getScheme())&&("127.0.0.1".equals(uri.getHost())||"localhost".equals(uri.getHost()));
  if((!"https".equals(uri.getScheme())&&!test)||uri.getHost()==null||uri.getUserInfo()!=null||uri.getFragment()!=null||uri.toString().length()>2048)throw new IllegalArgumentException("Music requires an HTTPS URL without embedded credentials");
 }
 public static synchronized CompletableFuture<byte[]> fetch(Path cache,String url,String bearer,String sha,int expected){
  if(!sha.matches("[a-f0-9]{64}")||expected<44||expected>ScooterMusic.MAX_BYTES)return CompletableFuture.failedFuture(new IOException("Invalid song bounds"));
  URI uri;try{uri=URI.create(url);validateUri(uri);if(bearer.length()>512||bearer.indexOf('\r')>=0||bearer.indexOf('\n')>=0)throw new IllegalArgumentException();}catch(Exception ex){return CompletableFuture.failedFuture(new IOException("Invalid song URL"));}
  CompletableFuture<byte[]> old=inFlight.get(sha);if(old!=null)return old;
  CompletableFuture<byte[]> result=new CompletableFuture<>();inFlight.put(sha,result);
  try{downloads.execute(()->{try{result.complete(readOrDownload(cache,uri,bearer,sha,expected));}catch(Exception ex){result.completeExceptionally(new IOException("Song download or validation failed"));}finally{synchronized(ScooterMusicHttpCache.class){inFlight.remove(sha,result);}}});}catch(RejectedExecutionException ex){inFlight.remove(sha);result.completeExceptionally(new IOException("Song download queue is full"));}
  return result;
 }
 static byte[] readOrDownload(Path folder,URI uri,String bearer,String sha,int expected)throws Exception{
  Files.createDirectories(folder);Path target=folder.resolve(sha+".wav");
  if(Files.isRegularFile(target)&&!Files.isSymbolicLink(target)){
   if(Files.size(target)==expected){byte[] bytes=Files.readAllBytes(target);if(valid(bytes,sha,expected)){Files.setLastModifiedTime(target,java.nio.file.attribute.FileTime.fromMillis(System.currentTimeMillis()));return bytes;}}
   Files.delete(target);
  }
  HttpURLConnection connection=(HttpURLConnection)uri.toURL().openConnection();connection.setConnectTimeout(6000);connection.setReadTimeout(12000);connection.setInstanceFollowRedirects(false);connection.setRequestProperty("Accept","audio/wav");
  if(!bearer.isEmpty())connection.setRequestProperty("Authorization","Bearer "+bearer);
  byte[] bytes;
  try{
   int status=connection.getResponseCode();if(status!=200)throw new IOException("HTTP song response rejected");long length=connection.getContentLengthLong();if(length>=0&&length!=expected)throw new IOException("Song size differs from catalog");
   try(InputStream in=connection.getInputStream()){bytes=in.readNBytes(expected+1);}
  }finally{connection.disconnect();}
  if(!valid(bytes,sha,expected))throw new IOException("Song content differs from catalog");
  synchronized(ScooterMusicHttpCache.class){
   trim(folder,bytes.length,target);Path temporary=Files.createTempFile(folder,"song-",".tmp");
   try{Files.write(temporary,bytes);try{Files.move(temporary,target,StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING);}catch(AtomicMoveNotSupportedException ex){Files.move(temporary,target,StandardCopyOption.REPLACE_EXISTING);}}finally{Files.deleteIfExists(temporary);}
  }
  return bytes;
 }
 static boolean valid(byte[] bytes,String sha,int expected)throws Exception{return bytes.length==expected&&expected>=44&&bytes[0]=='R'&&bytes[1]=='I'&&bytes[2]=='F'&&bytes[3]=='F'&&bytes[8]=='W'&&bytes[9]=='A'&&bytes[10]=='V'&&bytes[11]=='E'&&HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)).equals(sha);}
 private static void trim(Path folder,long incoming,Path preserve)throws IOException{
  List<Path> files;try(var stream=Files.list(folder)){files=stream.filter(p->p.getFileName().toString().matches("[a-f0-9]{64}\\.wav")&&!Files.isSymbolicLink(p)).sorted(Comparator.comparingLong(p->{try{return Files.getLastModifiedTime(p).toMillis();}catch(IOException ex){return Long.MIN_VALUE;}})).toList();}
  long used=0;for(Path p:files)used+=Files.size(p);
  for(Path p:files){if(used+incoming<=MAX_CACHE_BYTES)break;if(!p.equals(preserve)){long n=Files.size(p);Files.delete(p);used-=n;}}
 }
 private ScooterMusicHttpCache(){}
}
