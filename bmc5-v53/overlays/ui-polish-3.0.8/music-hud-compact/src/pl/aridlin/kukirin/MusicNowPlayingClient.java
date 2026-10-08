package pl.aridlin.kukirin;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import java.io.ByteArrayInputStream;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.Map.Entry;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.client.renderer.texture.TextureManager;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.common.EventBusSubscriber.Bus;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.event.RegisterClientCommandsEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent.Post;

@EventBusSubscriber(
   modid = "goplanska_kukirin",
   value = {Dist.CLIENT}
)
public final class MusicNowPlayingClient {
   private static final Map<MusicNowPlayingClient.Key, MusicNowPlaying.Info> metadata = new LinkedHashMap<>();
   private static final Map<String, MusicNowPlayingClient.Cover> covers = new LinkedHashMap<>(8, 0.75F, true);
   private static final Map<ScooterAudioClient.Moving, UUID> discSessions = new IdentityHashMap<>();
   private static final Set<MusicNowPlayingClient.Key> announced = new LinkedHashSet<>();
   private static List<MusicNowPlayingClient.SourceView> live = List.of();
   private static Object level;
   private static MusicNowPlayingClient.Key selected;
   private static MusicNowPlayingClient.Key challenger;
   private static MusicNowPlayingClient.Key popup;
   private static long challengerSince;
   private static long popupStarted;
   private static final ItemStack FALLBACK = new ItemStack(Items.JUKEBOX);
   private static final int MAX_METADATA = 32;
   private static final int MAX_COVERS = 4;
   private static final int MAX_ANNOUNCED = 256;

   public static void receive(MusicNowPlaying.Info var0) {
      syncLevel();
      if (Minecraft.getInstance().level != null) {
         MusicNowPlayingClient.Key var1 = new MusicNowPlayingClient.Key(var0.source(), var0.session());
         MusicNowPlaying.Info var2 = metadata.remove(var1);
         if (var2 != null) {
            var0 = new MusicNowPlaying.Info(
               var0.source(),
               var0.session(),
               var0.title().isEmpty() ? var2.title() : var0.title(),
               var0.artist().isEmpty() ? var2.artist() : var0.artist(),
               var0.album().isEmpty() ? var2.album() : var0.album(),
               var0.durationTicks() == 0 ? var2.durationTicks() : var0.durationTicks(),
               var0.artwork().length == 0 ? var2.artwork() : var0.artwork()
            );
         }

         metadata.put(var1, var0);

         while (metadata.size() > 32) {
            metadata.remove(metadata.keySet().iterator().next());
         }
      }
   }

   private static void syncLevel() {
      ClientLevel var0 = Minecraft.getInstance().level;
      if (var0 != level) {
         clear();
         level = var0;
      }
   }

   public static void clear() {
      TextureManager var0 = Minecraft.getInstance().getTextureManager();

      for (MusicNowPlayingClient.Cover var2 : covers.values()) {
         var0.release(var2.square());
         var0.release(var2.disc());
      }

      covers.clear();
      metadata.clear();
      discSessions.clear();
      announced.clear();
      live = List.of();
      popup = null;
      challenger = null;
      selected = null;
      popupStarted = 0L;
   }

   public static List<MusicNowPlayingClient.SourceView> sources() {
      return live;
   }

   public static MusicNowPlayingClient.SourceView view(UUID var0) {
      return live.stream().filter(var1 -> var1.source().equals(var0)).findFirst().orElse(null);
   }

   public static MusicNowPlayingClient.SourceView current() {
      return selected();
   }

   /** Exact-source metadata stays available in the picker while audio is paused. */
   public static MusicNowPlaying.Info menuMetadata(UUID source) {
      MusicNowPlaying.Info result = null;
      if (source != null) for (MusicNowPlaying.Info info : metadata.values()) {
         if (source.equals(info.source())) result = info;
      }
      return result;
   }

   public static ResourceLocation menuArt(UUID source) {
      MusicNowPlaying.Info info = menuMetadata(source);
      Cover value = info == null ? null : cover(info.artwork());
      return value == null ? null : value.square();
   }

   /** Half-size cards, positioned beside the minimap's actual rendered footprint. */
   static int hudWidth(int screenWidth, boolean popupCard, MusicHudSettings.Corner corner) {
      return Math.max(24, Math.min(popupCard ? 123 : 101, screenWidth - 16));
   }

