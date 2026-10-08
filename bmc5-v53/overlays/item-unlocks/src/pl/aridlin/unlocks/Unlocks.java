package pl.aridlin.unlocks;

import com.mojang.brigadier.arguments.StringArgumentType;
import net.minecraft.commands.Commands;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.*;
import net.minecraft.world.entity.player.*;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.inventory.*;
import net.minecraft.world.item.*;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.component.ItemLore;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import java.time.*;
import java.util.*;

@Mod("psychiatryk_unlocks")
public final class Unlocks {
    private String applied="";private boolean reloading;private int ticks;
    public Unlocks(){NeoForge.EVENT_BUS.register(this);}
    @SubscribeEvent public void started(ServerStartedEvent e)throws Exception{
        Rules.ensureSaved();applied=Rules.fingerprint();StatusPublisher.publish();
        org.slf4j.LoggerFactory.getLogger("PsychiatrykUnlocks").info("Item calendar active: {} rules, Europe/Warsaw; locked={}",Rules.all().size(),applied);
    }
    @SubscribeEvent public void tick(ServerTickEvent.Post e){
        if(++ticks%1200==0)StatusPublisher.publish();
        if(ticks%20!=0||reloading)return;
        String desired=Rules.fingerprint();if(desired.equals(applied))return;
        reloading=true;MinecraftServer s=e.getServer();
        s.reloadResources(s.getPackRepository().getSelectedIds()).whenComplete((unused,error)->s.execute(()->{
            reloading=false;
            if(error==null){applied=desired;s.getPlayerList().broadcastSystemMessage(Component.literal("Zaktualizowano odblokowania przedmiotów. /itemlocks — panel administratora."),false);}
            else org.slf4j.LoggerFactory.getLogger("PsychiatrykUnlocks").error("Recipe refresh failed; will retry",error);
        }));
    }
    @SubscribeEvent public void commands(RegisterCommandsEvent e){
        e.getDispatcher().register(Commands.literal("itemlocks").requires(s->s.hasPermission(2))
          .executes(c->{open(c.getSource().getPlayerOrException(),"",0,null);return 1;})
          .then(Commands.literal("status").executes(c->{for(var entry:Rules.all().entrySet()){String id=entry.getKey();boolean locked=Rules.locked(id);boolean recipe=c.getSource().getServer().getRecipeManager().getRecipes().stream().anyMatch(r->BuiltInRegistries.ITEM.getKey(r.value().getResultItem(c.getSource().getServer().registryAccess()).getItem()).toString().equals(id));c.getSource().sendSuccess(()->Component.literal(id+" locked="+locked+" recipe="+recipe+" unlock="+entry.getValue().unlockAt()),false);}return 1;}))
          .then(Commands.literal("held").executes(c->{var p=c.getSource().getPlayerOrException();if(p.getMainHandItem().isEmpty())return 0;open(p,"",0,BuiltInRegistries.ITEM.getKey(p.getMainHandItem().getItem()).toString());return 1;}))
          .then(Commands.literal("search").then(Commands.argument("text",StringArgumentType.greedyString()).executes(c->{open(c.getSource().getPlayerOrException(),StringArgumentType.getString(c,"text"),0,null);return 1;}))));
    }
    private static boolean denied(Player p,ItemStack stack){return p instanceof ServerPlayer&&!p.isCreative()&&!stack.isEmpty()&&Rules.locked(BuiltInRegistries.ITEM.getKey(stack.getItem()).toString());}
    private static void message(Player p){p.displayClientMessage(Component.literal("Ten przedmiot / zawód jest jeszcze zablokowany. Terminy: info.goplanska.pl"),true);}
    @SubscribeEvent(priority=EventPriority.HIGHEST) public void block(PlayerInteractEvent.RightClickBlock e){
        if(!(e.getEntity() instanceof ServerPlayer p)||p.isCreative())return;
        if(denied(p,e.getItemStack())||Rules.locked(BuiltInRegistries.BLOCK.getKey(e.getLevel().getBlockState(e.getPos()).getBlock()).toString())){e.setCanceled(true);e.setCancellationResult(InteractionResult.FAIL);message(p);}
    }
    @SubscribeEvent public void item(PlayerInteractEvent.RightClickItem e){if(denied(e.getEntity(),e.getItemStack())){e.setCanceled(true);e.setCancellationResult(InteractionResult.FAIL);message(e.getEntity());}}
    @SubscribeEvent public void entity(PlayerInteractEvent.EntityInteract e){
        if(!(e.getEntity() instanceof ServerPlayer p)||p.isCreative())return;
        boolean locked=denied(p,e.getItemStack());
        if(e.getTarget() instanceof Villager v){String profession=BuiltInRegistries.VILLAGER_PROFESSION.getKey(v.getVillagerData().getProfession()).toString();locked|=Rules.all().values().stream().anyMatch(r->profession.equals(r.profession())&&r.locked(Instant.now()));}
        if(locked){e.setCanceled(true);e.setCancellationResult(InteractionResult.FAIL);message(p);}
    }
    @SubscribeEvent public void attack(net.neoforged.neoforge.event.entity.player.AttackEntityEvent e){if(denied(e.getEntity(),e.getEntity().getMainHandItem())){e.setCanceled(true);message(e.getEntity());}}
    @SubscribeEvent public void mine(PlayerInteractEvent.LeftClickBlock e){if(denied(e.getEntity(),e.getItemStack())){e.setCanceled(true);message(e.getEntity());}}
    private static void open(ServerPlayer p,String search,int page,String selected){p.openMenu(new SimpleMenuProvider((id,inv,who)->new Menu(id,inv,search,page,selected),Component.literal(selected==null?"Blokady przedmiotów":"Ustaw odblokowanie")));}
    private static ItemStack icon(Item item,String name,String... lore){var s=new ItemStack(item);s.set(DataComponents.CUSTOM_NAME,Component.literal(name));s.set(DataComponents.LORE,new ItemLore(Arrays.stream(lore).map(Component::literal).map(c->(Component)c).toList()));return s;}
    private static final class Menu extends ChestMenu {
        final String search,selected;final int page;final List<String> ids;final SimpleContainer display;
        Menu(int id,Inventory inv,String search,int page,String selected){this(id,inv,search,page,selected,new SimpleContainer(54));}
        Menu(int id,Inventory inv,String search,int page,String selected,SimpleContainer display){
            super(MenuType.GENERIC_9x6,id,inv,display,6);this.display=display;this.search=search;this.page=page;this.selected=selected;
            ids=BuiltInRegistries.ITEM.keySet().stream().map(Object::toString).filter(k->!k.equals("minecraft:air")&&k.contains(search.toLowerCase(Locale.ROOT))).sorted(Comparator.<String,Boolean>comparing(k->!Rules.all().containsKey(k)).thenComparing(k->k)).toList();
            if(selected==null){for(int i=0;i<45&&page*45+i<ids.size();i++){String k=ids.get(page*45+i);var r=Rules.all().get(k);display.setItem(i,icon(BuiltInRegistries.ITEM.get(net.minecraft.resources.ResourceLocation.parse(k)),k,r==null?"Bez blokady":r.unlockAt(),"Kliknij, aby zmienić"));}
                display.setItem(45,icon(Items.ARROW,"Poprzednia strona"));display.setItem(49,icon(Items.PAPER,"Strona "+(page+1),"/itemlocks search <fragment ID>","/itemlocks held — przedmiot w ręce"));display.setItem(53,icon(Items.ARROW,"Następna strona"));
            }else{display.setItem(4,icon(BuiltInRegistries.ITEM.get(net.minecraft.resources.ResourceLocation.parse(selected)),selected));display.setItem(19,icon(Items.LIME_DYE,"Odblokuj teraz"));display.setItem(21,icon(Items.BARRIER,"Zablokuj bez terminu"));display.setItem(23,icon(Items.CLOCK,"Odblokuj jutro (00:00 PL)"));display.setItem(25,icon(Items.CLOCK,"Odblokuj za 2 dni (00:00 PL)"));display.setItem(31,icon(Items.PAPER,"Termin +1 dzień","Przesuń zapisany termin o dzień"));display.setItem(32,icon(Items.PAPER,"Termin −1 dzień","Przesuń zapisany termin o dzień"));display.setItem(40,icon(Items.ENDER_EYE,"1 listopada 2026 (00:00 PL)"));display.setItem(45,icon(Items.ARROW,"Wróć"));}
        }
        @Override public boolean stillValid(Player p){return p.hasPermissions(2);}
        @Override public ItemStack quickMoveStack(Player p,int slot){return ItemStack.EMPTY;}
        @Override public void clicked(int slot,int button,ClickType type,Player who){
            if(!(who instanceof ServerPlayer p))return;
            if(!p.hasPermissions(2)){p.closeContainer();return;}
            // Never let menu icons enter inventories: deny shift, swap, drag, throw and double-click.
            if(type!=ClickType.PICKUP||button!=0||slot<0||slot>=54){broadcastFullState();return;}
            if(selected==null){if(slot<45&&page*45+slot<ids.size())open(p,search,page,ids.get(page*45+slot));else if(slot==45&&page>0)open(p,search,page-1,null);else if(slot==53&&(page+1)*45<ids.size())open(p,search,page+1,null);return;}
            if(slot==45){open(p,search,page,null);return;}
            LocalDate today=LocalDate.now(Rules.ZONE);String value=switch(slot){case 19->"now";case 21->"never";case 23->today.plusDays(1).toString();case 25->today.plusDays(2).toString();case 40->"2026-11-01";default->null;};
            if(slot==31||slot==32){var r=Rules.all().get(selected);LocalDate date=r==null||r.unlockAt().equals("now")||r.unlockAt().equals("never")?today:LocalDate.parse(r.unlockAt());value=date.plusDays(slot==31?1:-1).toString();}
            if(value==null)return;
            try{Rules.set(selected,value);StatusPublisher.publish();p.displayClientMessage(Component.literal(selected+" → "+value+" (Europe/Warsaw)"),false);open(p,search,page,selected);}catch(Exception ex){p.displayClientMessage(Component.literal("Nie zapisano zmiany: "+ex.getMessage()),false);}
        }
    }
}
