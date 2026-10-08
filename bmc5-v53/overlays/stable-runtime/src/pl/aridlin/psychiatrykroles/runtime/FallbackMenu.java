package pl.aridlin.psychiatrykroles.runtime;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.*;
import net.minecraft.world.entity.player.*;
import net.minecraft.world.inventory.*;
import net.minecraft.world.item.*;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.component.ItemLore;
import java.util.*;

/** Standard protocol fallback for clients without the optional native screen. */
public final class FallbackMenu extends ChestMenu {
    final Schema.View view;final int page;
    public static void open(ServerPlayer p,Schema.View view){open(p,view,0);}
    private static void open(ServerPlayer p,Schema.View v,int page){p.openMenu(new SimpleMenuProvider((id,inv,who)->new FallbackMenu(id,inv,v,page),Component.literal(v.title())));}
    private FallbackMenu(int id,Inventory inv,Schema.View view,int page){this(id,inv,view,page,new SimpleContainer(54));}
    private FallbackMenu(int id,Inventory inv,Schema.View view,int page,SimpleContainer display){super(MenuType.GENERIC_9x6,id,inv,display,6);this.view=view;this.page=page;
        for(int i=0;i<45&&page*45+i<view.controls().size();i++){var c=view.controls().get(page*45+i);var item=new ItemStack(c.kind().equals("toggle")?Items.LEVER:c.kind().equals("number")?Items.COMPARATOR:Items.PAPER);item.set(DataComponents.CUSTOM_NAME,Component.literal(c.label()+(c.kind().equals("number")||c.kind().equals("toggle")?": "+c.value():"")));item.set(DataComponents.LORE,new ItemLore(List.of(Component.literal(c.text()==null?"":c.text()),Component.literal("LPM + / PPM −"))));display.setItem(i,item);}
        var prev=new ItemStack(Items.ARROW);prev.set(DataComponents.CUSTOM_NAME,Component.literal("Poprzednia strona"));display.setItem(45,prev);var next=prev.copy();next.set(DataComponents.CUSTOM_NAME,Component.literal("Następna strona"));display.setItem(53,next);
    }
    @Override public boolean stillValid(Player p){return true;}
    @Override public ItemStack quickMoveStack(Player p,int i){return ItemStack.EMPTY;}
    @Override public void clicked(int slot,int button,ClickType type,Player who){
        if(!(who instanceof ServerPlayer p))return;
        if(type!=ClickType.PICKUP||button<0||button>1||slot<0||slot>=54){broadcastFullState();return;}
        if(slot==45&&page>0){open(p,view,page-1);return;}if(slot==53&&(page+1)*45<view.controls().size()){open(p,view,page+1);return;}
        int index=page*45+slot;if(slot>=45||index>=view.controls().size())return;var c=view.controls().get(index);if(c.kind().equals("label"))return;
        String value="";if(c.kind().equals("toggle"))value=c.value()==0?"1":"0";else if(c.kind().equals("number"))value=Double.toString(Math.clamp(c.value()+(button==0?c.step():-c.step()),c.min(),c.max()));
        RuntimeServer.action(p,Schema.JSON.toJson(new Schema.Action(1,view.revision(),view.session(),view.menu(),c.id(),value)));
    }
}