   static int hudX(int screenWidth, int cardWidth, MusicHudSettings.Corner corner, boolean popupCard) {
      boolean right = corner == MusicHudSettings.Corner.TOP_RIGHT || corner == MusicHudSettings.Corner.BOTTOM_RIGHT;
      int reserve = corner == MusicHudSettings.Corner.TOP_RIGHT ? 138 : 0;
      return right ? Math.max(8, screenWidth - cardWidth - 8 - reserve) : 8;
   }

   static int hudY(int screenWidth, int screenHeight, int cardWidth, int cardHeight, MusicHudSettings.Corner corner, int usual, boolean popupCard) {
      return corner == MusicHudSettings.Corner.TOP_RIGHT && screenWidth < cardWidth + 154
         ? Math.max(8, Math.min(screenHeight - cardHeight - 8, Math.max(158, usual))) : usual;
   }

   static int[] adjacentMapPosition(int screenWidth, int screenHeight, int cardWidth, int cardHeight,
                                    int mapX, int mapY, int mapWidth, int mapHeight, int offset) {
      int gap = 4;
      int x = mapX - gap - cardWidth;
      int y = mapY + offset;
      if (x < 8) { x = mapX + mapWidth + gap; }
      if (x + cardWidth > screenWidth - 8) { x = Math.max(8, Math.min(screenWidth-cardWidth-8,mapX)); y = mapY+mapHeight+gap+offset; }
      return new int[]{Math.max(8,Math.min(screenWidth-cardWidth-8,x)), Math.max(8,Math.min(screenHeight-cardHeight-8,y))};
   }

   // Optional integration: no Xaero link or class loading occurs on a dedicated server.
   private static boolean minimapLookupDone;
   private static Object minimapModule;
   private static java.lang.reflect.Method minimapPushState, minimapSession, minimapActive;
   private static java.lang.reflect.Field mapX, mapY, mapWidth, mapHeight;
   private static int[] minimapBounds() {
      try {
         if (!minimapLookupDone) {
            minimapLookupDone = true;
            Class<?> modules = Class.forName("xaero.hud.minimap.BuiltInHudModules");
            minimapModule = modules.getField("MINIMAP").get(null);
            Class<?> type = minimapModule.getClass();
            minimapPushState = type.getMethod("getPushState");
            minimapSession = type.getMethod("getCurrentSession");
            Class<?> state = minimapPushState.getReturnType();
            mapX = state.getField("x"); mapY = state.getField("y");
            mapWidth = state.getField("w"); mapHeight = state.getField("h");
            minimapActive = Class.forName("xaero.hud.module.ModuleSession").getMethod("isActive");
         }
         if (minimapModule == null) return null;
         Object session = minimapSession.invoke(minimapModule);
         if (session == null || !Boolean.TRUE.equals(minimapActive.invoke(session))) return null;
         Object state = minimapPushState.invoke(minimapModule);
         int x = mapX.getInt(state), y = mapY.getInt(state), w = mapWidth.getInt(state), h = mapHeight.getInt(state);
         return w > 0 && h > 0 ? new int[]{x,y,w,h} : null;
      } catch (ReflectiveOperationException | LinkageError ignored) { return null; }
   }

   /** Opaque grey inventory-style bevel, using integer pixel edges. */
   public static void minecraftPanel(GuiGraphics gui, int x, int y, int width, int height) {
      gui.fill(x, y, x + width, y + height, 0xFF171717);
      gui.fill(x + 1, y + 1, x + width - 1, y + height - 1, 0xFF555555);
      gui.fill(x + 2, y + 2, x + width - 2, y + height - 2, 0xFFC6C6C6);
      gui.fill(x + 2, y + 2, x + width - 2, y + 3, 0xFFFFFFFF);
      gui.fill(x + 2, y + 2, x + 3, y + height - 2, 0xFFFFFFFF);
      gui.fill(x + 3, y + height - 3, x + width - 2, y + height - 2, 0xFF757575);
      gui.fill(x + width - 3, y + 3, x + width - 2, y + height - 2, 0xFF757575);
   }

   public static void removed(UUID var0, UUID var1) {
      live = live.stream().filter(var2 -> !var2.source().equals(var0) || var1 != null && !var2.session().equals(var1)).toList();
      if (selected != null && selected.source().equals(var0) && (var1 == null || selected.session().equals(var1))) {
         selected = null;
      }

      if (popup != null && popup.source().equals(var0) && (var1 == null || popup.session().equals(var1))) {
         popup = null;
         popupStarted = 0L;
      }
   }

