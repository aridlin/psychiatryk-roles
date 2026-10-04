package pl.aridlin.partymarkers;
import java.util.*;
import java.nio.file.*;
import net.minecraft.client.Minecraft;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.phys.*;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.client.event.*;
/** Seven providers for the shared chams effect. Never requests hidden server data. */
@EventBusSubscriber(modid="goplanska_party_markers",value=Dist.CLIENT)
public final class ChamsCategories {
 public enum Kind {PARTY,MARKED,CHESTS,ORES,ITEMS,HOSTILES,PASSIVE,CHALK,AREAS,BLOCKS}
 static final EnumSet<Kind> enabled=EnumSet.of(Kind.PARTY,Kind.MARKED,Kind.CHESTS,Kind.CHALK,Kind.AREAS,Kind.BLOCKS);
 static final Set<UUID> marked=new HashSet<>();static final Set<BlockPos> ores=new HashSet<>();
 static final Set<BlockPos> blocks=new HashSet<>();
 static Object level;static BlockPos scanCenter;static int cursor,radius=16;static boolean loaded;
 static java.lang.reflect.Field highlights;static boolean triedStorage;
 static final Path config=Path.of("config/goplanska-chams.json");
 public static boolean enabled(Kind k){load();return enabled.contains(k)&&(k!=Kind.ORES||admin());}
 static boolean admin(){var player=Minecraft.getInstance().player;return player!=null&&player.hasPermissions(2);}
 static void load(){if(loaded)return;loaded=true;try{if(Files.exists(config)){var d=com.google.gson.JsonParser.parseString(Files.readString(config)).getAsJsonObject();enabled.clear();for(var v:d.getAsJsonArray("enabled"))enabled.add(Kind.valueOf(v.getAsString()));radius=Math.max(4,Math.min(32,d.get("oreRadius").getAsInt()));}}catch(Exception e){enabled.clear();enabled.addAll(EnumSet.of(Kind.PARTY,Kind.MARKED,Kind.CHESTS,Kind.CHALK,Kind.AREAS,Kind.BLOCKS));}}
 static void save(){try{Files.createDirectories(config.getParent());Files.writeString(config,new com.google.gson.GsonBuilder().setPrettyPrinting().create().toJson(Map.of("enabled",enabled.stream().map(Enum::name).toList(),"oreRadius",radius)));}catch(Exception e){org.slf4j.LoggerFactory.getLogger("GoplanskaChams").warn("Could not save preferences",e);}}
 @SubscribeEvent public static void commands(RegisterClientCommandsEvent e){load();var root=Commands.literal("chams").executes(c->{c.getSource().sendSuccess(()->Component.literal("Chams: "+enabled+". /chams <category> on|off; /chams mark; /chams block; /chams range 4..32"),false);return 1;});
 for(Kind kind:Kind.values()){var node=Commands.literal(kind.name().toLowerCase(Locale.ROOT));if(kind==Kind.ORES)node.requires(c->admin());for(boolean on:new boolean[]{true,false})node.then(Commands.literal(on?"on":"off").executes(c->{if(on)enabled.add(kind);else enabled.remove(kind);save();c.getSource().sendSuccess(()->Component.literal(kind+" chams "+(on?"enabled":"disabled")),false);return 1;}));root.then(node);}
 root.then(Commands.literal("mark").executes(c->{var mc=Minecraft.getInstance();if(mc.hitResult instanceof EntityHitResult hit&&hit.getEntity() instanceof LivingEntity target){boolean added=marked.add(target.getUUID());if(!added)marked.remove(target.getUUID());enabled.add(Kind.MARKED);c.getSource().sendSuccess(()->Component.literal((added?"Marked ":"Unmarked ")+target.getName().getString()),false);return 1;}c.getSource().sendFailure(Component.literal("Aim at a nearby mob first."));return 0;}).then(Commands.literal("clear").executes(c->{marked.clear();return 1;})));
 root.then(Commands.literal("block").executes(c->{var mc=Minecraft.getInstance();if(mc.hitResult instanceof BlockHitResult hit && hit.getType()==HitResult.Type.BLOCK){var pos=hit.getBlockPos().immutable();boolean added=blocks.add(pos);if(!added)blocks.remove(pos);enabled.add(Kind.BLOCKS);c.getSource().sendSuccess(()->Component.literal((added?"Marked block ":"Unmarked block ")+pos.toShortString()),false);return 1;}c.getSource().sendFailure(Component.literal("Aim at a nearby block first."));return 0;}).then(Commands.literal("clear").executes(c->{blocks.clear();return 1;})));
 root.then(Commands.literal("range").requires(c->admin()).then(Commands.argument("blocks",com.mojang.brigadier.arguments.IntegerArgumentType.integer(4,32)).executes(c->{radius=com.mojang.brigadier.arguments.IntegerArgumentType.getInteger(c,"blocks");cursor=0;ores.clear();save();c.getSource().sendSuccess(()->Component.literal("Ore scan radius: "+radius+" loaded blocks"),false);return 1;})));e.getDispatcher().register(root);}
 @SubscribeEvent public static void tick(ClientTickEvent.Post e){load();var mc=Minecraft.getInstance();if(mc.level!=level){level=mc.level;ores.clear();marked.clear();blocks.clear();cursor=0;scanCenter=null;ChamsPass.clear();}if(mc.player==null||mc.level==null||!enabled(Kind.ORES)){ores.clear();return;}
 var center=mc.player.blockPosition();if(scanCenter==null||scanCenter.distSqr(center)>16){scanCenter=center;cursor=0;ores.removeIf(p->p.distSqr(center)>radius*radius);}
 int side=radius*2+1,total=side*side*side;for(int i=0;i<512;i++){int n=cursor++%total;var p=scanCenter.offset(n%side-radius,(n/side)%side-radius,n/(side*side)-radius);if(p.distSqr(center)>radius*radius||!mc.level.hasChunkAt(p))continue;if(mc.level.getBlockState(p).is(net.neoforged.neoforge.common.Tags.Blocks.ORES))ores.add(p.immutable());else ores.remove(p);}ores.removeIf(p->p.distSqr(center)>radius*radius);}
 record Hover(Component name,int color,double distance){}static Hover hover;static long hoverTime;
 static void considerHover(RenderLevelStageEvent e,ItemEntity item,int color){var mc=Minecraft.getInstance();var eye=e.getCamera().getPosition();var end=eye.add(net.minecraft.world.phys.Vec3.directionFromRotation(e.getCamera().getXRot(),e.getCamera().getYRot()).scale(64));var hit=item.getBoundingBox().inflate(.15,.3,.15).clip(eye,end);if(hit.isEmpty())return;double distance=eye.distanceToSqr(hit.get());if(hover==null||distance<hover.distance()){var name=item.getItem().getHoverName().copy();if(item.getItem().getCount()>1)name.append(" ×"+item.getItem().getCount());hover=new Hover(name,color,distance);}}
 @SubscribeEvent public static void itemName(RenderGuiEvent.Post e){var mc=Minecraft.getInstance();if(hover==null||mc.screen!=null||mc.options.hideGui||System.nanoTime()-hoverTime>500_000_000L)return;var g=e.getGuiGraphics();int x=g.guiWidth()/2+10,y=g.guiHeight()/2-5;g.drawString(mc.font,hover.name(),Math.min(x,g.guiWidth()-mc.font.width(hover.name())-4),y,hover.color(),true);}
 record Target(UUID id,AABB box,int color,Entity entity){}
 static AABB containerBox(BlockPos pos){var level=Minecraft.getInstance().level;var shape=level.getBlockState(pos).getShape(level,pos);return shape.isEmpty()?new AABB(pos):shape.bounds().move(pos);}
 static List<Target> chestTargets(){var result=new ArrayList<Target>();if(!enabled.contains(Kind.CHESTS)||!net.neoforged.fml.ModList.get().isLoaded("storagefinder"))return result;try{if(!triedStorage){triedStorage=true;highlights=Class.forName("com.storagefinder.storagefindermod.render.ContainerHighlightRenderer").getDeclaredField("activeHighlights");highlights.setAccessible(true);}if(highlights==null)return result;var mc=Minecraft.getInstance();long now=mc.level.getGameTime();for(Object entry:List.copyOf((List<?>)highlights.get(null))){var h=(com.storagefinder.storagefindermod.render.HighlightEntry)entry;if(h.isExpired(now))continue;AABB box=containerBox(h.pos());if(h.pairedPos()!=null)box=box.minmax(containerBox(h.pairedPos()));result.add(new Target(id("chest",h.pos()),box,h.color(),null));}}catch(Exception ex){if(highlights!=null){org.slf4j.LoggerFactory.getLogger("GoplanskaChams").warn("Storage Finder adapter unavailable",ex);highlights=null;}}return result;}
 static UUID id(String type,BlockPos p){return UUID.nameUUIDFromBytes((type+":"+Minecraft.getInstance().level.dimension().location()+":"+p.asLong()).getBytes(java.nio.charset.StandardCharsets.UTF_8));}
 @SubscribeEvent public static void render(RenderLevelStageEvent e){if(e.getStage()!=RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS||!ChamsEffect.ready()||qouteall.imm_ptl.core.render.context_management.PortalRendering.isRendering())return;var mc=Minecraft.getInstance();if(mc.player==null||mc.level==null)return;hover=null;hoverTime=System.nanoTime();var camera=e.getCamera().getPosition();var targets=new ArrayList<>(chestTargets());
 for(Entity entity:mc.level.entitiesForRendering()){if(entity==mc.player||!entity.isAlive()||entity.distanceToSqr(mc.player)>64*64)continue;
  boolean member=ClientWaypoints.members().stream().anyMatch(m->m.uuid().equals(entity.getUUID()));if(member)continue;
  int color=0;Entity model=null;
  if(entity instanceof LivingEntity living&&!(entity instanceof net.minecraft.world.entity.player.Player)&&marked.contains(entity.getUUID())&&enabled.contains(Kind.MARKED)){color=0xff55ffff;model=living;}
  else if(entity instanceof ItemEntity item&&(enabled.contains(Kind.ITEMS)||pl.aridlin.psychiatrykroles.LootLens.active(mc.player))){color=pl.aridlin.psychiatrykroles.LootLens.color(item.getItem());model=item;}
  else if(entity instanceof Mob mob){if(entity instanceof Enemy&&enabled.contains(Kind.HOSTILES)){color=0xffff5555;model=mob;}else if(!(entity instanceof Enemy)&&enabled.contains(Kind.PASSIVE)){color=0xff55ff55;model=mob;}}
  if(color!=0)targets.add(new Target(entity.getUUID(),entity.getBoundingBox(),color,model));
 }
 if(enabled.contains(Kind.BLOCKS))for(var pos:blocks)if(mc.level.hasChunkAt(pos)&&!mc.level.getBlockState(pos).isAir())targets.add(new Target(id("block",pos),new AABB(pos).deflate(.015),0xffffaa55,null));
 if(enabled(Kind.ORES))for(var pos:ores)if(mc.level.hasChunkAt(pos)&&mc.level.getBlockState(pos).is(net.neoforged.neoforge.common.Tags.Blocks.ORES))targets.add(new Target(id("ore",pos),new AABB(pos).deflate(.015),OreColors.color(mc.level.getBlockState(pos)),null));
 targets.sort(Comparator.comparingDouble(t->t.box().getCenter().distanceToSqr(camera)));int count=0;for(var t:targets){if(t.box().getCenter().distanceToSqr(camera)>64*64)continue;if(++count>16)break;if(t.entity() instanceof ItemEntity item){ChamsEffect.item(e,item,t.color());considerHover(e,item,t.color());}else if(t.entity() instanceof LivingEntity living)ChamsEffect.entity(e,living,t.color());else ChamsEffect.blockBox(e,t.id(),t.box(),t.color());}
 }
}
