package pl.aridlin.kukirin;
import javax.sound.sampled.*;
import java.io.*;
/** Decode once off the render thread; mono is required for OpenAL spatialisation. */
public final class ScooterWav {
 public record Pcm(AudioFormat format,byte[] samples){}
 public static Pcm decode(byte[] wav)throws Exception{
  try(var input=AudioSystem.getAudioInputStream(new ByteArrayInputStream(wav))){var f=input.getFormat();int channels=f.getChannels();float rate=f.getSampleRate();if(channels<1||channels>2||rate<8000||rate>96000)throw new IOException("Use mono/stereo WAV at 8–96 kHz");var target=new AudioFormat(AudioFormat.Encoding.PCM_SIGNED,rate,16,channels,channels*2,rate,false);
   try(var pcm=AudioSystem.getAudioInputStream(target,input)){byte[] raw=pcm.readNBytes(ScooterMusic.MAX_BYTES+1);if(raw.length>ScooterMusic.MAX_BYTES)throw new IOException("Decoded audio exceeds 16 MiB");if(channels==1)return new Pcm(target,raw);byte[] mono=new byte[raw.length/4*2];for(int i=0,j=0;i+3<raw.length;i+=4,j+=2){int a=(short)((raw[i]&255)|(raw[i+1]<<8)),b=(short)((raw[i+2]&255)|(raw[i+3]<<8));int sample=(a+b)/2;mono[j]=(byte)sample;mono[j+1]=(byte)(sample>>8);}return new Pcm(new AudioFormat(rate,16,1,true,false),mono);}
  }
 }
}