   public static void observe(Collection<ScooterAudioClient.Moving> var0) {
      syncLevel();
      Minecraft var1 = Minecraft.getInstance();
      if (var1.level == null) {
         live = List.of();
      } else {
         ArrayList var2 = new ArrayList();
         Set var3 = Collections.newSetFromMap(new IdentityHashMap());
         Vec3 var4 = var1.gameRenderer.getMainCamera().getPosition();

         for (ScooterAudioClient.Moving var6 : var0) {
            var3.add(var6);
            if (!var6.isStopped() && var6.physicsActive() && var1.getSoundManager().isActive(var6)) {
               UUID var7 = var6.session != null ? var6.session : discSessions.computeIfAbsent(var6, var0x -> UUID.randomUUID());
               MusicNowPlayingClient.Key var8 = new MusicNowPlayingClient.Key(var6.source, var7);
               MusicNowPlaying.Info var9 = metadata.get(var8);
               if (var9 == null) {
                  var9 = new MusicNowPlaying.Info(
                     var6.source, var7, MusicNowPlaying.fallbackTitle(var6.getLocation().getPath()), "", "", var6.duration, new byte[0]
                  );
               }

               MusicNowPlayingClient.Cover var10 = cover(var9.artwork());
               double var11 = Math.max(0.0, (double)var6.age * 0.05);
               double var13 = Math.max(0.0, (double)var6.duration * 0.05);
               float var15 = Math.max(0.0F, var6.getVolume());
               float var16 = var1.options.getSoundSourceVolume(SoundSource.MASTER);
               float var17 = var6.getSource() == SoundSource.MASTER ? 1.0F : var1.options.getSoundSourceVolume(var6.getSource());
               double var18 = var4.distanceTo(new Vec3(var6.getX(), var6.getY(), var6.getZ()));
               double var20 = var6.getSound() == null ? 16.0 : (double)((float)var6.getSound().getAttenuationDistance() * Math.max(1.0F, var15));
               float var22 = (float)((double)(var15 * var16 * var17) * Math.max(0.0, 1.0 - var18 / Math.max(1.0, var20)));
               if (!Float.isFinite(var22)) {
                  var22 = 0.0F;
               }

               boolean var23 = Boolean.TRUE.equals(var6.loopOverride)
                  || var6.loopOverride == null && var6.scooter != null && ScooterMusic.looping(var6.scooter);
               var2.add(
                  new MusicNowPlayingClient.SourceView(
                     var6.source,
                     var7,
                     var6.entity,
                     var6.block,
                     var9,
                     var10 == null ? null : var10.square(),
                     var10 == null ? null : var10.disc(),
                     var11,
                     var13,
                     var23,
                     var22
                  )
               );
            }
         }

         discSessions.keySet().retainAll(var3);
         live = List.copyOf(var2);
         choose();
      }
   }

   private static MusicNowPlayingClient.SourceView selected() {
      return selected == null
         ? null
         : live.stream().filter(var0 -> var0.source().equals(selected.source()) && var0.session().equals(selected.session())).findFirst().orElse(null);
   }

   private static void choose() {
      MusicNowPlayingClient.SourceView var0 = live.stream()
         .filter(var0x -> var0x.audibility() > 0.005F)
         .max(Comparator.comparingDouble(MusicNowPlayingClient.SourceView::audibility).thenComparing(var0x -> var0x.source().toString()))
         .orElse(null);
      MusicNowPlayingClient.SourceView var1 = selected();
      long var2 = System.nanoTime();
      if (var0 == null) {
         popup = null;
         challenger = null;
         selected = null;
         popupStarted = 0L;
      } else {
         MusicNowPlayingClient.Key var4 = new MusicNowPlayingClient.Key(var0.source(), var0.session());
         if (var1 != null && !(var1.audibility() <= 0.005F)) {
            if (var4.equals(selected) || var0.audibility() <= var1.audibility() * 1.35F + 0.02F) {
               challenger = null;
            } else if (!var4.equals(challenger)) {
               challenger = var4;
               challengerSince = var2;
            } else {
               if (var2 - challengerSince >= 600000000L) {
                  select(var4);
               }
            }
         } else {
            select(var4);
         }
      }
   }

