package pl.aridlin.kukirin;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.function.DoubleSupplier;
import javax.sound.sampled.AudioFormat;
import net.minecraft.client.sounds.AudioStream;
/** Installed OGG assets: seek in bounded decoded chunks, catching up preparation latency. */
final class ScooterAudioSeekStream implements AudioStream {
 private final AudioStream stream;
 private final double position;
 private ScooterAudioSeekStream(AudioStream stream,double position){this.stream=stream;this.position=position;}
 double positionSeconds(){return position;}
 static ScooterAudioSeekStream at(AudioStream stream,DoubleSupplier seconds)throws IOException {
  AudioFormat format=stream.getFormat();int frame=format.getFrameSize();long discarded=0;
  // Catch up decoding/open latency. Each read is bounded; this runs on an I/O worker.
  for(int pass=0;pass<3;pass++) {
   long target=(long)(Math.max(0,seconds.getAsDouble())*format.getFrameRate())*frame;
   if(target<=discarded)break;
   while(discarded<target){ByteBuffer data=stream.read((int)Math.min(target-discarded,32768));int read=data.remaining();if(read==0)return new ScooterAudioSeekStream(stream,discarded/(double)frame/format.getFrameRate());discarded+=read;}
  }
  return new ScooterAudioSeekStream(stream,discarded/(double)frame/format.getFrameRate());
 }
 public AudioFormat getFormat(){return stream.getFormat();}
 public ByteBuffer read(int bytes)throws IOException{return stream.read(bytes);}
 public void close()throws IOException{stream.close();}
}
