import com.sun.net.httpserver.HttpServer;
import java.io.*;import java.net.*;import java.nio.*;import java.security.*;import java.util.*;import java.util.concurrent.*;import java.util.concurrent.atomic.*;
import pl.aridlin.kukirin.ScooterHttpAudioStream;import pl.aridlin.kukirin.MusicTimeline;
public final class SyncStreamCheck {
 private static int checks;
 private static void check(boolean ok,String why){++checks;if(!ok)throw new AssertionError(why);}
 private static byte[] wav(int count,boolean rf64)throws Exception {
  ByteBuffer h=ByteBuffer.allocate(rf64?80:44).order(ByteOrder.LITTLE_ENDIAN);
  h.put((rf64?"RF64":"RIFF").getBytes());h.putInt(rf64?-1:36+count);h.put("WAVE".getBytes());
  if(rf64){h.put("ds64".getBytes());h.putInt(28);h.putLong(72+count);h.putLong(count);h.putLong(count/2);h.putInt(0);}
  h.put("fmt ".getBytes());h.putInt(16);h.putShort((short)1);h.putShort((short)1);h.putInt(22050);h.putInt(44100);h.putShort((short)2);h.putShort((short)16);h.put("data".getBytes());h.putInt(rf64?-1:count);
  byte[] out=Arrays.copyOf(h.array(),h.capacity()+count);for(int i=0;i<count;++i)out[h.capacity()+i]=(byte)(i*13);return out;
 }
 private static String sha(byte[] data)throws Exception{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(data));}
 public static void main(String[]args)throws Exception {
  System.setProperty("goplanska.music.allowLocalHttp","true");
  HttpServer server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);server.setExecutor(Executors.newCachedThreadPool(r->{Thread t=new Thread(r);t.setDaemon(true);return t;}));
  Map<String,byte[]> tracks=new HashMap<>();byte[] normal=wav(44100*6,false),rf64=wav(44100*6,true);String normalSha=sha(normal),rfSha=sha(rf64);
  tracks.put(normalSha,normal);tracks.put(rfSha,rf64);tracks.put("0".repeat(64),normal);
  AtomicInteger ranges=new AtomicInteger();AtomicBoolean auth=new AtomicBoolean();AtomicLong rangeBegin=new AtomicLong();
  server.createContext("/",exchange->{
   String path=exchange.getRequestURI().getPath();String key=path.substring(path.lastIndexOf('/')+1).replace(".wav","");byte[] track=tracks.get(key);
   if(track==null){exchange.sendResponseHeaders(404,-1);exchange.close();return;}
   auth.set("Bearer fixture-only".equals(exchange.getRequestHeaders().getFirst("Authorization")));
   String range=exchange.getRequestHeaders().getFirst("Range");int start=0,end=track.length-1,status=200;
   if(range!=null){ranges.incrementAndGet();String[] limits=range.substring(6).split("-");start=Integer.parseInt(limits[0]);end=Integer.parseInt(limits[1]);rangeBegin.set(start);status=206;exchange.getResponseHeaders().add("Content-Range","bytes "+start+"-"+end+"/"+track.length);}
   if(path.startsWith("/bad-range/")&&range!=null)exchange.getResponseHeaders().set("Content-Range","bytes 0-"+end+"/"+track.length);
   exchange.sendResponseHeaders(status,end-start+1);
   try(OutputStream out=exchange.getResponseBody()){out.write(track,start,end-start+1);}
  });server.start();String base="http://127.0.0.1:"+server.getAddress().getPort();
  try {
   for(boolean large:new boolean[]{false,true}) {
    byte[] data=large?rf64:normal;String hash=large?rfSha:normalSha;int header=large?80:44;
    ScooterHttpAudioStream stream=ScooterHttpAudioStream.open(base+"/"+hash+".wav","fixture-only",hash).get(2,TimeUnit.SECONDS);
    check(stream.getFormat().getFrameSize()==2,"validPCMformat");
    stream.prepare(2.1).get(2,TimeUnit.SECONDS);int offset=(int)(2.1*22050)*2;
    check(rangeBegin.get()==header+offset,"byte-range starts at precise PCM frame");
    check(Math.abs(stream.positionSeconds()-2.1)<1/22050.0,"precise start timestamp");
    ByteBuffer chunk=stream.read(1024);byte[] heard=new byte[chunk.remaining()];chunk.get(heard);
    check(Arrays.equals(heard,Arrays.copyOfRange(data,header+offset,header+offset+1024)),"late listener hears current section");
    check(auth.get(),"seek retainsauth");check(heard.length==1024,"boundeddecodedbuffer");
    stream.prepare(4.0).get(2,TimeUnit.SECONDS);check(rangeBegin.get()==header+44100*4,"periodic drift correction seeksforward");
    stream.prepare(.5).get(2,TimeUnit.SECONDS);check(rangeBegin.get()==header+22050,"correctioncanseekbackward");
    stream.closeAsync();check(stream.read(1024).remaining()==0,"cancellationclosesreads");
   }
   ScooterHttpAudioStream catchup=ScooterHttpAudioStream.open(base+"/"+normalSha+".wav","fixture-only",normalSha).get(2,TimeUnit.SECONDS);
   AtomicInteger positions=new AtomicInteger();catchup.prepare(()->positions.incrementAndGet()==1?2.1:2.3).get(2,TimeUnit.SECONDS);
   check(Math.abs(catchup.positionSeconds()-2.3)<1.1/22050.0,"HTTP preparation latency catchup");
   ByteBuffer afterCatchup=catchup.read(1024);byte[] suffix=new byte[afterCatchup.remaining()];afterCatchup.get(suffix);int catchupOffset=(int)(2.3*22050)*2;
   check(Arrays.equals(suffix,Arrays.copyOfRange(normal,44+catchupOffset,44+catchupOffset+1024)),"first sample is shared current part after opening");catchup.close();
   ScooterHttpAudioStream full=ScooterHttpAudioStream.open(base+"/"+normalSha+".wav","",normalSha).get(2,TimeUnit.SECONDS);int bytes=0;ByteBuffer part;
   do{part=full.read(32768);bytes+=part.remaining();}while(part.hasRemaining());check(bytes==normal.length-44,"fullpassstillhashverifiedandcomplete");full.close();
   ScooterHttpAudioStream wrong=ScooterHttpAudioStream.open(base+"/"+"0".repeat(64)+".wav","","0".repeat(64)).get(2,TimeUnit.SECONDS);
   boolean failed=false;try{while(wrong.read(32768).hasRemaining()){};}catch(IOException expected){failed=true;}check(failed,"fullreadcontenthashmismatchrejects");
   ScooterHttpAudioStream badRange=ScooterHttpAudioStream.open(base+"/bad-range/"+normalSha+".wav","",normalSha).get(2,TimeUnit.SECONDS);
   failed=false;try{badRange.prepare(2).get(2,TimeUnit.SECONDS);}catch(ExecutionException expected){failed=true;}check(failed,"serverwrongrangeisrejected");
   failed=false;try{ScooterHttpAudioStream.open(base+"/arbitrary.wav","",normalSha).get(2,TimeUnit.SECONDS);}catch(ExecutionException expected){failed=true;}check(failed,"partialstreamrequirescontentaddressedSHApath");
   check(Math.abs(MusicTimeline.position(5000,1_000_000_000L,1_500_000_000L,200,false)-5.5)<.00001,"networkpreparationdelaycompensated");
   check(Math.abs(MusicTimeline.position(11500,0,0,200,true)-1.5)<.00001,"looppositionswrap");
   check(MusicTimeline.position(11500,0,0,200,false)==10,"nonloopsclampend");
   check(MusicTimeline.drift(9.9,.1,200,true)<.21,"loopboundarydoesnotfalsecorrect");
   check(MusicTimeline.drift(1,4,200,true)==3,"largedriftdetected");
   for(int ticks:new int[]{1,20,1754,720000})for(int cycle=0;cycle<5;++cycle){double duration=ticks/20.0,position=MusicTimeline.position((long)(duration*1000*cycle+50),0,0,ticks,true);check(position>=0&&position<duration,"wrappedpositionwithintrack");}
   System.out.println("{\"success\":true,\"assertions\":"+checks+",\"http_range_seek_verified\":true,\"riFF_and_RF64\":true,\"full_read_hash_retained\":true,\"partial_reads_not_full_hash_claimed\":true,\"native_game_launched\":false}");
  }finally{server.stop(0);}
 }
}
