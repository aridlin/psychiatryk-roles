package pl.aridlin.psychiatrykroles.runtime.client;

import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.PacketDistributor;
import pl.aridlin.psychiatrykroles.runtime.*;
import java.util.*;

/** Composition surface: layout and content are server data; all clicks return IDs. */
public final class RuntimeScreen extends Screen {
 private final Schema.View view;private int page,perPage;private boolean pending;private long pendingSince;
 private record Box(int x,int y,int w,int h){}
 private record Prepared(Schema.Control control,Box box,int tint,Component label,ItemStack item,
                         ResourceLocation image,AssetClient.Size imageSize,Component loading){}
 private Prepared[] prepared=new Prepared[0];private String preparedRevision="";private Component footer=Component.empty();
 public RuntimeScreen(Schema.View view){super(Component.literal(view.title()));this.view=view;}
 private static double number(Map<String,String> p,String key,double fallback){try{return Double.parseDouble(p.get(key));}catch(Exception bad){return fallback;}}
 private static int color(Map<String,String> p,int fallback){try{return (int)Long.parseLong(p.get("color").substring(1),16);}catch(Exception bad){return fallback;}}
 private Box box(Schema.Control c,int ordinal){Map<String,String> p=c.props()==null?Map.of():c.props();
  if(p.containsKey("x")&&p.containsKey("y")&&p.containsKey("w")&&p.containsKey("h"))return new Box((int)(width*number(p,"x",0)),(int)(height*number(p,"y",0)),Math.max(1,(int)(width*number(p,"w",.5))),Math.max(1,(int)(height*number(p,"h",.1))));
  int w=Math.min(440,width-24);return new Box(Math.max(12,(width-w)/2),42+ordinal*30,w,22);
 }
 @Override protected void init(){
  perPage=Math.max(1,(height-100)/30);page=Math.clamp(page,0,Math.max(0,(view.controls().size()-1)/perPage));
  prepare();
  for(int i=0;i<perPage&&page*perPage+i<view.controls().size();i++){
   var c=prepared[i].control();var b=prepared[i].box();
   if(c.kind().equals("number")){int labelWidth=Math.max(40,b.w-110);var label=Button.builder(Component.literal(c.label()+": "+String.format(Locale.ROOT,"%.3f",c.value())),ignored->{}).bounds(b.x,b.y,labelWidth,b.h).build();label.active=false;addRenderableWidget(label);
    addRenderableWidget(Button.builder(Component.literal("−"),ignored->send(c,Double.toString(Math.clamp(c.value()-c.step(),c.min(),c.max())))).bounds(b.x+labelWidth+4,b.y,50,b.h).build());
    addRenderableWidget(Button.builder(Component.literal("+"),ignored->send(c,Double.toString(Math.clamp(c.value()+c.step(),c.min(),c.max())))).bounds(b.x+labelWidth+58,b.y,50,b.h).build());
   }else if(c.kind().equals("button")||c.kind().equals("toggle")){
    String label=c.label()+(c.kind().equals("toggle")?(c.value()==0?" [OFF]":" [ON]"):"");var button=Button.builder(Component.literal(label),ignored->send(c,c.kind().equals("toggle")?(c.value()==0?"1":"0"):"")).bounds(b.x,b.y,b.w,b.h).build();
    if(c.text()!=null&&!c.text().isBlank())button.setTooltip(Tooltip.create(Component.literal(c.text())));addRenderableWidget(button);
   }
  }
  int left=Math.max(12,(width-Math.min(440,width-24))/2),w=Math.min(440,width-24);
  var prev=Button.builder(Component.literal("←"),ignored->{page--;rebuildWidgets();}).bounds(left,height-42,50,20).build();prev.active=page>0;addRenderableWidget(prev);
  addRenderableWidget(Button.builder(Component.literal("Close"),ignored->onClose()).bounds(left+w/2-40,height-42,80,20).build());
  var next=Button.builder(Component.literal("→"),ignored->{page++;rebuildWidgets();}).bounds(left+w-50,height-42,50,20).build();next.active=(page+1)*perPage<view.controls().size();addRenderableWidget(next);
 }
 private void prepare(){
  int count=Math.max(0,Math.min(perPage,view.controls().size()-page*perPage));
  var next=new Prepared[count];
  for(int i=0;i<count;i++){
   var control=view.controls().get(page*perPage+i);var props=control.props()==null?Map.<String,String>of():control.props();
   ItemStack item=ItemStack.EMPTY;ResourceLocation image=null;AssetClient.Size imageSize=null;
   if(control.kind().equals("item")&&props.containsKey("item"))try{
    var id=ResourceLocation.parse(props.get("item"));
    if(BuiltInRegistries.ITEM.containsKey(id))item=new ItemStack(BuiltInRegistries.ITEM.get(id));
   }catch(RuntimeException ignored){}
   if(control.kind().equals("image")){
    image=AssetClient.texture(props.get("asset")).orElse(null);
    imageSize=AssetClient.dimensions(props.get("asset")).orElse(null);
   }
   next[i]=new Prepared(control,box(control,i),color(props,0xFFFFFFFF),Component.literal(control.label()),
       item,image,imageSize,Component.literal("Loading "+control.label()));
  }
  prepared=next;preparedRevision=AssetClient.revision();
  footer=Component.literal("Server scene · "+(page+1)+" / "+Math.max(1,(view.controls().size()+perPage-1)/perPage));
 }
 private void send(Schema.Control c,String value){if(pending||minecraft==null||minecraft.getConnection()==null||!minecraft.getConnection().hasChannel(RuntimeNetwork.Action.TYPE))return;pending=true;pendingSince=System.nanoTime();PacketDistributor.sendToServer(new RuntimeNetwork.Action(Schema.JSON.toJson(new Schema.Action(1,view.revision(),view.session(),view.menu(),c.id(),value))));if(c.kind().equals("button"))onClose();}
 @Override public void tick(){if(pending&&System.nanoTime()-pendingSince>2_000_000_000L)pending=false;}
 @Override public void render(GuiGraphics g,int mouseX,int mouseY,float partial){
  if(!preparedRevision.equals(AssetClient.revision()))prepare();
  renderBackground(g,mouseX,mouseY,partial);g.fill(Math.max(4,(width-464)/2),12,Math.min(width-4,(width+464)/2),height-12,0xE820242A);g.drawCenteredString(font,title,width/2,23,0xFFFFFF);
  for(var item:prepared){
   var c=item.control();var b=item.box();int tint=item.tint();
   switch(c.kind()){
    case "label","text","heading" -> g.drawString(font,item.label(),b.x,b.y+(c.kind().equals("heading")?3:6),tint);
    case "rect" -> g.fill(b.x,b.y,b.x+b.w,b.y+b.h,tint);
    case "progress" -> {g.fill(b.x,b.y,b.x+b.w,b.y+b.h,0xFF333941);double fraction=Math.clamp(c.value(),0,1);g.fill(b.x,b.y,b.x+(int)(b.w*fraction),b.y+b.h,tint);g.drawString(font,item.label(),b.x+4,b.y+4,0xFFFFFFFF);}
    case "item" -> {if(!item.item().isEmpty())g.renderItem(item.item(),b.x,b.y);g.drawString(font,item.label(),b.x+20,b.y+4,tint);}
    case "image" -> {if(item.image()!=null&&item.imageSize()!=null)g.blit(item.image(),b.x,b.y,0,0,b.w,b.h,item.imageSize().width(),item.imageSize().height());else g.drawString(font,item.loading(),b.x,b.y+4,tint);}
    default -> {} // Unknown future components are inert on older clients.
   }
  }
  super.render(g,mouseX,mouseY,partial);g.drawCenteredString(font,footer,width/2,height-17,0xAAB4BE);
 }
 @Override public boolean isPauseScreen(){return false;}
}
