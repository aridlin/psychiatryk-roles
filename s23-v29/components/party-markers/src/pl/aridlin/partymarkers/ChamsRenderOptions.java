package pl.aridlin.partymarkers;
import java.nio.file.*;
/** Client renderer preferences, polled without restarting the game. */
public final class ChamsRenderOptions {
 private static final Path FILE=Path.of("config/goplanska-chams-render.json");
 private static long nextRead;private static String previous;private static boolean torchHalftone=false;
 private ChamsRenderOptions(){}
 public static boolean markingTorchHalftone(){long now=System.nanoTime();if(now<nextRead)return torchHalftone;nextRead=now+500_000_000L;
  try{if(!Files.exists(FILE)){Files.createDirectories(FILE.getParent());Files.writeString(FILE,"{\"markingTorchHalftone\": false}\n");}String text=Files.readString(FILE);if(text.equals(previous))return torchHalftone;previous=text;var obj=com.google.gson.JsonParser.parseString(text).getAsJsonObject();var value=obj.get("markingTorchHalftone");if(value==null||!value.isJsonPrimitive()||!value.getAsJsonPrimitive().isBoolean())throw new IllegalArgumentException("markingTorchHalftone must be true or false");torchHalftone=value.getAsBoolean();}
  catch(Exception e){org.slf4j.LoggerFactory.getLogger("GoplanskaChams").warn("Renderer settings rejected; keeping previous setting",e);}return torchHalftone;
 }
}
