package pl.aridlin.kukirin;
import net.minecraft.client.gui.GuiGraphics;
/** Compact analog gauge with a readable numeric value and a server-owned parking warning. */
public final class ScooterDial {
 public static void draw(GuiGraphics g,net.minecraft.client.gui.Font font,Scooter scooter,boolean speed){
  boolean rental=ScooterRental.isRental(scooter);int w=100,h=(speed?89:0)+(rental?26:0),x=g.guiWidth()-w-9,y=g.guiHeight()-h-13;
  g.fill(x-4,y-4,x+w,y+h,0xc00b1016);
  if(speed){int cx=x+46,cy=y+40,r=34;double max=rental?30:Math.max(60,scooter.cruiseSpeed()*72),value=Math.max(0,scooter.speed(1)*72);
   for(int i=0;i<270;i++){double a=Math.toRadians(135+i);int px=cx+(int)Math.round(Math.cos(a)*r),py=cy+(int)Math.round(Math.sin(a)*r);g.fill(px,py,px+1,py+1,0xff75808d);}
   for(int i=0;i<=10;i++){double a=Math.toRadians(135+i*27);line(g,cx+(int)Math.round(Math.cos(a)*(r-5)),cy+(int)Math.round(Math.sin(a)*(r-5)),cx+(int)Math.round(Math.cos(a)*(r+1)),cy+(int)Math.round(Math.sin(a)*(r+1)),i>=8?0xffff7f37:0xffccd3dc);}
   double a=Math.toRadians(135+270*Math.min(1,value/max));line(g,cx,cy,cx+(int)Math.round(Math.cos(a)*26),cy+(int)Math.round(Math.sin(a)*26),0xffffa537);g.fill(cx-2,cy-2,cx+3,cy+3,0xffffa537);
   var text=String.format(java.util.Locale.ROOT,"%.0f km/h",value);g.drawCenteredString(font,text,cx,y+61,0xffffd28c);g.drawString(font,"0",x+13,y+58,0xffb7c0cb,false);g.drawString(font,""+(int)Math.round(max),x+74,y+58,0xffb7c0cb,false);
   g.drawCenteredString(font,net.minecraft.network.chat.Component.translatable(scooter.headlights()?"scooter.hud.lights":"scooter.hud.brand"),cx,y+76,0xffd1d5dc);y+=89;
  }
  if(rental){int percent=RentalPolicy.batteryPercent(ScooterRental.battery(scooter)),color=percent<=10?0xffff5555:percent<=25?0xffffbc35:0xff89dc65;g.drawString(font,net.minecraft.network.chat.Component.translatable("scooter.hud.battery",percent),x,y,color,true);g.fill(x,y+12,x+90,y+18,0xff343c45);g.fill(x+1,y+13,x+1+88*percent/100,y+17,color);
   if(scooter.rentalNoParking()){int ix=x+7,iy=y-((speed?89:0)+25);g.fill(x-4,iy-4,x+w,iy+20,0xe52b1017);for(int i=0;i<360;i+=6){double a=Math.toRadians(i);int px=ix+(int)Math.round(Math.cos(a)*9),py=iy+7+(int)Math.round(Math.sin(a)*9);g.fill(px,py,px+2,py+2,0xffff5555);}g.drawString(font,"P",ix-2,iy+3,0xffffffff,false);line(g,ix-6,iy+14,ix+7,iy,0xffff5555);g.drawString(font,net.minecraft.network.chat.Component.translatable("scooter.parking.warning"),x+21,iy+4,0xffff7777,false);}
  }
 }
 static void line(GuiGraphics g,int x0,int y0,int x1,int y1,int color){int dx=Math.abs(x1-x0),sx=x0<x1?1:-1,dy=-Math.abs(y1-y0),sy=y0<y1?1:-1,error=dx+dy;for(;;){g.fill(x0,y0,x0+1,y0+1,color);if(x0==x1&&y0==y1)break;int e=error*2;if(e>=dy){error+=dy;x0+=sx;}if(e<=dx){error+=dx;y0+=sy;}}}
 private ScooterDial(){}
}
