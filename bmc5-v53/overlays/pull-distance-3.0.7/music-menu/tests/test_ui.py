"""Execute the authored MusicScreen with small GUI/packet doubles; never launch Minecraft."""
from pathlib import Path
import shutil, subprocess, json, hashlib
r=Path(__file__).resolve().parent.parent;b=r/'build/headless';s=b/'src';c=b/'classes';b.mkdir(exist_ok=True);c.mkdir(exist_ok=True)
old=r.parent/'pause/build/headless/src'
shutil.copytree(old,s,dirs_exist_ok=True)
(s/'pl/aridlin/kukirin/PauseUiCheck.java').unlink(missing_ok=True)
def put(path,text):
 p=s/path;p.parent.mkdir(parents=True,exist_ok=True);p.write_text(text)
put('net/minecraft/client/gui/components/Button.java','''package net.minecraft.client.gui.components;
import net.minecraft.network.chat.Component;import net.minecraft.client.gui.GuiGraphics;
public class Button{public boolean active=true,visible=true;public Component message;public Tooltip tooltip;public OnPress action;protected int x,y,w,h;
 public interface OnPress{void onPress(Button b);}public interface CreateNarration{}public static final CreateNarration DEFAULT_NARRATION=new CreateNarration(){};
 public Button(){}protected Button(int x,int y,int w,int h,Component c,OnPress a,CreateNarration n){this.x=x;this.y=y;this.w=w;this.h=h;message=c;action=a;}
 public int getX(){return x;}public int getY(){return y;}public int getWidth(){return w;}public int getHeight(){return h;}
 public boolean isHoveredOrFocused(){return false;}public boolean isFocused(){return false;}
 public void click(){if(active&&visible&&action!=null)action.onPress(this);}public void setMessage(Component c){message=c;}public void setTooltip(Tooltip t){tooltip=t;}
 public void renderWidget(GuiGraphics g,int mx,int my,float p){g.drawString(new net.minecraft.client.gui.Font(),message.text(),x+4,y+4,0xffffffff,false);}
 public static Builder builder(Component c,OnPress a){return new Builder(c,a);}public static class Builder{Button b;
 public Builder(Component c,OnPress a){b=new Button(0,0,1,1,c,a,DEFAULT_NARRATION);}public Builder bounds(int x,int y,int w,int h){b.x=x;b.y=y;b.w=w;b.h=h;return this;}
 public Builder tooltip(Tooltip t){b.tooltip=t;return this;}public Button build(){return b;}}
}''')
put('net/minecraft/client/gui/components/EditBox.java','''package net.minecraft.client.gui.components;import net.minecraft.client.gui.Font;import net.minecraft.network.chat.Component;import java.util.function.Consumer;
public class EditBox extends Button{String value="";Consumer<String> responder=v->{};public EditBox(Font f,int x,int y,int w,int h,Component c){super(x,y,w,h,c,null,DEFAULT_NARRATION);}
public void setMaxLength(int n){}public void setHint(Component c){}public void setResponder(Consumer<String> c){responder=c;}public void setValue(String s){value=s;responder.accept(s);}public String getValue(){return value;}}
''')
put('net/minecraft/client/gui/components/AbstractSliderButton.java','''package net.minecraft.client.gui.components;import net.minecraft.network.chat.Component;public abstract class AbstractSliderButton extends Button{protected double value;
public AbstractSliderButton(int x,int y,int w,int h,Component c,double v){super(x,y,w,h,c,null,DEFAULT_NARRATION);value=v;}protected abstract void updateMessage();protected abstract void applyValue();
public void onClick(double x,double y){value=Math.clamp(x,0,1);updateMessage();applyValue();}public void onRelease(double x,double y){}}
''')
put('net/minecraft/client/gui/screens/Screen.java','''package net.minecraft.client.gui.screens;import net.minecraft.client.Minecraft;import net.minecraft.client.gui.Font;import net.minecraft.client.gui.GuiGraphics;import net.minecraft.client.gui.components.Button;import net.minecraft.network.chat.Component;import java.util.*;
public class Screen{protected Minecraft minecraft=Minecraft.getInstance();protected Font font=new Font();protected Component title;protected int width=800,height=600;public final List<Object> widgets=new ArrayList<>();
public Screen(Component c){title=c;}protected void init(){}public void headlessInit(){widgets.clear();init();}public void headlessResize(int w,int h){width=w;height=h;headlessInit();}
protected <T> T addRenderableWidget(T v){widgets.add(v);return v;}protected void setInitialFocus(Object v){}protected void renderBackground(GuiGraphics g,int x,int y,float t){}
public void render(GuiGraphics g,int x,int y,float t){for(Object v:widgets)if(v instanceof Button b&&b.visible)b.renderWidget(g,x,y,t);}public void onClose(){}public boolean isPauseScreen(){return false;}}
''')
put('net/minecraft/client/gui/Font.java','''package net.minecraft.client.gui;public class Font{public int width(String s){return s.length()*6;}public String plainSubstrByWidth(String s,int w){return s.substring(0,Math.min(s.length(),Math.max(0,w)/6));}}''')
put('net/minecraft/client/gui/GuiGraphics.java','''package net.minecraft.client.gui;import java.util.*;import net.minecraft.network.chat.Component;import net.minecraft.resources.ResourceLocation;import net.minecraft.world.item.ItemStack;
public class GuiGraphics{public record Blit(ResourceLocation texture,int x,int y,int w,int h,int sourceW,int sourceH,int textureW,int textureH){}public final List<Blit> blits=new ArrayList<>();public final List<String> strings=new ArrayList<>();public int fills,items;public Pose pose(){return new Pose();}public static class Pose{public void pushPose(){}public void popPose(){}public void translate(float x,float y,float z){}public void scale(float x,float y,float z){}}
public void fill(int x,int y,int x2,int y2,int color){if(x2<x||y2<y)throw new AssertionError("negative fill");fills++;}public void renderOutline(int x,int y,int w,int h,int color){}
public void renderItem(ItemStack s,int x,int y){items++;}public void blit(ResourceLocation t,int x,int y,int w,int h,float u,float v,int sw,int sh,int tw,int th){blits.add(new Blit(t,x,y,w,h,sw,sh,tw,th));}
public void drawCenteredString(Font f,Component c,int x,int y,int color){strings.add(c.text());}public void drawString(Font f,String t,int x,int y,int color,boolean shadow){strings.add(t);}public void drawWordWrap(Font f,Component c,int x,int y,int w,int color){strings.add(c.text());}}
''')
put('net/minecraft/resources/ResourceLocation.java','''package net.minecraft.resources;public record ResourceLocation(String value){}''')
put('net/minecraft/world/item/ItemStack.java','''package net.minecraft.world.item;public class ItemStack{public ItemStack(Object i){}}''')
put('net/minecraft/world/item/Items.java','''package net.minecraft.world.item;public class Items{public static final Object JUKEBOX=new Object();}''')
put('pl/aridlin/kukirin/YouTubeMusicScreen.java','''package pl.aridlin.kukirin;import net.minecraft.client.gui.screens.Screen;import net.minecraft.network.chat.Component;public class YouTubeMusicScreen extends Screen{public final ScooterMusicScreen library;public YouTubeMusicScreen(ScooterMusicScreen s){super(Component.literal("YouTube Music"));library=s;}public void onClose(){minecraft.setScreen(library);}}''')
put('pl/aridlin/kukirin/MusicNowPlaying.java','''package pl.aridlin.kukirin;import java.util.UUID;public class MusicNowPlaying{public record Info(UUID source,UUID session,String title,String artist,String album,int durationTicks,byte[] artwork){}public static String fallbackTitle(String n){return n.replace(".wav","").replace('_',' ');}}''')
put('pl/aridlin/kukirin/MusicNowPlayingClient.java','''package pl.aridlin.kukirin;import java.util.*;import net.minecraft.client.gui.GuiGraphics;import net.minecraft.resources.ResourceLocation;
public class MusicNowPlayingClient{public static final Map<UUID,MusicNowPlaying.Info> info=new HashMap<>();public static final Map<UUID,ResourceLocation> art=new HashMap<>();public static UUID requestedInfo,requestedArt;public static int panels;
public static MusicNowPlaying.Info menuMetadata(UUID s){requestedInfo=s;return info.get(s);}public static ResourceLocation menuArt(UUID s){requestedArt=s;return art.get(s);}
public static void minecraftPanel(GuiGraphics g,int x,int y,int w,int h){panels++;g.fill(x,y,x+w,y+h,0xffc6c6c6);}}
''')
put('pl/aridlin/kukirin/ScooterMusicMenu.java','''package pl.aridlin.kukirin;import java.util.*;import net.minecraft.network.protocol.common.custom.CustomPacketPayload;public class ScooterMusicMenu{
public record Catalog(List<String> songs,boolean wav,boolean disc,boolean loop,boolean paused,int volume,String currentSong,UUID source){}
public record Options(boolean autoplay,boolean shuffle,boolean paused,int volume,String currentSong,UUID source){}
public record Request(String action,String song)implements CustomPacketPayload{}}
''')
put('pl/aridlin/kukirin/MusicMenuUiCheck.java',r'''package pl.aridlin.kukirin;
import java.util.*;import net.minecraft.client.Minecraft;import net.minecraft.client.gui.GuiGraphics;import net.minecraft.client.gui.components.Button;import net.minecraft.resources.ResourceLocation;import net.neoforged.neoforge.network.PacketDistributor;
public class MusicMenuUiCheck{
 static int checks;static void check(boolean b,String label){checks++;if(!b)throw new AssertionError(label);}
 static ScooterMusicMenu.Request last(){return (ScooterMusicMenu.Request)PacketDistributor.sent.getLast();}
 static ScooterMusicScreen.IconButton icon(ScooterMusicScreen s,ScooterMusicScreen.Icon i){return (ScooterMusicScreen.IconButton)s.widgets.stream().filter(w->w instanceof ScooterMusicScreen.IconButton b&&b.icon==i).findFirst().orElseThrow();}
 static void bounds(Button b,int w,int h){check(b.getX()>=0&&b.getY()>=0&&b.getWidth()>0&&b.getHeight()>0&&b.getX()+b.getWidth()<=w&&b.getY()+b.getHeight()<=h,"widget outside "+w+"x"+h+" "+b.message.text());}
 static boolean overlap(Button a,Button b){return a.getX()<b.getX()+b.getWidth()&&a.getX()+a.getWidth()>b.getX()&&a.getY()<b.getY()+b.getHeight()&&a.getY()+a.getHeight()>b.getY();}
 static void noOverlap(ScooterMusicScreen s,int w,int h){List<Button> widgets=s.widgets.stream().filter(v->v instanceof Button b&&b.visible).map(v->(Button)v).toList();for(Button a:widgets)bounds(a,w,h);
  for(int a=0;a<widgets.size();a++)for(int b=a+1;b<widgets.size();b++)check(!overlap(widgets.get(a),widgets.get(b)),"overlap "+w+"x"+h+": "+widgets.get(a).message.text()+" / "+widgets.get(b).message.text());
  check(s.layout.footerY()+22<=h-12,"panel clears footer status "+w+"x"+h+" footer="+s.layout.footerY());check(s.layout.artY()+s.layout.artSize()<s.layout.searchY()||s.layout.wide(),"narrow cover clears search");
  if(s.layout.wide())check(s.layout.artX()+s.layout.artSize()<s.layout.likeX(),"cover clears Like");
 }
 public static void main(String[]args){
  UUID source=UUID.randomUUID(),other=UUID.randomUUID();List<String> songs=new ArrayList<>();for(int i=0;i<100;i++)songs.add(String.format("song_%03d.wav",i));songs.add("bad.exe");
  ScooterMusicScreen s=new ScooterMusicScreen(new ScooterMusicMenu.Catalog(songs,true,true,true,false,40,"song_007.wav",source),()->{});Minecraft.getInstance().setScreen(s);
  for(int[] size:new int[][]{{320,240},{360,240},{427,240},{519,240},{520,240},{640,360},{854,480},{960,540},{1920,1080}}){s.headlessResize(size[0],size[1]);noOverlap(s,size[0],size[1]);}
  check(s.filtered.size()==100,"invalid files filtered");check(s.currentSong.equals("song_007.wav")&&s.currentSource.equals(source),"authoritative current source captured");
  s.headlessResize(640,360);check(!s.previousPageButton.active&&s.nextPageButton.active,"first-page icons");s.nextPageButton.click();check(s.page==1&&s.previousPageButton.active,"next page");s.previousPageButton.click();check(s.page==0,"previous page");
  s.likeButton.click();check(ScooterMusicFavorites.contains("song_007.wav")&&s.likeButton.selected,"Like current song rather than first row");check(!ScooterMusicFavorites.contains("song_000.wav"),"Like doesn't select unrelated row");
  s.favoritesButton.click();check(s.favoritesOnly&&s.filtered.equals(List.of("song_007.wav")),"favorites filter");s.favoritesButton.click();s.stars.getFirst().click();check(ScooterMusicFavorites.contains("song_000.wav"),"row favorite preserved");
  s.search.setValue("007");check(s.filtered.equals(List.of("song_007.wav")),"search retained");s.rows.getFirst().click();check(last().action().equals("play")&&last().song().equals("song_007.wav"),"filtered play action");
  s.previousSongButton.click();check(last().action().equals("previous"),"Previous song callback");s.skipButton.click();check(last().action().equals("skip"),"Next song callback");
  s.pauseButton.click();check(last().action().equals("pause")&&!s.pauseButton.active&&!s.paused,"pause awaits acknowledgment");s.likeButton.click();check(!s.pauseButton.active,"favorite callback cannot defeat pending pause");
  s.options(new ScooterMusicMenu.Options(true,true,true,55,"song_007.wav",source));check(s.paused&&s.pauseButton.active&&((ScooterMusicScreen.IconButton)s.pauseButton).icon==ScooterMusicScreen.Icon.PLAY,"Pause acknowledgment shows Resume icon");
  check(s.searchTerm.equals("007")&&s.autoplay&&s.shuffle,"pause preserves picker state");s.pauseButton.click();check(last().action().equals("resume"),"Resume callback");
  s.options(new ScooterMusicMenu.Options(true,true,false,55,"song_007.wav",source));check(!s.paused&&((ScooterMusicScreen.IconButton)s.pauseButton).icon==ScooterMusicScreen.Icon.PAUSE,"Resume acknowledgment");
  int count=PacketDistributor.sent.size();s.volumeSlider.onClick(.37,0);check(PacketDistributor.sent.size()==count,"volume drag sends nothing");s.options(new ScooterMusicMenu.Options(true,true,false,60,"song_007.wav",source));check(s.volumeSlider.message.text().equals("Volume: 37%"),"server snapshot does not fight drag");s.volumeSlider.onRelease(.37,0);check(PacketDistributor.sent.size()==count+1&&last().action().equals("volume")&&last().song().equals("37"),"volume release exactly one packet");
  icon(s,ScooterMusicScreen.Icon.STOP).click();check(last().action().equals("stop"),"Stop callback");s.loopButton.click();check(last().action().equals("loop"),"Repeat callback");s.autoplayButton.click();check(last().action().equals("autoplay")&&!s.autoplayButton.active,"Autoplay request waits ack");s.options(new ScooterMusicMenu.Options(false,true,false,37,"song_007.wav",source));s.shuffleButton.click();check(last().action().equals("shuffle"),"Shuffle callback");
  s.options(new ScooterMusicMenu.Options(true,true,false,37,"song_008.wav",other));check(s.currentSong.equals("song_008.wav")&&s.currentSource.equals(other),"Options update Now Playing source");
  MusicNowPlayingClient.info.put(source,new MusicNowPlaying.Info(source,UUID.randomUUID(),"Wrong Source","Other artist","",200,new byte[0]));MusicNowPlayingClient.info.put(other,new MusicNowPlaying.Info(other,UUID.randomUUID(),"Exact Song","Exact Artist","Exact Album",200,new byte[0]));
  ResourceLocation cover=new ResourceLocation("exact-cover");MusicNowPlayingClient.art.put(other,cover);GuiGraphics g=new GuiGraphics();s.render(g,0,0,0);
  check(MusicNowPlayingClient.requestedInfo.equals(other)&&MusicNowPlayingClient.requestedArt.equals(other),"cover lookup exact source UUID");check(g.blits.size()==1&&g.blits.getFirst().texture().equals(cover),"correct cover drawn");check(g.blits.getFirst().sourceW()==64&&g.blits.getFirst().sourceH()==64&&g.blits.getFirst().textureW()==64,"full cover UV scales, doesn't repeat");
  check(g.strings.contains("Exact Song")&&g.strings.contains("Exact Artist")&&!g.strings.contains("Wrong Source"),"metadata title and artist shown");check(MusicNowPlayingClient.panels>0&&g.fills>100,"gray panel and pixel glyphs rendered");check(!g.strings.contains("Pause for everyone"),"icon button does not draw text label");check(s.pauseButton.tooltip!=null&&!s.pauseButton.message.text().isBlank(),"icon retains tooltip/narration");
  for(ScooterMusicScreen.Icon i:ScooterMusicScreen.Icon.values()){check(i.pixels.length==7,"pixel icon height");for(String row:i.pixels)check(row.length()==7&&row.matches("[01]+"),"pixel icon mask");}
  s.importButton.click();check(Minecraft.getInstance().screen instanceof YouTubeMusicScreen y&&y.library==s,"import preserves library context");Minecraft.getInstance().screen.onClose();check(Minecraft.getInstance().screen==s,"Back restores same screen");
  s.updateCatalog(new ScooterMusicMenu.Catalog(List.of("first.wav","imported.wav"),true,false,true,true,22,"imported.wav",source),true,"imported.wav");check(s.searchTerm.isEmpty()&&!s.favoritesOnly&&s.filtered.contains("imported.wav"),"new import visible without stale filter");check(s.currentSong.equals("imported.wav")&&s.currentSource.equals(source)&&s.paused&&s.volume==22,"catalog authoritative state");
  s.updateCatalog(new ScooterMusicMenu.Catalog(List.of("first.wav"),false,true,true,true,22,"first.wav",source),false);check(!s.rows.getFirst().active&&!s.pauseButton.active&&!s.skipButton.active&&!s.previousSongButton.active&&!s.volumeSlider.active&&!s.importButton.active&&s.discButton.active,"source permissions preserved");
  s.updateCatalog(new ScooterMusicMenu.Catalog(List.of(),true,false,true,false,100,"",null),false);check(!s.likeButton.active&&!s.nextPageButton.active,"empty source safe");GuiGraphics fallback=new GuiGraphics();s.render(fallback,0,0,0);check(fallback.items==1&&fallback.blits.isEmpty()&&fallback.strings.contains("No song playing"),"native jukebox fallback no song");
  s.headlessResize(320,240);noOverlap(s,320,240);
  System.out.println("{\"success\":true,\"checks\":"+checks+",\"authored_ui_with_gui_packet_doubles\":true,\"native_game_launched\":false,\"music_menu_ui_verified\":true}");
 }
}''')
sources=list(s.rglob('*.java'))+[r/'src/pl/aridlin/kukirin/ScooterMusicScreen.java']
x=subprocess.run(['nice','-n','19','javac','-J-Xmx128m','--release','21','-proc:none','-d',str(c),*map(str,sources)],capture_output=True,text=True);(b/'compile.log').write_text(x.stdout+x.stderr)
if x.returncode:print(x.stderr[-3000:]);raise SystemExit(x.returncode)
x=subprocess.run(['nice','-n','19','java','-Xmx128m','-cp',str(c),'pl.aridlin.kukirin.MusicMenuUiCheck'],capture_output=True,text=True);(b/'test.log').write_text(x.stdout+x.stderr)
if x.returncode:print(x.stderr[-3000:]);raise SystemExit(x.returncode)
data=json.loads(x.stdout);data['source_sha256']=hashlib.sha256((r/'src/pl/aridlin/kukirin/ScooterMusicScreen.java').read_bytes()).hexdigest();data['fixture_sha256']={str(p.relative_to(s)):hashlib.sha256(p.read_bytes()).hexdigest() for p in s.rglob('*.java')};(r/'build/ui-test.json').write_text(json.dumps(data,indent=2)+'\n');print(x.stdout)
