package pl.aridlin.psychiatrykroles.runtime;

import com.mojang.brigadier.arguments.StringArgumentType;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.NonNullList;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundUpdateRecipesPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.*;
import net.minecraft.world.item.component.*;
import net.minecraft.world.item.crafting.*;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import java.nio.file.*;
import java.time.*;
import java.util.*;

public final class RuntimeServer {
    public static final Path FILE=Path.of("config/psychiatryk-runtime.json");
    private static volatile Schema.Config config=initial();
    private static volatile String revision=Schema.revision(config);
    private static String lastObservedFile;
    private record Session(String token,String revision,String menu,long expires){}
    private static final Map<UUID,Session> sessions=new HashMap<>();
    private static final Map<UUID,long[]> rates=new HashMap<>();
    private static final Map<UUID,Map<String,Long>> cooldowns=new HashMap<>();
    private static final Map<UUID,Boolean> lastSneak=new HashMap<>();
    private record PendingBreak(ServerLevel level,BlockPos pos,BlockState before){}
    private static final Map<UUID,List<PendingBreak>> pendingBreaks=new HashMap<>();
    private static final long ASSET_MENU_WAIT_NANOS=5_000_000_000L;
    static record PendingAssetMenu(String id,long deadline){}
    private static final Map<UUID,PendingAssetMenu> pendingAssetMenus=new HashMap<>();
    private static Schema.Config initial(){try{
        if(Files.exists(FILE)){Schema.require(Files.size(FILE)<=Schema.MAX_DOCUMENT,"Runtime config too large");return Schema.config(Files.readString(FILE));}
        try(var in=RuntimeServer.class.getResourceAsStream("/psychiatryk-runtime-default.json")){return Schema.config(new String(Objects.requireNonNull(in).readAllBytes(),java.nio.charset.StandardCharsets.UTF_8));}
    }catch(Exception e){System.err.println("[Psychiatryk Runtime] Invalid startup config; using bundled defaults and preserving the file: "+e.getMessage());
        try(var in=RuntimeServer.class.getResourceAsStream("/psychiatryk-runtime-default.json")){return Schema.config(new String(java.util.Objects.requireNonNull(in).readAllBytes(),java.nio.charset.StandardCharsets.UTF_8));}catch(Exception fatal){throw new IllegalStateException("Bundled runtime defaults invalid",fatal);}
    }}
    @SubscribeEvent public void started(ServerStartedEvent e)throws Exception{
        try{validateRegistries(config);}catch(Exception bad){System.err.println("[Psychiatryk Runtime] Invalid configured item registry; retaining file and using safe defaults");try(var in=RuntimeServer.class.getResourceAsStream("/psychiatryk-runtime-default.json")){config=Schema.config(new String(in.readAllBytes(),java.nio.charset.StandardCharsets.UTF_8));revision=Schema.revision(config);}}if(!Files.exists(FILE)){Files.createDirectories(FILE.getParent());Files.writeString(FILE,Schema.JSON.toJson(config)+"\n");}
        injectRecipes(e.getServer().getRecipeManager());lastObservedFile=fingerprint();System.out.println("[Psychiatryk Runtime] schema=1 revision="+revision+" menus="+config.menus().size()+" variants="+config.items().size());
    }
    private static void validateRegistries(Schema.Config c){for(var v:c.items().values()){
        Schema.require(BuiltInRegistries.ITEM.containsKey(ResourceLocation.parse(v.base()))&&!v.base().equals("minecraft:air"),"Unknown base item");
        for(String id:v.ingredients())Schema.require(BuiltInRegistries.ITEM.containsKey(ResourceLocation.parse(id))&&!id.equals("minecraft:air"),"Unknown ingredient");
        if(v.action().equals("effect"))Schema.require(BuiltInRegistries.MOB_EFFECT.containsKey(ResourceLocation.parse(v.target())),"Unknown effect");
    }for(var behavior:c.behaviors().values())for(var step:behavior.steps()){
        if(step.op().equals("give"))Schema.require(BuiltInRegistries.ITEM.containsKey(ResourceLocation.parse(step.target()))&&!step.target().equals("minecraft:air"),"Unknown given item");
        if(step.op().equals("effect"))Schema.require(BuiltInRegistries.MOB_EFFECT.containsKey(ResourceLocation.parse(step.target())),"Unknown behavior effect");
        if(step.op().equals("sound"))Schema.require(BuiltInRegistries.SOUND_EVENT.containsKey(ResourceLocation.parse(step.target())),"Unknown sound");
        if(step.op().equals("particle"))Schema.require(BuiltInRegistries.PARTICLE_TYPE.containsKey(ResourceLocation.parse(step.target())),"Unknown particle");
    }}
    public static void reload(MinecraftServer s)throws Exception{
        Schema.require(Files.size(FILE)<=Schema.MAX_DOCUMENT,"Runtime config too large");String source=Files.readString(FILE);Schema.Config candidate=Schema.config(source);validateRegistries(candidate);
        // Build every output before publishing the new revision; invalid config leaves the old one intact.
        for(var entry:candidate.items().entrySet())stack(entry.getKey(),entry.getValue());
        var previous=config;var oldRevision=revision;try{config=candidate;revision=Schema.revision(candidate);injectRecipes(s.getRecipeManager());}catch(RuntimeException ex){config=previous;revision=oldRevision;throw ex;}
        sessions.clear();pendingAssetMenus.clear();
        // Never reopen a menu the player has already closed. Client screens can be
        // replaced by any other GUI without the server receiving a close event.
        for(ServerPlayer p:s.getPlayerList().getPlayers())try{
            p.connection.send(new ClientboundUpdateRecipesPacket(s.getRecipeManager().getOrderedRecipes()));
        }catch(Exception sendFailure){System.err.println("[Psychiatryk Runtime] Recipe sync failed for "+p.getUUID()+": "+sendFailure.getMessage());}
        try{AssetServer.reload(s);}catch(Exception assetFailure){System.err.println("[Psychiatryk Runtime] Data reloaded but asset change rejected: "+assetFailure.getMessage());}
        lastObservedFile=HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(source.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
    }
    private static String fingerprint()throws Exception{
        if(!Files.exists(FILE))return "missing";
        Schema.require(Files.size(FILE)<=Schema.MAX_DOCUMENT,"Runtime config too large");
        return HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(FILE)));
    }
    @SubscribeEvent public void watch(ServerTickEvent.Post event){
        MinecraftServer server=event.getServer();
        if(server.getTickCount()%40==0)try{String observed=fingerprint();if(!observed.equals(lastObservedFile)){
            // Record failed revisions too. A later edit retries automatically.
            lastObservedFile=observed;try{reload(server);System.out.println("[Psychiatryk Runtime] Live data reload "+revision.substring(0,12));}
            catch(Exception invalid){System.err.println("[Psychiatryk Runtime] Live data rejected; previous revision active: "+invalid.getMessage());}
        }}catch(Exception read){System.err.println("[Psychiatryk Runtime] Cannot inspect data update: "+read.getMessage());}
        if(server.getTickCount()%20==0)expireAssetMenus(server,System.nanoTime());
        if(!pendingBreaks.isEmpty()){
            var due=Map.copyOf(pendingBreaks);pendingBreaks.clear();
            for(var entry:due.entrySet()){
                var player=server.getPlayerList().getPlayer(entry.getKey());if(player==null)continue;
                for(var pending:entry.getValue())if(pending.level().hasChunkAt(pending.pos())
                    &&!pending.level().getBlockState(pending.pos()).equals(pending.before()))hook("block_break",player);
            }
        }
    }
    public static void injectRecipes(RecipeManager manager){
        try{validateRegistries(config);}catch(Exception invalid){System.err.println("[Psychiatryk Runtime] Variant recipes rejected: "+invalid.getMessage());return;}
        var recipes=new ArrayList<RecipeHolder<?>>(manager.getRecipes().stream().filter(r->!r.id().getNamespace().equals("psychiatryk_runtime")).toList());
        for(var entry:config.items().entrySet()){var v=entry.getValue();if(v.ingredients().isEmpty()||blocked(v.base()))continue;
            var ingredients=NonNullList.<Ingredient>create();for(String id:v.ingredients())ingredients.add(Ingredient.of(BuiltInRegistries.ITEM.get(ResourceLocation.parse(id))));
            recipes.add(new RecipeHolder<>(ResourceLocation.fromNamespaceAndPath("psychiatryk_runtime",entry.getKey()),new ShapelessRecipe("psychiatryk_runtime",CraftingBookCategory.MISC,stack(entry.getKey(),v),ingredients)));
        }manager.replaceRecipes(recipes);
    }
    private static boolean blocked(String id){try{return (boolean)Class.forName("pl.aridlin.unlocks.Rules").getMethod("locked",String.class).invoke(null,id);}catch(ClassNotFoundException e){return false;}catch(ReflectiveOperationException e){throw new IllegalStateException("Cannot read item restriction",e);}}
    private static ItemStack stack(String id,Schema.Variant v){var stack=new ItemStack(BuiltInRegistries.ITEM.get(ResourceLocation.parse(v.base())),v.count());stack.set(DataComponents.CUSTOM_NAME,Component.literal(v.name()));stack.set(DataComponents.LORE,new ItemLore(v.lore().stream().map(Component::literal).map(c->(Component)c).toList()));var nbt=new CompoundTag();nbt.putString("psychiatrykRuntimeVariant",id);nbt.putString("psychiatrykRuntimeRevision",revision);nbt.putString("psychiatrykRuntimeSignature",VariantSignature.sign(id,v.base()));stack.set(DataComponents.CUSTOM_DATA,CustomData.of(nbt));return stack;}
    @SubscribeEvent public void commands(RegisterCommandsEvent e){
        e.getDispatcher().register(Commands.literal("psychiatryk").executes(c->{open(c.getSource().getPlayerOrException(),"main");return 1;}));
        e.getDispatcher().register(Commands.literal("psychiatrykruntime").requires(s->s.hasPermission(2))
            .then(Commands.literal("reload").executes(c->{try{reload(c.getSource().getServer());c.getSource().sendSuccess(()->Component.literal("Runtime data reloaded; clients stay connected. Revision "+revision.substring(0,12)),false);return 1;}catch(Exception ex){c.getSource().sendFailure(Component.literal("Rejected; previous data retained: "+ex.getMessage()));return 0;}}))
            .then(Commands.literal("status").executes(c->{c.getSource().sendSuccess(()->Component.literal("Runtime schema 1; menus="+config.menus().size()+" variants="+config.items().size()+" revision="+revision),false);return 1;}))
            .then(Commands.literal("assets").executes(c->{try{AssetServer.reload(c.getSource().getServer());c.getSource().sendSuccess(()->Component.literal("Assets scanned; changed hashes are pushed when needed."),false);return 1;}catch(Exception ex){c.getSource().sendFailure(Component.literal("Assets rejected; previous manifest remains active: "+ex.getMessage()));return 0;}}))
            .then(Commands.literal("give").then(Commands.argument("variant",StringArgumentType.word()).suggests((ctx,b)->{config.items().keySet().forEach(b::suggest);return b.buildFuture();}).executes(c->{String id=StringArgumentType.getString(c,"variant");var v=config.items().get(id);if(v==null)return 0;var p=c.getSource().getPlayerOrException();var item=stack(id,v);if(!p.addItem(item))p.drop(item,false);return 1;}))));
    }
    static boolean hasVisibleImage(Schema.Menu menu,java.util.function.IntPredicate canSee){
        return menu.entries().stream().anyMatch(entry->entry.kind().equals("image")&&canSee.test(entry.permission()));
    }
    static boolean deadlinePassed(long deadline,long now){return now>=deadline;}
    static boolean shouldDeferAssetMenu(Schema.Menu menu,java.util.function.IntPredicate canSee,
                                        boolean channelSupported,boolean ready,boolean skipWait,
                                        String id,PendingAssetMenu pending,long now){
        return !skipWait&&channelSupported&&!ready&&hasVisibleImage(menu,canSee)
            &&(pending==null||!pending.id().equals(id)||!deadlinePassed(pending.deadline(),now));
    }
    private static void expireAssetMenus(MinecraftServer server,long now){
        for(var entry:List.copyOf(pendingAssetMenus.entrySet())){
            var pending=entry.getValue();if(!deadlinePassed(pending.deadline(),now))continue;
            if(!pendingAssetMenus.remove(entry.getKey(),pending))continue;
            var player=server.getPlayerList().getPlayer(entry.getKey());
            if(player!=null)open(player,pending.id(),true);
        }
    }
    public static void open(ServerPlayer p,String id){open(p,id,false);}
    private static void open(ServerPlayer p,String id,boolean skipAssetWait){
        var menu=config.menus().get(id);if(menu==null||!p.hasPermissions(menu.permission()))return;
        var pending=pendingAssetMenus.get(p.getUUID());long now=System.nanoTime();
        if(shouldDeferAssetMenu(menu,p::hasPermissions,AssetNetwork.supported(p),AssetServer.ready(p),skipAssetWait,id,pending,now)){
            if(pending==null||!pending.id().equals(id))pendingAssetMenus.put(p.getUUID(),new PendingAssetMenu(id,now+ASSET_MENU_WAIT_NANOS));
            p.displayClientMessage(Component.literal("Preparing server art…"),true);return;
        }
        pendingAssetMenus.remove(p.getUUID());
        var controls=new ArrayList<Schema.Control>();for(var e:menu.entries())if(p.hasPermissions(e.permission())){double value=0;String text=e.text()==null?"":e.text();
            if(e.action().equals("setting"))try{value=Settings.read(e.target());}catch(Exception ex){text="Unavailable on this server build";controls.add(new Schema.Control(e.id(),e.label(),"label",text,0,0,0,0));continue;}
            if(e.props()!=null&&e.props().containsKey("value")&&!e.action().equals("setting"))value=Double.parseDouble(e.props().get("value"));
            controls.add(new Schema.Control(e.id(),e.label(),e.kind(),text,value,e.min(),e.max(),e.step(),e.props()));
        }
        var session=new Session(UUID.randomUUID().toString(),revision,id,System.nanoTime()+900_000_000_000L);
        var view=new Schema.View(1,revision,session.token,id,menu.title(),List.copyOf(controls));
        if(!viewFits(view)){
            p.displayClientMessage(Component.literal("This server menu is too large to display safely; ask an operator to shorten it."),false);
            return;
        }
        sessions.put(p.getUUID(),session);
        if(RuntimeNetwork.supported(p))RuntimeNetwork.send(p,view);else FallbackMenu.open(p,view);
    }
    /** Config documents can fit 64 KiB while their pretty-printed View exceeds the wire cap. */
    public static boolean viewFits(Schema.View view){return Schema.JSON.toJson(view).length()<=Schema.MAX_DOCUMENT;}
    public static void assetsReady(ServerPlayer p){var pending=pendingAssetMenus.remove(p.getUUID());if(pending!=null)open(p,pending.id(),true);}
    public static void action(ServerPlayer p,String raw){
        try{if(!admit(p.getUUID(),System.nanoTime()))return;var a=Schema.action(raw);var s=sessions.get(p.getUUID());
            if(s==null||s.expires<System.nanoTime()||!s.token.equals(a.session())||!s.revision.equals(a.revision())||!revision.equals(a.revision())||!s.menu.equals(a.menu())){p.displayClientMessage(Component.literal("Menu changed or expired; open /psychiatryk again."),true);return;}
            var menu=config.menus().get(s.menu);if(menu==null||!p.hasPermissions(menu.permission()))return;
            var entry=menu.entries().stream().filter(e->e.id().equals(a.control())).findFirst().orElse(null);if(entry==null||!p.hasPermissions(entry.permission()))return;
            // Consume a validated action before any side effect. A failed screen
            // refresh must never leave a setting or command token replayable.
            if(!sessions.remove(p.getUUID(),s))return;
            switch(entry.action()){
                case "open"->open(p,entry.target());
                case "command"->{p.closeContainer();p.getServer().getCommands().performPrefixedCommand(p.createCommandSourceStack(),entry.target());}
                case "message"->{p.sendSystemMessage(Component.literal(entry.text()==null?"":entry.text()));refresh(p,s.menu);}
                case "behavior"->{BehaviorEngine.run(config.behaviors().get(entry.target()),p);if(sessions.get(p.getUUID())==null)refresh(p,s.menu);}
                case "setting"->{if(!p.hasPermissions(2))return;double value=Double.parseDouble(a.value());Schema.require(Double.isFinite(value)&&value>=entry.min()&&value<=entry.max(),"Value outside bounds");Settings.write(entry.target(),value);refresh(p,s.menu);}
                default->{}
            }
        }catch(Exception ex){p.displayClientMessage(Component.literal("Rejected runtime action; settings unchanged."),true);var session=sessions.get(p.getUUID());if(session!=null)refresh(p,session.menu);}
    }
    private static void refresh(ServerPlayer p,String menu){try{open(p,menu);}catch(Exception sendFailure){System.err.println("[Psychiatryk Runtime] Menu refresh failed for "+p.getUUID()+": "+sendFailure.getMessage());}}
    static boolean admit(UUID id,long now){long[] r=rates.computeIfAbsent(id,k->new long[]{now,0});if(now-r[0]>1_000_000_000L){r[0]=now;r[1]=0;}return ++r[1]<=8;}
    private static void hook(String name,ServerPlayer p){String id=config.hooks().get(name);if(id==null)return;try{BehaviorEngine.run(config.behaviors().get(id),p);}catch(Exception ex){System.err.println("[Psychiatryk Runtime] Hook "+name+" failed for "+p.getUUID()+": "+ex.getMessage());}}
    @SubscribeEvent public void login(PlayerEvent.PlayerLoggedInEvent e){if(e.getEntity() instanceof ServerPlayer p)hook("join",p);}
    @SubscribeEvent public void respawn(PlayerEvent.PlayerRespawnEvent e){if(e.getEntity() instanceof ServerPlayer p)hook("respawn",p);}
    @SubscribeEvent public void dimension(PlayerEvent.PlayerChangedDimensionEvent e){if(e.getEntity() instanceof ServerPlayer p)hook("dimension_change",p);}
    @SubscribeEvent(priority=EventPriority.LOWEST) public void breakBlock(BlockEvent.BreakEvent e){
        if(e.isCanceled()||!(e.getPlayer() instanceof ServerPlayer p)||!(e.getLevel() instanceof ServerLevel level))return;
        var pending=pendingBreaks.computeIfAbsent(p.getUUID(),ignored->new ArrayList<>());
        if(pending.size()>=64||pending.stream().anyMatch(old->old.level()==level&&old.pos().equals(e.getPos())))return;
        pending.add(new PendingBreak(level,e.getPos().immutable(),e.getState()));
    }
    @SubscribeEvent public void death(LivingDeathEvent e){if(e.getSource().getEntity() instanceof ServerPlayer p)hook("kill",p);}
    @SubscribeEvent public void logout(PlayerEvent.PlayerLoggedOutEvent e){UUID id=e.getEntity().getUUID();sessions.remove(id);rates.remove(id);cooldowns.remove(id);lastSneak.remove(id);pendingAssetMenus.remove(id);pendingBreaks.remove(id);}
    @SubscribeEvent public void refreshInventory(net.neoforged.neoforge.event.tick.PlayerTickEvent.Post e){
        if(!(e.getEntity() instanceof ServerPlayer p))return;
        Boolean before=lastSneak.put(p.getUUID(),p.isShiftKeyDown());if(before!=null&&before!=p.isShiftKeyDown())hook(p.isShiftKeyDown()?"sneak":"unsneak",p);
        if(p.tickCount%20==0)hook("interval_1s",p);
        if(p.tickCount%100!=0)return;
        for(int i=0;i<p.getInventory().getContainerSize();i++){var item=p.getInventory().getItem(i);var data=item.get(DataComponents.CUSTOM_DATA);if(data==null)continue;var nbt=data.copyTag();String id=nbt.getString("psychiatrykRuntimeVariant");var v=config.items().get(id);if(v==null||revision.equals(nbt.getString("psychiatrykRuntimeRevision"))||!BuiltInRegistries.ITEM.getKey(item.getItem()).toString().equals(v.base())||!VariantSignature.verify(id,v.base(),nbt.getString("psychiatrykRuntimeSignature")))continue;
            item.set(DataComponents.CUSTOM_NAME,Component.literal(v.name()));item.set(DataComponents.LORE,new ItemLore(v.lore().stream().map(Component::literal).map(c->(Component)c).toList()));nbt.putString("psychiatrykRuntimeRevision",revision);item.set(DataComponents.CUSTOM_DATA,CustomData.of(nbt));
        }
    }
    @SubscribeEvent public void item(PlayerInteractEvent.RightClickItem e){
        if(!(e.getEntity() instanceof ServerPlayer p))return;var data=e.getItemStack().get(DataComponents.CUSTOM_DATA);if(data==null)return;String id=data.copyTag().getString("psychiatrykRuntimeVariant");var v=config.items().get(id);if(v==null||v.action().equals("none")||blocked(v.base()))return;
        if(!BuiltInRegistries.ITEM.getKey(e.getItemStack().getItem()).toString().equals(v.base())||!VariantSignature.verify(id,v.base(),data.copyTag().getString("psychiatrykRuntimeSignature")))return;
        if(v.action().equals("behavior")&&!BehaviorEngine.matches(config.behaviors().get(v.target()),p))return;
        e.setCanceled(true);e.setCancellationResult(InteractionResult.SUCCESS);var times=cooldowns.computeIfAbsent(p.getUUID(),ignored->new HashMap<>());long now=System.nanoTime();if(times.getOrDefault(id,0L)>now)return;
        try{switch(v.action()){
            case "message"->p.sendSystemMessage(Component.literal(v.target()));
            case "command"->p.getServer().getCommands().performPrefixedCommand(p.createCommandSourceStack(),v.target());
            case "effect"->p.addEffect(new net.minecraft.world.effect.MobEffectInstance(BuiltInRegistries.MOB_EFFECT.getHolderOrThrow(net.minecraft.resources.ResourceKey.create(net.minecraft.core.registries.Registries.MOB_EFFECT,ResourceLocation.parse(v.target()))),(int)(v.amount()*20),0));
            case "dash"->{p.setDeltaMovement(p.getLookAngle().scale(v.amount()));p.hurtMarked=true;}
            case "behavior"->BehaviorEngine.run(config.behaviors().get(v.target()),p);
            default->{return;}
        }}catch(Exception ex){p.displayClientMessage(Component.literal("Item action could not be completed."),true);System.err.println("[Psychiatryk Runtime] Item action failed: "+ex.getMessage());return;}
        times.put(id,now+v.cooldownSeconds()*1_000_000_000L);if(v.consume()&&!p.isCreative())e.getItemStack().shrink(1);
    }
}
