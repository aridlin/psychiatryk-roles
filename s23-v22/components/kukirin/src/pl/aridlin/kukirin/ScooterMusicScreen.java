package pl.aridlin.kukirin;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.components.*;
import net.minecraft.network.chat.Component;
public final class ScooterMusicScreen extends Screen {
 final ScooterMusicMenu.Catalog catalog;java.util.List<String> filtered;final java.util.List<Button> rows=new java.util.ArrayList<>();int page,pageSize;
 public ScooterMusicScreen(ScooterMusicMenu.Catalog catalog){super(Component.literal("Scooter music"));this.catalog=catalog;filtered=catalog.songs();}
 @Override protected void init(){pageSize=Math.clamp((height-130)/22,1,7);int left=width/2-150,top=Math.max(25,(height-(26+pageSize*22+52))/2);rows.clear();var search=new EditBox(font,left,top,300,20,Component.literal("Search songs"));search.setMaxLength(128);search.setHint(Component.literal("Search server songs…"));search.setResponder(query->{filtered=catalog.songs().stream().filter(s->s.toLowerCase(java.util.Locale.ROOT).contains(query.toLowerCase(java.util.Locale.ROOT))).toList();page=0;refresh();});addRenderableWidget(search);
 for(int i=0;i<pageSize;i++){final int slot=i;rows.add(addRenderableWidget(Button.builder(Component.empty(),b->{int index=page*pageSize+slot;if(index<filtered.size())send("play",filtered.get(index));}).bounds(left,top+26+i*22,300,20).build()));}
 addRenderableWidget(Button.builder(Component.literal("Previous"),b->{page=Math.max(0,page-1);refresh();}).bounds(left,top+26+pageSize*22,95,20).build());addRenderableWidget(Button.builder(Component.literal("Next"),b->{page=Math.min(Math.max(0,(filtered.size()-1)/pageSize),page+1);refresh();}).bounds(left+205,top+26+pageSize*22,95,20).build());
 addRenderableWidget(Button.builder(Component.literal("Stop"),b->send("stop","")).bounds(left,top+52+pageSize*22,95,20).build());var disc=addRenderableWidget(Button.builder(Component.literal("Play disc"),b->send("disc","")).bounds(left+102,top+52+pageSize*22,95,20).build());disc.active=catalog.disc();addRenderableWidget(Button.builder(Component.literal("Back"),b->onClose()).bounds(left+205,top+52+pageSize*22,95,20).build());refresh();}
 void refresh(){for(int i=0;i<rows.size();i++){int index=page*pageSize+i;var row=rows.get(i);row.visible=index<filtered.size();row.active=catalog.wav()&&row.visible;if(row.visible){String name=filtered.get(index);row.setMessage(Component.literal(font.plainSubstrByWidth(name,280)));row.setTooltip(Tooltip.create(Component.literal(name)));}}}
 static void send(String action,String song){net.neoforged.neoforge.network.PacketDistributor.sendToServer(new ScooterMusicMenu.Request(action,song));}
 @Override public void render(net.minecraft.client.gui.GuiGraphics g,int x,int y,float partial){renderBackground(g,x,y,partial);super.render(g,x,y,partial);g.drawCenteredString(font,title,width/2,Math.max(10,height/2-(26+pageSize*22+52)/2-20),0xffffa537);if(!catalog.wav())g.drawCenteredString(font,Component.literal("Add a note block at the smithing table to play WAVs."),width/2,height-18,0xffcccccc);}
 @Override public void onClose(){minecraft.setScreen(null);net.neoforged.neoforge.network.PacketDistributor.sendToServer(new ScooterStorageOpen());}
 @Override public boolean isPauseScreen(){return false;}
}
