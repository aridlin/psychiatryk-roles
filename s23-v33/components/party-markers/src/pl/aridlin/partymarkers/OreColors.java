package pl.aridlin.partymarkers;

import java.util.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.block.state.BlockState;

/** Ore mineral colors, with a texture accent fallback for other mods. Client render thread only. */
public final class OreColors {
 private static final Map<TextureAtlasSprite,Integer> accents=new WeakHashMap<>();
 private static final Map<String,Integer> minerals=Map.ofEntries(
  Map.entry("diamond",0xff36e5e5),Map.entry("emerald",0xff35dc72),
  Map.entry("gold",0xffffd339),Map.entry("iron",0xffd7b69a),
  Map.entry("copper",0xffed9258),Map.entry("redstone",0xffed3030),
  Map.entry("lapis",0xff477deb),Map.entry("coal",0xff50545b),
  Map.entry("quartz",0xffeee5db),Map.entry("ancient_debris",0xff947567),
  Map.entry("netherite",0xff947567),Map.entry("zinc",0xffb2d2b4),
  Map.entry("tin",0xffccd8df),Map.entry("silver",0xffc0e5ed),
  Map.entry("lead",0xff9981c7),Map.entry("nickel",0xffd8c47b),
  Map.entry("osmium",0xff8bb8ed),Map.entry("aluminum",0xffc8d0d5),
  Map.entry("aluminium",0xffc8d0d5),Map.entry("uranium",0xff81e449),
  Map.entry("sulfur",0xffffe55a),Map.entry("sulphur",0xffffe55a),
  Map.entry("fluorite",0xffe6a4e5),Map.entry("ruby",0xffef3c64),
  Map.entry("sapphire",0xff5279ef),Map.entry("amethyst",0xffc18aeb));
 public static int color(BlockState state){
  String path=BuiltInRegistries.BLOCK.getKey(state.getBlock()).getPath();
  // Token boundaries avoid accidentally matching names such as "golden" or "ironic".
  for(var entry:minerals.entrySet())if(("_"+path+"_").contains("_"+entry.getKey()+"_"))return entry.getValue();
  var sprite=Minecraft.getInstance().getBlockRenderer().getBlockModel(state).getParticleIcon();
  return accents.computeIfAbsent(sprite,OreColors::textureAccent);
 }
 private static int textureAccent(TextureAtlasSprite sprite){
  var image=sprite.contents().getOriginalImage();
  double[] weights=new double[64],rSum=new double[64],gSum=new double[64],bSum=new double[64];
  for(int y=0;y<Math.min(image.getHeight(),sprite.contents().height());y++)for(int x=0;x<Math.min(image.getWidth(),sprite.contents().width());x++){
   int p=image.getPixelRGBA(x,y);if((p>>>24)<128)continue;
   int r=p&255,g=(p>>>8)&255,b=(p>>>16)&255;
   double saturation=(Math.max(r,Math.max(g,b))-Math.min(r,Math.min(g,b)))/255.0;
   // Colored flecks outweigh neutral stone. Quantization collects similar flecks.
   double w=.015+saturation*saturation;int bin=(r>>6)*16+(g>>6)*4+(b>>6);
   weights[bin]+=w;rSum[bin]+=r*w;gSum[bin]+=g*w;bSum[bin]+=b*w;
  }
  int best=0;for(int i=1;i<64;i++)if(weights[i]>weights[best])best=i;
  if(weights[best]==0)return 0xffbbbbbb;
  int r=(int)(rSum[best]/weights[best]),g=(int)(gSum[best]/weights[best]),b=(int)(bSum[best]/weights[best]);
  return 0xff000000|(r<<16)|(g<<8)|b;
 }
}