   private static void select(MusicNowPlayingClient.Key var0) {
      selected = var0;
      challenger = null;
      if (announced.add(var0)) {
         popup = var0;
         popupStarted = 0L;

         while (announced.size() > 256) {
            announced.remove(announced.iterator().next());
         }
      }
   }

   private static MusicNowPlayingClient.Cover cover(byte[] var0) {
      if (var0.length == 0) {
         return null;
      } else {
         NativeImage var1 = null;
         NativeImage var2 = null;
         DynamicTexture var3 = null;
         DynamicTexture var4 = null;
         ResourceLocation var5 = null;
         ResourceLocation var6 = null;
         boolean var7 = false;
         boolean var8 = false;

         Object var22;
         try {
            String var9 = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(var0));
            MusicNowPlayingClient.Cover var21 = covers.get(var9);
            if (var21 != null) {
               return var21;
            }

            if (MusicNowPlaying.validArtwork(var0)) {
               Minecraft var23 = Minecraft.getInstance();
               var1 = NativeImage.read(new ByteArrayInputStream(var0));
               if (var1.getWidth() == 64 && var1.getHeight() == 64) {
                  var2 = new NativeImage(64, 64, false);
                  var2.copyFrom(var1);

                  for (int var25 = 0; var25 < 64; var25++) {
                     for (int var13 = 0; var13 < 64; var13++) {
                        double var14 = ((double)var13 - 31.5) * ((double)var13 - 31.5) + ((double)var25 - 31.5) * ((double)var25 - 31.5);
                        if (var14 > 992.25 || var14 < 4.0) {
                           var2.setPixelRGBA(var13, var25, var2.getPixelRGBA(var13, var25) & 16777215);
                        }
                     }
                  }

                  int var26 = 0;
                  if (covers.size() >= 4) {
                     Entry var27 = covers.entrySet().iterator().next();
                     var26 = ((MusicNowPlayingClient.Cover)var27.getValue()).slot();
                     var23.getTextureManager().release(((MusicNowPlayingClient.Cover)var27.getValue()).square());
                     var23.getTextureManager().release(((MusicNowPlayingClient.Cover)var27.getValue()).disc());
                     covers.remove(var27.getKey());
                  } else {
                     HashSet var28 = new HashSet();

                     for (MusicNowPlayingClient.Cover var15 : covers.values()) {
                        var28.add(var15.slot());
                     }

                     while (var28.contains(var26)) {
                        var26++;
                     }
                  }

                  var5 = ResourceLocation.parse("goplanska_kukirin:now_playing/slot_" + var26);
                  var6 = ResourceLocation.parse("goplanska_kukirin:now_playing/slot_" + var26 + "_disc");
                  var3 = new DynamicTexture(var1);
                  var1 = null;
                  var4 = new DynamicTexture(var2);
                  var2 = null;
                  var23.getTextureManager().register(var5, var3);
                  var7 = true;
                  var23.getTextureManager().register(var6, var4);
                  var8 = true;
                  MusicNowPlayingClient.Cover var29 = new MusicNowPlayingClient.Cover(var5, var6, var26);
                  covers.put(var9, var29);
                  var3 = null;
                  var4 = null;
                  return var29;
               }

               return null;
            }

            var22 = null;
         } catch (Exception var19) {
            TextureManager var10 = Minecraft.getInstance().getTextureManager();
            if (var3 != null) {
               if (var7) {
                  var10.release(var5);
               } else {
                  var3.close();
               }
            }

            if (var4 != null) {
               if (var8) {
                  var10.release(var6);
               } else {
                  var4.close();
               }
            }

            return null;
         } finally {
            if (var1 != null) {
               var1.close();
            }

            if (var2 != null) {
               var2.close();
            }
         }

         return (MusicNowPlayingClient.Cover)var22;
      }
   }

   @SubscribeEvent
   public static void tick(Post var0) {
      syncLevel();
   }

   @SubscribeEvent
   public static void render(net.neoforged.neoforge.client.event.RenderGuiEvent.Post var0) {
      Minecraft var1 = Minecraft.getInstance();
      if (var1.level != null && !var1.options.hideGui && var1.screen == null) {
         MusicHudSettings.Values var2 = MusicHudSettings.get();
         MusicNowPlayingClient.SourceView var3 = selected();
         if (var3 != null) {
            GuiGraphics var4 = var0.getGuiGraphics();
            if (var2.hud()) {
               card(var4, var3, var2.hudCorner(), false, var2.artwork());
            }

            if (popup != null && !popup.equals(selected)) {
               popup = null;
               popupStarted = 0L;
            }

            if (var2.popup() && popup != null) {
               if (popupStarted == 0L) {
                  popupStarted = System.nanoTime();
               }

               if (System.nanoTime() - popupStarted < (long)var2.popupSeconds() * 1000000000L) {
                  card(var4, var3, var2.popupCorner(), true, var2.artwork());
               } else {
                  popup = null;
               }
            }
         }
      }
   }

   private static void card(GuiGraphics var0, MusicNowPlayingClient.SourceView var1, MusicHudSettings.Corner var2, boolean var3, boolean var4) {
      Minecraft var5 = Minecraft.getInstance();
      int actualWidth = hudWidth(var0.guiWidth(), var3, var2);
      int actualHeight = var3 ? 36 : 26;
      int x = hudX(var0.guiWidth(), actualWidth, var2, var3);
      int y = var2 != MusicHudSettings.Corner.BOTTOM_RIGHT && var2 != MusicHudSettings.Corner.BOTTOM_LEFT ? 8 : var0.guiHeight()-actualHeight-32;
      MusicHudSettings.Values var10 = MusicHudSettings.get();
      int offset = var3 && var10.hud() && var2 == var10.hudCorner() ? 30 : 0;
      if (var2 == MusicHudSettings.Corner.BOTTOM_RIGHT || var2 == MusicHudSettings.Corner.BOTTOM_LEFT) y -= offset; else y += offset;
      y = hudY(var0.guiWidth(), var0.guiHeight(), actualWidth, actualHeight, var2, y, var3);
      int[] map = var2 == MusicHudSettings.Corner.TOP_RIGHT ? minimapBounds() : null;
      if (map != null) {
         int[] position = adjacentMapPosition(var0.guiWidth(),var0.guiHeight(),actualWidth,actualHeight,map[0],map[1],map[2],map[3],offset);
         x = position[0]; y = position[1];
      }
      var0.pose().pushPose();
      var0.pose().translate(x,y,0);
      var0.pose().scale(0.5F,0.5F,1.0F);
      int var6 = actualWidth * 2;
      int var7 = actualHeight * 2;
      int var8 = 0, var9 = 0;
      minecraftPanel(var0, var8, var9, var6, var7);
      int var11 = var3 ? 56 : 36;
      int var12 = var8 + 8;
      int var13 = var9 + 8;
      if (var4 && var1.artwork() != null) {
         var0.blit(var1.artwork(), var12, var13, var11, var11, 0.0F, 0.0F, 64, 64, 64, 64);
      } else {
         var0.fill(var12, var13, var12 + var11, var13 + var11, 0xFF8B8B8B);
         var0.pose().pushPose();
         var0.pose().translate((float)var12 + (float)(var11 - 24) / 2.0F, (float)var13 + (float)(var11 - 24) / 2.0F, 0.0F);
         var0.pose().scale(1.5F, 1.5F, 1.0F);
         var0.renderItem(FALLBACK, 0, 0);
         var0.pose().popPose();
      }

      int var14 = var12 + var11 + 8;
      int var15 = var8 + var6 - var14 - 8;
      if (var3) var0.drawString(var5.font, "NEW SONG", var14, var9 + 7, 0xFF404040, false);
      var0.drawString(var5.font, fit(var1.metadata().title(), var15), var14, var9 + (var3 ? 19 : 7), 0xFF171717, false);
      String var16 = var1.metadata().artist();
      if (var16.isEmpty()) {
         var16 = var1.entity() instanceof Player ? "Worn jukebox" : (var1.block() != null ? "Jukebox" : "Scooter");
      }

      var0.drawString(var5.font, fit(var16, var15), var14, var9 + (var3 ? 31 : 20), 0xFF404040, false);
      if (var3 && !var1.metadata().album().isEmpty()) {
         var0.drawString(var5.font, fit(var1.metadata().album(), var15), var14, var9 + 43, 0xFF555555, false);
      }

      int var17 = var9 + var7 - 5;
      double var18 = var1.durationSeconds() > 0.0
         ? var1.elapsedSeconds() % (var1.loop() ? var1.durationSeconds() : Double.MAX_VALUE) / var1.durationSeconds()
         : 0.0;
      var0.fill(var14, var17, var8 + var6 - 8, var17 + 1, 0xFF555555);
      var0.fill(var14, var17, var14 + (int)((double)var15 * Math.clamp(var18, 0.0, 1.0)), var17 + 1, 0xFF3F6F31);
      var0.pose().popPose();
   }

   private static String fit(String var0, int var1) {
      Font var2 = Minecraft.getInstance().font;
      return var2.width(var0) <= var1 ? var0 : var2.plainSubstrByWidth(var0, Math.max(0, var1 - var2.width("…"))) + "…";
   }

   @SubscribeEvent
   public static void commands(RegisterClientCommandsEvent var0) {
      LiteralArgumentBuilder var1 = (LiteralArgumentBuilder)Commands.literal("musichud")
         .executes(
            var0x -> {
               ((CommandSourceStack)var0x.getSource())
                  .sendSuccess(
                     () -> Component.literal(
                           "Music HUD: /musichud hud|popup|artwork true|false, /musichud corner hud|popup top_right|top_left|bottom_right|bottom_left, /musichud reload"
                        ),
                     false
                  );
               return 1;
            }
         );

      for (String var3 : List.of("hud", "popup", "artwork")) {
         var1.then(
            Commands.literal(var3)
               .then(
                  Commands.argument("enabled", BoolArgumentType.bool())
                     .executes(
                        var1x -> {
                           MusicHudSettings.Values var2 = MusicHudSettings.get();
                           boolean var3x = BoolArgumentType.getBool(var1x, "enabled");
                           return save(
                              (CommandSourceStack)var1x.getSource(),
                              new MusicHudSettings.Values(
                                 var3.equals("hud") ? var3x : var2.hud(),
                                 var3.equals("popup") ? var3x : var2.popup(),
                                 var3.equals("artwork") ? var3x : var2.artwork(),
                                 var2.hudCorner(),
                                 var2.popupCorner(),
                                 var2.popupSeconds()
                              )
                           );
                        }
                     )
               )
         );
      }

      LiteralArgumentBuilder var10 = Commands.literal("corner");

      for (String var4 : List.of("hud", "popup")) {
         LiteralArgumentBuilder var5 = Commands.literal(var4);

         for (MusicHudSettings.Corner var9 : MusicHudSettings.Corner.values()) {
            var5.then(
               Commands.literal(var9.name().toLowerCase(Locale.ROOT))
                  .executes(
                     var2 -> {
                        MusicHudSettings.Values var3x = MusicHudSettings.get();
                        return save(
                           (CommandSourceStack)var2.getSource(),
                           new MusicHudSettings.Values(
                              var3x.hud(),
                              var3x.popup(),
                              var3x.artwork(),
                              var4.equals("hud") ? var9 : var3x.hudCorner(),
                              var4.equals("popup") ? var9 : var3x.popupCorner(),
                              var3x.popupSeconds()
                           )
                        );
                     }
                  )
            );
         }

         var10.then(var5);
      }

      var1.then(var10);
      var1.then(Commands.literal("reload").executes(var0x -> {
         MusicHudSettings.reload();
         ((CommandSourceStack)var0x.getSource()).sendSuccess(() -> Component.literal("Music HUD preferences reloaded."), false);
         return 1;
      }));
      var0.getDispatcher().register(var1);
   }

   private static int save(CommandSourceStack var0, MusicHudSettings.Values var1) {
      try {
         MusicHudSettings.save(var1);
         var0.sendSuccess(() -> Component.literal("Music HUD preference saved."), false);
         return 1;
      } catch (Exception var3) {
         var0.sendFailure(Component.literal("Music HUD preferences could not be saved."));
         return 0;
      }
   }

   private MusicNowPlayingClient() {
   }

   @EventBusSubscriber(
      modid = "goplanska_kukirin",
      value = {Dist.CLIENT},
      bus = Bus.MOD
   )
   public static final class Bootstrap {
      @SubscribeEvent
      public static void setup(FMLClientSetupEvent var0) {
         var0.enqueueWork(() -> MusicNowPlaying.clientHandler(MusicNowPlayingClient::receive));
      }
   }

   private static record Cover(ResourceLocation square, ResourceLocation disc, int slot) {
   }

   private static record Key(UUID source, UUID session) {
   }

   public static record SourceView(
      UUID source,
      UUID session,
      Entity entity,
      BlockPos block,
      MusicNowPlaying.Info metadata,
      ResourceLocation artwork,
      ResourceLocation discArtwork,
      double elapsedSeconds,
      double durationSeconds,
      boolean loop,
      float audibility
   ) {
   }
}
