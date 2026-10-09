package pl.aridlin.psychiatrykroles.runtime.client;

import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderGuiEvent;
import net.neoforged.neoforge.client.event.ScreenEvent;

/** Visible join timeline without replacing Minecraft's connection screen or its network state. */
@EventBusSubscriber(modid="psychiatryk_runtime",value=Dist.CLIENT)
public final class JoinProgressOverlay {
    private JoinProgressOverlay() {}
    private static final String[] STAGES={"Połączenie","Pamięć podręczna","Pobieranie","Sprawdzanie","Gotowe"};
    private static volatile AssetClient.Progress snapshot;
    private static volatile long readyUntil;
    private static volatile long failureUntil;
    private static volatile boolean initialJoin;
    public static void accept(AssetClient.Progress next){
        snapshot=next;
        if(next==null||"disconnected".equals(next.phase()))initialJoin=false;
        else if("checking".equals(next.phase()))initialJoin=!next.previousAvailable();
        if(next!=null&&"ready".equals(next.phase()))readyUntil=System.nanoTime()+1_500_000_000L;
        if(next!=null&&"failed".equals(next.phase()))failureUntil=System.nanoTime()+5_000_000_000L;
    }
    @SubscribeEvent public static void onScreen(ScreenEvent.Render.Post event){
        String type=event.getScreen().getClass().getSimpleName();
        if(!type.equals("ConnectScreen")&&!type.equals("ReceivingLevelScreen")&&
           !type.equals("LevelLoadingScreen")&&!type.equals("GenericMessageScreen"))return;
        render(event.getGuiGraphics(),false);
    }
    @SubscribeEvent public static void onWorld(RenderGuiEvent.Post event){render(event.getGuiGraphics(),!initialJoin);}
    private static void render(GuiGraphics graphics,boolean world){
        var progress=snapshot;var mc=Minecraft.getInstance();
        if(progress==null||mc.getConnection()==null||"disconnected".equals(progress.phase()))return;
        boolean finished="ready".equals(progress.phase());
        if(finished&&System.nanoTime()>readyUntil)return;
        boolean failed="failed".equals(progress.phase());
        if(failed&&System.nanoTime()>failureUntil)return;
        if(world){
            int w=mc.getWindow().getGuiScaledWidth();
            graphics.fill(Math.max(4,w-234),6,w-6,40,0xDE14231E);
            graphics.drawString(mc.font,failed?"Zasoby nie zostały wczytane":finished?"Psychiatryk gotowy":"Aktualizacja zasobów Psychiatryk",Math.max(10,w-228),11,failed?0xFFDCAAA2:0xDBF1DC);
            graphics.drawString(mc.font,failed?"Sprawdź komunikat na czacie":progress.complete()+" / "+progress.total()+" gotowe",Math.max(10,w-228),24,0xA9CDB1);
            return;
        }
        int width=mc.getWindow().getGuiScaledWidth(),height=mc.getWindow().getGuiScaledHeight();
        graphics.fill(0,0,width,height,0xF00A1712);
        int left=Math.max(14,width/14),top=Math.max(16,height/12);
        int railWidth=Math.min(160,Math.max(100,width/4));
        int right=left+railWidth+22;
        int usable=Math.max(110,width-right-18);
        int phase=phaseIndex(progress.phase());
        graphics.drawString(mc.font,"PSYCHIATRYK  /  ŁĄCZENIE",left,top,0xD9EED5);
        graphics.fill(left,top+16,width-left,top+17,0xA17BBA8B);
        for(int i=0;i<STAGES.length;i++){
            int y=top+37+i*28;
            int color=i<phase?0xFF6BB48E:i==phase?0xFFDCECC4:0xFF60776B;
            if(i<STAGES.length-1)graphics.fill(left+5,y+9,left+7,y+29,i<phase?0xFF6BB48E:0xFF405E4F);
            graphics.fill(left,y,left+12,y+12,color);
            graphics.drawString(mc.font,STAGES[i],left+18,y+2,color);
        }
        graphics.fill(right-10,top+29,right-9,height-top,0xFF446451);
        String heading=switch(progress.phase()){
            case "checking" -> "Sprawdzamy zapisane pliki";
            case "transferring" -> "Pobieramy brakujące pliki";
            case "verifying" -> "Weryfikujemy pliki";
            case "preparing" -> "Przygotowujemy dodatki";
            case "ready" -> "Wszystko gotowe";
            case "failed" -> "Nie udało się wczytać zasobów";
            default -> "Przygotowujemy grę";
        };
        graphics.drawString(mc.font,heading,right,top+34,0xFFE3F1DD);
        String explanation=switch(progress.phase()){
            case "checking" -> "Najpierw używamy plików, które masz już na komputerze.";
            case "transferring" -> "Serwer wysyła tylko brakujące grafiki i ustawienia.";
            case "verifying" -> "Sprawdzamy zgodność plików przed uruchomieniem dodatków.";
            case "preparing" -> "Wczytujemy sprawdzone pliki do gry.";
            case "ready" -> "Dodatki są wczytane. Miłej gry!";
            case "failed" -> "Połączenie działa, ale dodatki nie zostały zatwierdzone.";
            default -> "Łączymy się z serwerem.";
        };
        int y=top+49;
        for(var line:mc.font.split(net.minecraft.network.chat.Component.literal(explanation),usable)){
            graphics.drawString(mc.font,line,right,y,0xFFACC8B3);y+=11;
        }
        int barY=y+13;
        graphics.fill(right,barY,right+usable,barY+9,0xFF294938);
        double ratio=progress.totalBytes()<=0?1:Math.clamp(progress.receivedBytes()/(double)progress.totalBytes(),0,1);
        graphics.fill(right,barY,right+(int)(usable*ratio),barY+9,0xFF83D3A4);
        graphics.drawString(mc.font,progress.complete()+" / "+progress.total()+" plików  ·  "+format(progress.receivedBytes())+" / "+format(progress.totalBytes()),right,barY+14,0xFFE0EDDD);
        int listY=barY+34;
        List<AssetClient.ProgressItem> entries=progress.items();
        if(!entries.isEmpty()){
            graphics.drawString(mc.font,"ZASOBY",right,listY,0xFF83D3A4);listY+=15;
            int maxRows=Math.max(1,Math.min(9,(height-listY-20)/13));
            int start=0;
            if(!progress.activeId().isEmpty())for(int i=0;i<entries.size();i++)if(entries.get(i).id().equals(progress.activeId()))start=Math.max(0,i-maxRows/2);
            start=Math.min(start,Math.max(0,entries.size()-maxRows));
            for(int i=start;i<entries.size()&&i<start+maxRows;i++){
                var item=entries.get(i);boolean active=item.id().equals(progress.activeId());
                int ink=active&&System.nanoTime()/350_000_000L%2==0?0xFFEDF8D2:0xFFB9D5C0;
                String name=item.id();if(name.length()>26)name=name.substring(0,25)+"…";
                graphics.drawString(mc.font,name,right,listY,ink);
                String state=switch(item.status()){
                    case "cached" -> "zapisany";case "verified" -> "sprawdzony";
                    case "transferring" -> Math.round(item.received()*100.0/item.bytes())+"%";
                    case "queued" -> "czeka";default -> item.status();
                };
                int stateX=right+usable-mc.font.width(state);
                if(stateX>right+110)graphics.drawString(mc.font,state,stateX,listY,0xFF88B69C);
                listY+=13;
            }
        }
    }
    private static int phaseIndex(String phase){return switch(phase){
        case "checking" -> 1;case "transferring" -> 2;case "verifying","preparing" -> 3;
        case "ready" -> 4;case "failed" -> 3;default -> 0;
    };}
    private static String format(long bytes){return bytes<1024?bytes+" B":bytes<1024*1024?String.format(java.util.Locale.ROOT,"%.1f KiB",bytes/1024.0):String.format(java.util.Locale.ROOT,"%.1f MiB",bytes/(1024.0*1024));}
}
