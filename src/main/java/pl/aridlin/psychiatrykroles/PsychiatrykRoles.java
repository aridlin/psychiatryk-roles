package pl.aridlin.psychiatrykroles;

import com.mojang.logging.LogUtils;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.commands.arguments.ResourceLocationArgument;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.core.registries.Registries;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.commands.Commands;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.TagKey;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.Container;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobType;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.monster.AbstractIllager;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.scores.PlayerTeam;
import net.minecraft.world.scores.Scoreboard;
import net.minecraft.world.scores.Team;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.EntityMountEvent;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.entity.item.ItemTossEvent;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.event.entity.living.LivingChangeTargetEvent;
import net.minecraftforge.event.entity.living.LivingDropsEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.event.entity.living.LivingEvent;
import net.minecraftforge.event.entity.player.AttackEntityEvent;
import net.minecraftforge.event.entity.player.EntityItemPickupEvent;
import net.minecraftforge.event.entity.player.PlayerContainerEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.event.entity.player.PlayerSleepInBedEvent;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import org.slf4j.Logger;

@Mod(PsychiatrykRoles.MOD_ID)
public final class PsychiatrykRoles {
    static final String MOD_ID = "psychiatryk_roles";
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final Set<UUID> LEGACY_PATIENTS = Set.of(
        UUID.fromString("87b7287e-0c23-4913-9edb-4760f5585ccb")
    );
    private static final Set<String> LEGACY_PATIENT_NAMES = Set.of(
        "szybkiorzech",
        "rozowykocurek"
    );
    private static final Map<UUID, ContainerSnapshot> CONTAINER_SNAPSHOTS = new ConcurrentHashMap<>();
    private static final Map<UUID, Long> NEXT_SIGN_GRANT_TICK = new ConcurrentHashMap<>();
    private static final long SIGN_GRANT_INTERVAL_TICKS = 20L * 60L * 10L;
    private static final String SIGN_REMOVER_MARKER = "psychiatrykSignRemover";
    private static final String CONSULTANT_SWORD_MARKER = "psychiatrykConsultantSword";
    private static final String CONSULTANT_PICKAXE_MARKER = "psychiatrykConsultantPickaxe";
    private static final String CONSULTANT_ITEM_MARKER = "psychiatrykConsultantItem";
    private static final String CONSULTANT_ACTION = "psychiatrykConsultantAction";
    private static final String CONSULTANT_TARGETS = "psychiatrykConsultantTargets";
    private static final String CONSULTANT_NEAR = "psychiatrykConsultantNear";
    private static final String CONSULTANT_RADIUS = "psychiatrykConsultantRadius";
    private static final String EXTRACTOR_MARKER = "psychiatrykExtractor";
    private static final String IMPORTER_MARKER = "psychiatrykImporter";
    private static final String TRAVEL_STAFF_MARKER = "psychiatrykTravelStaff";
    private static final String RETURN_MIRROR_MARKER = "psychiatrykReturnMirror";
    private static final String IMPORTED_ITEM_MARKER = "psychiatrykImportedItem";
    private static final String IMPORTED_ITEM_OWNER = "psychiatrykImportedOwner";
    private static final String KILL_LOOT_OWNER = "psychiatrykKillLootOwner";
    private static final String MINE_LOOT_OWNER = "psychiatrykMineLootOwner";
    private static final String LORE_LANGUAGE = "psychiatrykLoreLanguage";
    private static final String CONSULTANT_EXPIRES_AT = "psychiatrykConsultantExpiresAt";
    private static final String WELCOME_BOOK_MARKER = "psychiatrykWelcomeBook";
    private static final List<String> ITEM_PRESETS = List.of(
        "pickup", "container-key", "hostile-amulet", "rock-amulet", "sign",
        "sign-remover", "sword", "pickaxe", "importer", "extractor", "passage-staff", "return-mirror"
    );
    private static final DateTimeFormatter LOG_TIME = DateTimeFormatter.ofPattern("uuuu-MM-dd HH:mm:ss")
        .withZone(ZoneId.of("Europe/Warsaw"));
    private static final Set<UUID> SUPPRESS_DEFAULT_ITEMS = ConcurrentHashMap.newKeySet();
    private static final Map<BlockDropKey, PendingBlockDrop> PENDING_BLOCK_DROPS = new ConcurrentHashMap<>();
    private static final Map<UUID, Integer> LAST_MIRROR_USE_TICK = new ConcurrentHashMap<>();
    private static final Map<UUID, Provocation> HOSTILE_PROVOCATIONS = new ConcurrentHashMap<>();
    private static final Map<String, Long> LAST_AUDIT = new ConcurrentHashMap<>();
    private static final int HOSTILE_ANGER_TICKS = 20 * 30;
    private static final ResourceKey<Level> FREEDOM_DIMENSION = ResourceKey.create(
        Registries.DIMENSION, new ResourceLocation(MOD_ID, "konsultanci")
    );
    private static final Set<net.minecraft.world.level.block.Block> ROCK_BLOCKS = Set.of(
        Blocks.STONE, Blocks.COBBLESTONE, Blocks.MOSSY_COBBLESTONE,
        Blocks.DEEPSLATE, Blocks.COBBLED_DEEPSLATE, Blocks.ANDESITE,
        Blocks.DIORITE, Blocks.GRANITE, Blocks.TUFF, Blocks.CALCITE,
        Blocks.DRIPSTONE_BLOCK, Blocks.BLACKSTONE, Blocks.BASALT,
        Blocks.SMOOTH_BASALT, Blocks.END_STONE, Blocks.NETHERRACK
    );

    private record ContainerSnapshot(int containerId, List<ItemStack> items) {
        static ContainerSnapshot capture(ServerPlayer player) {
            return new ContainerSnapshot(
                player.containerMenu.containerId,
                player.containerMenu.slots.stream().map(slot -> slot.getItem().copy()).toList()
            );
        }

        void restore(ServerPlayer player) {
            if (player.containerMenu.containerId != containerId || player.containerMenu.slots.size() != items.size()) {
                return;
            }
            boolean changed = false;
            for (int index = 0; index < items.size(); index++) {
                ItemStack expected = items.get(index);
                if (!ItemStack.matches(player.containerMenu.getSlot(index).getItem(), expected)) {
                    player.containerMenu.getSlot(index).set(expected.copy());
                    changed = true;
                }
            }
            if (changed) {
                player.containerMenu.broadcastChanges();
            }
        }

        boolean hasConsultantDeposit(ServerPlayer player) {
            if (player.containerMenu.containerId != containerId || player.containerMenu.slots.size() != items.size()) {
                return false;
            }
            for (int index = 0; index < items.size(); index++) {
                var slot = player.containerMenu.getSlot(index);
                if (slot.container == player.getInventory()) {
                    continue;
                }
                ItemStack before = items.get(index);
                ItemStack now = slot.getItem();
                if (isConsultantEquipment(now)
                    && (!isConsultantEquipment(before)
                        || !ItemStack.isSameItemSameTags(now, before)
                        || now.getCount() > before.getCount())) {
                    return true;
                }
            }
            return false;
        }
    }

    private record BlockDropKey(ResourceKey<Level> dimension, BlockPos pos) {}
    private record PendingBlockDrop(UUID owner, long gameTime) {}
    private record Provocation(UUID consultant, int expiresAtTick) {}

    public PsychiatrykRoles() {
        MinecraftForge.EVENT_BUS.register(this);
    }

    private static boolean isOperator(ServerPlayer player) {
        return player.getServer() != null && player.getServer().getPlayerList().isOp(player.getGameProfile());
    }

    private static boolean isPatient(ServerPlayer player) {
        return LEGACY_PATIENTS.contains(player.getUUID())
            || LEGACY_PATIENT_NAMES.contains(player.getGameProfile().getName().toLowerCase())
            || (player.getServer() != null && RoleData.get(player.getServer()).isPatient(player.getUUID()));
    }

    public static boolean isConsultant(Player player) {
        return player instanceof ServerPlayer serverPlayer
            && !isOperator(serverPlayer)
            && !isPatient(serverPlayer);
    }

    private static boolean isFreedomDimension(Player player) {
        return player.level().dimension().equals(FREEDOM_DIMENSION);
    }

    private static boolean isRestrictedConsultant(Player player) {
        return isConsultant(player) && !isFreedomDimension(player);
    }

    private static boolean isEnglish(Player player) {
        return player instanceof ServerPlayer serverPlayer
            && serverPlayer.getServer() != null
            && RoleData.get(serverPlayer.getServer()).isEnglish(player.getUUID());
    }

    private static String tr(Player player, String polish, String english) {
        return isEnglish(player) ? english : polish;
    }

    private static String tr(net.minecraft.commands.CommandSourceStack source, String polish, String english) {
        return source.getEntity() instanceof Player player ? tr(player, polish, english) : polish;
    }

    private static void audit(net.minecraft.server.MinecraftServer server, String actor, String action, String detail) {
        RoleData.get(server).addAudit(actor, action, detail);
        LOGGER.info("[Psychiatryk Audit] {} | {} | {}", actor, action, detail);
    }

    private static void audit(Player player, String action, String detail) {
        if (player.getServer() != null) {
            audit(player.getServer(), player.getGameProfile().getName(), action, detail);
        }
    }

    private static void auditDenied(Player player, String action, String detail) {
        long now = System.currentTimeMillis();
        String key = player.getUUID() + "|" + action + "|" + detail;
        Long previous = LAST_AUDIT.put(key, now);
        if (previous == null || now - previous >= 2000L) {
            audit(player, action, detail);
        }
    }

    private static boolean isSpruceBlock(BlockState state) {
        var id = BuiltInRegistries.BLOCK.getKey(state.getBlock());
        return id.getNamespace().equals("minecraft") && id.getPath().contains("spruce");
    }

    private static boolean isSpruceSign(BlockState state) {
        return state.is(Blocks.SPRUCE_SIGN)
            || state.is(Blocks.SPRUCE_WALL_SIGN)
            || state.is(Blocks.SPRUCE_HANGING_SIGN)
            || state.is(Blocks.SPRUCE_WALL_HANGING_SIGN);
    }

    private static boolean isSpruceSignItem(ItemStack stack) {
        return stack.is(Items.SPRUCE_SIGN) || stack.is(Items.SPRUCE_HANGING_SIGN);
    }

    private static void appendLore(ItemStack stack, Component... lines) {
        CompoundTag display = stack.getOrCreateTagElement("display");
        ListTag lore = display.contains("Lore", Tag.TAG_LIST)
            ? display.getList("Lore", Tag.TAG_STRING)
            : new ListTag();
        for (Component line : lines) {
            lore.add(StringTag.valueOf(Component.Serializer.toJson(line)));
        }
        display.put("Lore", lore);
    }

    private static ItemStack makePlaceableSpruceSign(Player player) {
        ItemStack stack = new ItemStack(Items.SPRUCE_SIGN);
        makeSpruceSignsPlaceable(stack, player);
        return stack;
    }

    private static void makeSpruceSignsPlaceable(ItemStack stack, Player player) {
        if (!isSpruceSignItem(stack)) {
            return;
        }
        ListTag canPlaceOn = new ListTag();
        BuiltInRegistries.BLOCK.keySet().forEach(id -> canPlaceOn.add(StringTag.valueOf(id.toString())));
        CompoundTag tag = stack.getOrCreateTag();
        tag.put("CanPlaceOn", canPlaceOn);
        tag.putBoolean(CONSULTANT_ITEM_MARKER, true);
        localizeConsultantItem(stack, player, true);
    }

    private static boolean isPlaceableSpruceSign(ItemStack stack) {
        return isSpruceSignItem(stack) && stack.hasTag() && stack.getTag().contains("CanPlaceOn");
    }

    private static ItemStack makeSignRemover(Player player) {
        ItemStack stack = new ItemStack(Items.STONE_AXE);
        CompoundTag tag = stack.getOrCreateTag();
        tag.putBoolean("Unbreakable", true);
        tag.putBoolean(SIGN_REMOVER_MARKER, true);
        tag.putBoolean(CONSULTANT_ITEM_MARKER, true);
        ListTag canDestroy = new ListTag();
        canDestroy.add(StringTag.valueOf("minecraft:spruce_sign"));
        canDestroy.add(StringTag.valueOf("minecraft:spruce_wall_sign"));
        canDestroy.add(StringTag.valueOf("minecraft:spruce_hanging_sign"));
        canDestroy.add(StringTag.valueOf("minecraft:spruce_wall_hanging_sign"));
        tag.put("CanDestroy", canDestroy);
        localizeConsultantItem(stack, player, true);
        return stack;
    }

    private static boolean isSignRemover(ItemStack stack) {
        return stack.is(Items.STONE_AXE)
            && stack.hasTag()
            && stack.getTag().getBoolean(SIGN_REMOVER_MARKER);
    }

    private static ItemStack makeConsultantSword(ItemStack stack, Player player) {
        stack.getOrCreateTag().putBoolean(CONSULTANT_SWORD_MARKER, true);
        stack.getOrCreateTag().putBoolean(CONSULTANT_ITEM_MARKER, true);
        localizeConsultantItem(stack, player, true);
        return stack;
    }

    private static boolean isConsultantSword(ItemStack stack) {
        return stack.is(Items.STONE_SWORD)
            && stack.hasTag()
            && stack.getTag().getBoolean(CONSULTANT_SWORD_MARKER);
    }

    private static ItemStack makeConsultantPickaxe(ItemStack stack, Player player) {
        CompoundTag tag = stack.getOrCreateTag();
        tag.putBoolean(CONSULTANT_PICKAXE_MARKER, true);
        tag.putBoolean(CONSULTANT_ITEM_MARKER, true);
        ListTag canDestroy = new ListTag();
        canDestroy.add(StringTag.valueOf("minecraft:stone"));
        canDestroy.add(StringTag.valueOf("minecraft:cobblestone"));
        tag.put("CanDestroy", canDestroy);
        localizeConsultantItem(stack, player, true);
        return stack;
    }

    private static boolean isConsultantPickaxe(ItemStack stack) {
        return stack.is(Items.STONE_PICKAXE)
            && stack.hasTag()
            && stack.getTag().getBoolean(CONSULTANT_PICKAXE_MARKER);
    }

    private static boolean isConsultantEquipment(ItemStack stack) {
        if (!stack.hasTag() || !stack.getTag().getBoolean(CONSULTANT_ITEM_MARKER)) {
            return false;
        }
        CompoundTag tag = stack.getTag();
        return tag.getBoolean(SIGN_REMOVER_MARKER)
            || tag.getBoolean(CONSULTANT_SWORD_MARKER)
            || tag.getBoolean(CONSULTANT_PICKAXE_MARKER)
            || tag.getBoolean(EXTRACTOR_MARKER)
            || tag.getBoolean(IMPORTER_MARKER)
            || tag.getBoolean(TRAVEL_STAFF_MARKER)
            || tag.getBoolean(RETURN_MIRROR_MARKER)
            || tag.getBoolean(WELCOME_BOOK_MARKER)
            || tag.contains(CONSULTANT_ACTION, Tag.TAG_STRING)
            || (isSpruceSignItem(stack) && tag.contains("CanPlaceOn", Tag.TAG_LIST));
    }

    private static ItemStack makeExtractor(ItemStack stack, Player player) {
        CompoundTag tag = stack.getOrCreateTag();
        tag.putBoolean(EXTRACTOR_MARKER, true);
        tag.putBoolean(CONSULTANT_ITEM_MARKER, true);
        localizeConsultantItem(stack, player, true);
        return stack;
    }

    private static boolean isExtractor(ItemStack stack) {
        return stack.is(Items.SHEARS) && stack.hasTag() && stack.getTag().getBoolean(EXTRACTOR_MARKER);
    }

    private static ItemStack makeImporter(ItemStack stack, Player player) {
        CompoundTag tag = stack.getOrCreateTag();
        tag.putBoolean(IMPORTER_MARKER, true);
        tag.putBoolean(CONSULTANT_ITEM_MARKER, true);
        localizeConsultantItem(stack, player, true);
        return stack;
    }

    private static boolean isImporter(ItemStack stack) {
        return stack.is(Items.RECOVERY_COMPASS)
            && stack.hasTag()
            && stack.getTag().getBoolean(IMPORTER_MARKER);
    }

    private static ItemStack makeTravelStaff(Player player) {
        ItemStack stack = new ItemStack(Items.BLAZE_ROD);
        CompoundTag tag = stack.getOrCreateTag();
        tag.putBoolean(TRAVEL_STAFF_MARKER, true);
        tag.putBoolean(CONSULTANT_ITEM_MARKER, true);
        tag.putBoolean("Unbreakable", true);
        localizeConsultantItem(stack, player, true);
        return stack;
    }

    private static boolean isTravelStaff(ItemStack stack) {
        return stack.is(Items.BLAZE_ROD) && stack.hasTag() && stack.getTag().getBoolean(TRAVEL_STAFF_MARKER);
    }

    private static ItemStack makeReturnMirror(ItemStack stack, Player player) {
        stack.getOrCreateTag().putBoolean(RETURN_MIRROR_MARKER, true);
        stack.getOrCreateTag().putBoolean(CONSULTANT_ITEM_MARKER, true);
        localizeConsultantItem(stack, player, true);
        return stack;
    }

    private static boolean isReturnMirror(ItemStack stack) {
        return stack.is(Items.ECHO_SHARD) && stack.hasTag() && stack.getTag().getBoolean(RETURN_MIRROR_MARKER);
    }

    private static ItemStack makeWelcomeBook(Player player) {
        ItemStack stack = new ItemStack(Items.WRITTEN_BOOK);
        stack.getOrCreateTag().putBoolean(WELCOME_BOOK_MARKER, true);
        stack.getOrCreateTag().putBoolean(CONSULTANT_ITEM_MARKER, true);
        localizeConsultantItem(stack, player, true);
        return stack;
    }

    private static boolean isWelcomeBook(ItemStack stack) {
        return stack.is(Items.WRITTEN_BOOK) && stack.hasTag() && stack.getTag().getBoolean(WELCOME_BOOK_MARKER);
    }

    private static void localizeConsultantItem(ItemStack stack, Player viewer, boolean force) {
        if (!isConsultantEquipment(stack)) {
            return;
        }
        String language = isEnglish(viewer) ? "en" : "pl";
        CompoundTag tag = stack.getOrCreateTag();
        if (!force && language.equals(tag.getString(LORE_LANGUAGE))) {
            return;
        }
        tag.putString(LORE_LANGUAGE, language);
        CompoundTag display = tag.getCompound("display");
        display.remove("Name");
        display.remove("Lore");
        if (display.isEmpty()) {
            tag.remove("display");
        } else {
            tag.put("display", display);
        }
        boolean en = language.equals("en");
        if (isSignRemover(stack)) {
            stack.setHoverName(Component.literal(en ? "Sign Remover" : "Usuwacz Tabliczek").withStyle(ChatFormatting.AQUA));
            appendLore(stack,
                Component.literal(en ? "Removes spruce signs only." : "Usuwa wyłącznie świerkowe tabliczki.").withStyle(ChatFormatting.GRAY),
                Component.literal(en ? "Indestructible consultant item." : "Niezniszczalny przedmiot konsultanta.").withStyle(ChatFormatting.DARK_GRAY));
        } else if (isConsultantSword(stack)) {
            stack.setHoverName(Component.literal(en ? "Consultant Sword" : "Miecz Konsultanta").withStyle(ChatFormatting.RED));
            appendLore(stack,
                Component.literal(en ? "Can attack hostile mobs only." : "Pozwala atakować wyłącznie wrogie moby.").withStyle(ChatFormatting.GRAY),
                Component.literal(en ? "Other entities remain protected." : "Inne istoty pozostają chronione.").withStyle(ChatFormatting.DARK_GRAY));
        } else if (isConsultantPickaxe(stack)) {
            stack.setHoverName(Component.literal(en ? "Consultant Pickaxe" : "Kilof Konsultanta").withStyle(ChatFormatting.GRAY));
            appendLore(stack,
                Component.literal(en ? "Can mine stone and cobblestone." : "Pozwala wydobywać kamień i bruk.").withStyle(ChatFormatting.GRAY),
                Component.literal(en ? "Other blocks remain protected." : "Inne bloki pozostają chronione.").withStyle(ChatFormatting.DARK_GRAY));
        } else if (isExtractor(stack)) {
            stack.setHoverName(Component.literal(en ? "Consultant Item Extractor" : "Ekstraktor Przedmiotów").withStyle(ChatFormatting.YELLOW));
            appendLore(stack,
                Component.literal(en ? "Right-click to remove all consultant items." : "PPM usuwa wszystkie przedmioty konsultanta.").withStyle(ChatFormatting.GRAY),
                Component.literal(en ? "Default items return after rejoining." : "Przedmioty domyślne wrócą po ponownym wejściu.").withStyle(ChatFormatting.DARK_GRAY));
        } else if (isImporter(stack)) {
            stack.setHoverName(Component.literal("Importer").withStyle(ChatFormatting.GREEN));
            appendLore(stack,
                Component.literal(en ? "Main hand: Importer; offhand: ordinary item." : "Główna ręka: Importer; druga ręka: zwykły przedmiot.").withStyle(ChatFormatting.GRAY),
                Component.literal(en ? "Right-click to make the stack droppable." : "PPM przenosi stos i pozwala go wyrzucić.").withStyle(ChatFormatting.DARK_GRAY));
        } else if (isTravelStaff(stack)) {
            stack.setHoverName(Component.literal(en ? "Passage Staff" : "Laska Przejścia").withStyle(ChatFormatting.LIGHT_PURPLE));
            appendLore(stack,
                Component.literal(en ? "Right-click or drop: switch worlds." : "PPM lub wyrzucenie: przejdź między światami.").withStyle(ChatFormatting.GRAY),
                Component.literal(en ? "Returns to your last position in each world." : "Wracasz do ostatniej pozycji w każdym świecie.").withStyle(ChatFormatting.DARK_GRAY),
                Component.literal(en ? "Consultant restrictions are disabled there." : "W świecie konsultantów ograniczenia są wyłączone.").withStyle(ChatFormatting.GREEN));
        } else if (isReturnMirror(stack)) {
            stack.setHoverName(Component.literal(en ? "Mirror of Returning" : "Lustro Powrotu").withStyle(ChatFormatting.AQUA));
            appendLore(stack,
                Component.literal(en ? "Use: return to your valid respawn point." : "Użycie: wróć do prawidłowego punktu odrodzenia.").withStyle(ChatFormatting.GRAY),
                Component.literal(en ? "Use again quickly: go to world spawn." : "Użyj ponownie szybko: wróć na spawn świata.").withStyle(ChatFormatting.GOLD));
        } else if (isWelcomeBook(stack)) {
            stack.setHoverName(Component.literal(en ? "Consultant Handbook" : "Poradnik Konsultanta")
                .withStyle(ChatFormatting.GOLD));
            appendLore(stack, Component.literal(en
                ? "Localized guide to roles, tools, and both worlds."
                : "Przewodnik po rolach, narzędziach i obu światach.").withStyle(ChatFormatting.GRAY));
            tag.putString("title", en ? "Consultant Handbook" : "Poradnik Konsultanta");
            tag.putString("author", "Psychiatryk");
            tag.putBoolean("resolved", true);
            ListTag pages = new ListTag();
            String[] bookPages = en ? new String[] {
                "WELCOME, CONSULTANT\n\nYou are in Survival while server rules protect the hospital world from destructive actions.",
                "PROTECTION\n\nContainers are view-only. PvP, entity damage, trampling, mounting, and ordinary building need explicit permission.",
                "TOOLS\n\nPermission items describe their action and targets. Inventory amulets work anywhere. Ordinary items may be dropped and recovered.",
                "TRAVEL\n\nUse or drop the Passage Staff for the unrestricted world. The Mirror returns to your respawn; use it again quickly for world spawn.",
                "HELP\n\n/english or /polski changes language.\n/konsultant status shows permissions.\n/przyjecie <code> redeems admission."
            } : new String[] {
                "WITAJ, KONSULTANCIE\n\nJesteś w trybie Survival, a zasady serwera chronią świat szpitala przed niszczeniem.",
                "OCHRONA\n\nPojemniki są tylko do podglądu. PvP, krzywdzenie istot, deptanie, jazda i zwykłe budowanie wymagają uprawnienia.",
                "NARZĘDZIA\n\nPrzedmioty opisują akcję i cele. Amulety działają w ekwipunku. Zwykłe przedmioty można wyrzucać i odzyskiwać.",
                "PODRÓŻ\n\nUżyj lub wyrzuć Laskę Przejścia do swobodnego świata. Lustro wraca do odrodzenia, a szybko użyte ponownie na spawn świata.",
                "POMOC\n\n/polski lub /english zmienia język.\n/konsultant status pokazuje uprawnienia.\n/przyjecie <kod> wykorzystuje kod."
            };
            for (String page : bookPages) {
                pages.add(StringTag.valueOf(Component.Serializer.toJson(Component.literal(page))));
            }
            tag.put("pages", pages);
        } else if (tag.contains(CONSULTANT_ACTION, Tag.TAG_STRING)) {
            String action = tag.getString(CONSULTANT_ACTION);
            String targets = tag.getString(CONSULTANT_TARGETS);
            String near = tag.getString(CONSULTANT_NEAR);
            int radius = tag.getInt(CONSULTANT_RADIUS);
            String condition = near.isEmpty() ? "" : " near " + near + " (" + radius + ")";
            stack.setHoverName(Component.literal((en ? "Consultant: " : "Konsultant: ") + action + condition)
                .withStyle(ChatFormatting.AQUA));
            String usage = switch (action) {
                case "take", "pickup" -> en ? "Works anywhere in your inventory." : "Działa z dowolnego miejsca w ekwipunku.";
                case "place" -> en ? "Keep the token in your offhand and the block in your main hand." : "Trzymaj amulet w drugiej ręce, a blok w głównej.";
                case "attack-amulet", "mine-amulet", "place-amulet" -> en ? "The amulet works anywhere in your inventory." : "Amulet działa z dowolnego miejsca w ekwipunku.";
                default -> en ? "Hold this item in your main hand." : "Przedmiot musi być trzymany w głównej ręce.";
            };
            appendLore(stack,
                Component.literal((en ? "Permission: " : "Uprawnienie: ") + action).withStyle(ChatFormatting.AQUA),
                Component.literal(targets.isEmpty()
                    ? (en ? "Targets: all for this action" : "Cele: wszystkie dla tej akcji")
                    : (en ? "Targets: " : "Cele: ") + targets).withStyle(ChatFormatting.GRAY),
                Component.literal(usage).withStyle(ChatFormatting.GRAY));
            if (!near.isEmpty()) {
                appendLore(stack, Component.literal(en
                    ? "Condition: within " + radius + " blocks of " + near + "."
                    : "Warunek: maks. " + radius + " bloków od " + near + ".").withStyle(ChatFormatting.GOLD));
            }
        } else if (isSpruceSignItem(stack) && tag.contains("CanPlaceOn")) {
            appendLore(stack,
                Component.literal(en ? "Consultant: placeable on any block." : "Konsultant: można postawić na dowolnym bloku.").withStyle(ChatFormatting.GRAY),
                Component.literal(en ? "Cannot be dropped without the Importer." : "Nie można wyrzucić bez Importera.").withStyle(ChatFormatting.DARK_GRAY));
        }
        long expiresAt = tag.getLong(CONSULTANT_EXPIRES_AT);
        if (expiresAt > 0L) {
            appendLore(stack, Component.literal((en ? "Expires: " : "Wygasa: ")
                + LOG_TIME.format(Instant.ofEpochMilli(expiresAt))).withStyle(ChatFormatting.RED));
        }
    }

    private static RoleData.TravelPosition currentPosition(ServerPlayer player) {
        return new RoleData.TravelPosition(
            player.level().dimension().location().toString(), player.getX(), player.getY(), player.getZ(),
            player.getYRot(), player.getXRot()
        );
    }

    private static void useTravelStaff(ServerPlayer player) {
        if (player.getServer() == null) {
            return;
        }
        if (player.getCooldowns().isOnCooldown(Items.BLAZE_ROD)) {
            return;
        }
        player.getCooldowns().addCooldown(Items.BLAZE_ROD, 20);
        RoleData data = RoleData.get(player.getServer());
        boolean leavingFreedom = isFreedomDimension(player);
        ServerLevel destination;
        RoleData.TravelPosition saved;
        if (leavingFreedom) {
            data.setFreedomPosition(player.getUUID(), currentPosition(player));
            saved = data.mainPosition(player.getUUID());
            destination = saved == null ? player.getServer().overworld() : levelFor(player, saved.dimension());
        } else {
            data.setMainPosition(player.getUUID(), currentPosition(player));
            saved = data.freedomPosition(player.getUUID());
            destination = player.getServer().getLevel(FREEDOM_DIMENSION);
        }
        if (destination == null) {
            player.sendSystemMessage(Component.literal(tr(player,
                "Świat konsultantów nie jest dostępny.", "The consultant world is unavailable."))
                .withStyle(ChatFormatting.RED));
            return;
        }
        if (saved != null && destination.dimension().location().toString().equals(saved.dimension())) {
            player.teleportTo(destination, saved.x(), saved.y(), saved.z(), saved.yaw(), saved.pitch());
        } else {
            BlockPos spawn = destination.getSharedSpawnPos();
            player.teleportTo(destination, spawn.getX() + 0.5D, spawn.getY() + 1.0D, spawn.getZ() + 0.5D,
                destination.getSharedSpawnAngle(), 0.0F);
        }
        player.displayClientMessage(Component.literal(leavingFreedom
            ? tr(player, "Powrót do ostatniej pozycji.", "Returned to your last position.")
            : tr(player, "Świat konsultantów: pełna swoboda.", "Consultant world: unrestricted mode.")
        ).withStyle(ChatFormatting.LIGHT_PURPLE), true);
        audit(player, "TRAVEL", destination.dimension().location().toString());
    }

    private static void useReturnMirror(ServerPlayer player) {
        if (player.getServer() == null || player.getCooldowns().isOnCooldown(Items.ECHO_SHARD)) {
            return;
        }
        int now = player.getServer().getTickCount();
        Integer previous = LAST_MIRROR_USE_TICK.put(player.getUUID(), now);
        boolean forceWorldSpawn = previous != null && now - previous <= 60;
        ServerLevel destination = null;
        net.minecraft.world.phys.Vec3 target = null;
        float yaw = player.getYRot();
        if (!forceWorldSpawn && player.getRespawnPosition() != null) {
            destination = player.getServer().getLevel(player.getRespawnDimension());
            if (destination != null) {
                target = ServerPlayer.findRespawnPositionAndUseSpawnBlock(
                    destination, player.getRespawnPosition(), player.getRespawnAngle(),
                    player.isRespawnForced(), true
                ).orElse(null);
                yaw = player.getRespawnAngle();
            }
        }
        if (target == null) {
            destination = player.getServer().overworld();
            BlockPos spawn = destination.getSharedSpawnPos();
            target = new net.minecraft.world.phys.Vec3(spawn.getX() + 0.5D, spawn.getY() + 1.0D, spawn.getZ() + 0.5D);
            yaw = destination.getSharedSpawnAngle();
        }
        player.teleportTo(destination, target.x, target.y, target.z, yaw, 0.0F);
        player.getCooldowns().addCooldown(Items.ECHO_SHARD, 10);
        player.displayClientMessage(Component.literal(forceWorldSpawn
            ? tr(player, "Powrót na spawn świata.", "Returned to world spawn.")
            : tr(player, "Powrót do punktu odrodzenia.", "Returned to your respawn point."))
            .withStyle(ChatFormatting.AQUA), true);
        audit(player, "RETURN_MIRROR", forceWorldSpawn ? "world_spawn" : "respawn");
    }

    private static ServerLevel levelFor(ServerPlayer player, String dimension) {
        ResourceLocation id = ResourceLocation.tryParse(dimension);
        return id == null || player.getServer() == null
            ? null
            : player.getServer().getLevel(ResourceKey.create(Registries.DIMENSION, id));
    }

    private static boolean isImportedItem(ItemStack stack) {
        return stack.hasTag() && stack.getTag().getBoolean(IMPORTED_ITEM_MARKER);
    }

    private static boolean isOwnedImportedItem(ItemStack stack, Player player) {
        return isImportedItem(stack)
            && player.getUUID().toString().equals(stack.getTag().getString(IMPORTED_ITEM_OWNER));
    }

    private static boolean isOwnedLoot(ItemStack stack, Player player) {
        if (!stack.hasTag()) {
            return false;
        }
        String owner = player.getUUID().toString();
        return owner.equals(stack.getTag().getString(KILL_LOOT_OWNER))
            || owner.equals(stack.getTag().getString(MINE_LOOT_OWNER));
    }

    private static void rememberBlockDrops(ServerPlayer player, BlockPos pos) {
        PENDING_BLOCK_DROPS.put(
            new BlockDropKey(player.level().dimension(), pos.immutable()),
            new PendingBlockDrop(player.getUUID(), player.level().getGameTime())
        );
    }

    private static void importOffhandItem(ServerPlayer player) {
        ItemStack moving = player.getOffhandItem();
        if (moving.isEmpty()) {
            player.displayClientMessage(Component.literal(tr(player,
                "Umieść zwykły przedmiot w drugiej ręce.", "Put an ordinary item in your offhand."))
                .withStyle(ChatFormatting.RED), true);
            return;
        }
        if (isConsultantEquipment(moving)) {
            player.displayClientMessage(Component.literal(tr(player,
                "Przedmiotów konsultanta nie można importować.", "Consultant items cannot be imported."))
                .withStyle(ChatFormatting.RED), true);
            return;
        }
        player.setItemInHand(InteractionHand.OFF_HAND, ItemStack.EMPTY);
        boolean alreadyImported = isImportedItem(moving);
        moving.getOrCreateTag().putBoolean(IMPORTED_ITEM_MARKER, true);
        moving.getOrCreateTag().putString(IMPORTED_ITEM_OWNER, player.getUUID().toString());
        if (!alreadyImported) {
            appendLore(moving, Component.literal(tr(player,
                "Zaimportowano: można wyrzucić i ponownie podnieść.",
                "Imported: this item can be dropped and picked up again."))
                .withStyle(ChatFormatting.DARK_GREEN));
        }
        player.getInventory().add(moving);
        if (!moving.isEmpty()) {
            player.setItemInHand(InteractionHand.OFF_HAND, moving);
        }
        player.inventoryMenu.broadcastChanges();
        player.displayClientMessage(Component.literal(tr(player,
            "Przeniesiono przedmiot do ekwipunku; można go teraz wyrzucić.",
            "Moved the item into your inventory; it can now be dropped."))
            .withStyle(ChatFormatting.GREEN), true);
    }

    private static boolean hasPermissionAction(ItemStack stack, String action) {
        return stack.hasTag()
            && stack.getTag().getBoolean(CONSULTANT_ITEM_MARKER)
            && (stack.getTag().getLong(CONSULTANT_EXPIRES_AT) <= 0L
                || stack.getTag().getLong(CONSULTANT_EXPIRES_AT) > System.currentTimeMillis())
            && action.equals(stack.getTag().getString(CONSULTANT_ACTION));
    }

    private static boolean permissionConditionSatisfied(ItemStack stack, Player player) {
        if (!stack.hasTag()) {
            return false;
        }
        String near = stack.getTag().getString(CONSULTANT_NEAR);
        if (near.isEmpty()) {
            return true;
        }
        int radius = stack.getTag().getInt(CONSULTANT_RADIUS);
        double maximumDistance = (double) radius * radius;
        if (near.equalsIgnoreCase("patients") || near.equalsIgnoreCase("pacjenci")) {
            if (player.getServer() == null) {
                return false;
            }
            for (ServerPlayer candidate : player.getServer().getPlayerList().getPlayers()) {
                if (isPatient(candidate)
                    && candidate.level() == player.level()
                    && player.distanceToSqr(candidate) <= maximumDistance) {
                    return true;
                }
            }
            return false;
        }
        ServerPlayer anchor = player.getServer() == null
            ? null
            : player.getServer().getPlayerList().getPlayerByName(near);
        return anchor != null
            && anchor.level() == player.level()
            && player.distanceToSqr(anchor) <= maximumDistance;
    }

    private static boolean hasPermission(ItemStack stack, Player player, String action) {
        return hasPermissionAction(stack, action) && permissionConditionSatisfied(stack, player);
    }

    private static boolean hasPermission(Player player, String action) {
        for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
            if (hasPermission(player.getInventory().getItem(slot), player, action)) {
                return true;
            }
        }
        return false;
    }

    private static boolean inventoryPermitsBlock(Player player, String action, BlockState state) {
        for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
            if (permitsBlock(player.getInventory().getItem(slot), player, action, state)) {
                return true;
            }
        }
        return false;
    }

    private static boolean inventoryPermitsEntity(Player player, String action, Entity entity) {
        for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
            ItemStack stack = player.getInventory().getItem(slot);
            if (hasPermission(stack, player, action)) {
                ResourceLocation entityId = BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType());
                for (String rawTarget : permissionTargets(stack)) {
                    String target = rawTarget.trim();
                    if (target.equals("*") || matchesEntityPreset(target, entity)) {
                        return true;
                    }
                    if (target.startsWith("#")) {
                        ResourceLocation tagId = ResourceLocation.tryParse(target.substring(1));
                        if (tagId != null && entity.getType().is(TagKey.create(Registries.ENTITY_TYPE, tagId))) {
                            return true;
                        }
                    } else if (entityId.equals(ResourceLocation.tryParse(target))) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    private static List<String> permissionTargets(ItemStack stack) {
        if (!stack.hasTag()) {
            return List.of();
        }
        return List.of(stack.getTag().getString(CONSULTANT_TARGETS).split(","));
    }

    private static boolean matchesBlockPreset(String preset, BlockState state) {
        return switch (preset.toLowerCase()) {
            case "rock", "rocks", "kamien" -> ROCK_BLOCKS.contains(state.getBlock());
            case "ores", "ore", "rudy" -> BuiltInRegistries.BLOCK.getKey(state.getBlock()).getPath().endsWith("_ore")
                || state.is(Blocks.ANCIENT_DEBRIS);
            case "logs", "wood", "drewno" -> state.is(BlockTags.LOGS);
            case "dirt", "soil", "ziemia" -> state.is(BlockTags.DIRT);
            case "sand", "piasek" -> state.is(BlockTags.SAND);
            default -> false;
        };
    }

    private static boolean isBlockPreset(String target) {
        return switch (target.toLowerCase()) {
            case "rock", "rocks", "kamien", "ores", "ore", "rudy", "logs", "wood", "drewno",
                "dirt", "soil", "ziemia", "sand", "piasek" -> true;
            default -> false;
        };
    }

    private static boolean matchesEntityPreset(String preset, Entity entity) {
        return switch (preset.toLowerCase()) {
            case "hostile", "hostiles", "wrogie" -> entity instanceof Enemy;
            case "passive", "friendly", "przyjazne" -> entity instanceof Animal || entity instanceof Villager;
            case "animals", "animal", "zwierzeta" -> entity instanceof Animal;
            case "villagers", "villager", "mieszkancy" -> entity instanceof Villager;
            case "players", "player", "gracze" -> entity instanceof Player;
            case "undead", "nieumarli" -> entity instanceof net.minecraft.world.entity.LivingEntity living
                && living.getMobType() == MobType.UNDEAD;
            case "arthropods", "arthropod", "stawonogi" -> entity instanceof net.minecraft.world.entity.LivingEntity living
                && living.getMobType() == MobType.ARTHROPOD;
            case "aquatic", "water", "wodne" -> entity instanceof net.minecraft.world.entity.LivingEntity living
                && living.getMobType() == MobType.WATER;
            case "illagers", "illager", "najezdzcy" -> entity instanceof AbstractIllager;
            case "mobs", "mob" -> entity instanceof Mob;
            case "bosses", "boss" -> {
                String id = BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType()).toString();
                yield id.equals("minecraft:ender_dragon") || id.equals("minecraft:wither");
            }
            default -> false;
        };
    }

    private static boolean permitsBlock(ItemStack stack, Player player, String action, BlockState state) {
        if (!hasPermission(stack, player, action)) {
            return false;
        }
        ResourceLocation blockId = BuiltInRegistries.BLOCK.getKey(state.getBlock());
        for (String rawTarget : permissionTargets(stack)) {
            String target = rawTarget.trim();
            if (target.equals("*")) {
                return true;
            } else if (matchesBlockPreset(target, state)) {
                return true;
            } else if (target.startsWith("#")) {
                ResourceLocation tagId = ResourceLocation.tryParse(target.substring(1));
                if (tagId != null && state.is(TagKey.create(Registries.BLOCK, tagId))) {
                    return true;
                }
            } else if (blockId.equals(ResourceLocation.tryParse(target))) {
                return true;
            }
        }
        return false;
    }

    private static boolean permitsEntity(ItemStack stack, Player player, Entity entity) {
        if (!hasPermission(stack, player, "attack")) {
            return false;
        }
        ResourceLocation entityId = BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType());
        for (String rawTarget : permissionTargets(stack)) {
            String target = rawTarget.trim();
            if (target.equals("*")) {
                return true;
            } else if (matchesEntityPreset(target, entity)) {
                return true;
            } else if (target.startsWith("#")) {
                ResourceLocation tagId = ResourceLocation.tryParse(target.substring(1));
                if (tagId != null && entity.getType().is(TagKey.create(Registries.ENTITY_TYPE, tagId))) {
                    return true;
                }
            } else if (entityId.equals(ResourceLocation.tryParse(target))) {
                return true;
            }
        }
        return false;
    }

    private static ItemStack makePermissionItem(
        Item item, String action, String targets, String near, int radius, Player player, long expiresAt
    ) {
        ItemStack stack = new ItemStack(item);
        CompoundTag tag = stack.getOrCreateTag();
        tag.putBoolean(CONSULTANT_ITEM_MARKER, true);
        tag.putString(CONSULTANT_ACTION, action);
        tag.putString(CONSULTANT_TARGETS, targets);
        tag.putString(CONSULTANT_NEAR, near);
        tag.putInt(CONSULTANT_RADIUS, radius);
        if (expiresAt > 0L) {
            tag.putLong(CONSULTANT_EXPIRES_AT, expiresAt);
        }
        if (action.equals("mine")) {
            ListTag canDestroy = new ListTag();
            if (List.of(targets.split(",")).stream().map(String::trim).anyMatch("*"::equals)) {
                BuiltInRegistries.BLOCK.keySet().forEach(id -> canDestroy.add(StringTag.valueOf(id.toString())));
            } else {
                for (String target : targets.split(",")) {
                    String trimmed = target.trim();
                    if (isBlockPreset(trimmed)) {
                        BuiltInRegistries.BLOCK.entrySet().stream()
                            .filter(entry -> matchesBlockPreset(trimmed, entry.getValue().defaultBlockState()))
                            .forEach(entry -> canDestroy.add(StringTag.valueOf(entry.getKey().location().toString())));
                    } else {
                        canDestroy.add(StringTag.valueOf(trimmed));
                    }
                }
            }
            tag.put("CanDestroy", canDestroy);
        }
        localizeConsultantItem(stack, player, true);
        return stack;
    }

    private static void extractConsultantItems(ServerPlayer player) {
        SUPPRESS_DEFAULT_ITEMS.add(player.getUUID());
        for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
            if (isConsultantEquipment(player.getInventory().getItem(slot))) {
                player.getInventory().setItem(slot, ItemStack.EMPTY);
            }
        }
        player.inventoryMenu.broadcastChanges();
        player.displayClientMessage(Component.literal(tr(player,
            "Usunięto wszystkie przedmioty konsultanta.", "Removed all consultant items."))
            .withStyle(ChatFormatting.YELLOW), true);
    }

    private static boolean hasSpruceSign(Player player) {
        for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
            if (isSpruceSignItem(player.getInventory().getItem(slot))) {
                return true;
            }
        }
        return false;
    }

    private static boolean hasSignRemover(Player player) {
        for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
            if (isSignRemover(player.getInventory().getItem(slot))) {
                return true;
            }
        }
        return false;
    }

    private static boolean hasTravelStaff(Player player) {
        for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
            if (isTravelStaff(player.getInventory().getItem(slot))) {
                return true;
            }
        }
        return false;
    }

    private static boolean hasWelcomeBook(Player player) {
        for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
            if (isWelcomeBook(player.getInventory().getItem(slot))) {
                return true;
            }
        }
        return false;
    }

    private static void ensureSignRemover(ServerPlayer player) {
        if (!SUPPRESS_DEFAULT_ITEMS.contains(player.getUUID()) && !hasSignRemover(player)) {
            player.getInventory().add(makeSignRemover(player));
            player.inventoryMenu.broadcastChanges();
        }
    }

    private static void ensureTravelStaff(ServerPlayer player) {
        if (!SUPPRESS_DEFAULT_ITEMS.contains(player.getUUID()) && !hasTravelStaff(player)) {
            player.getInventory().add(makeTravelStaff(player));
            player.inventoryMenu.broadcastChanges();
        }
    }

    private static void ensureWelcomeBook(ServerPlayer player) {
        if (!SUPPRESS_DEFAULT_ITEMS.contains(player.getUUID())
            && RoleData.get(player.getServer()).hasLanguage(player.getUUID())
            && !hasWelcomeBook(player)) {
            player.getInventory().add(makeWelcomeBook(player));
            player.inventoryMenu.broadcastChanges();
        }
    }

    private static Component roleName(ServerPlayer player) {
        if (isOperator(player)) {
            return Component.literal(isEnglish(player) ? "[Director] " : "[Ordynator] ").withStyle(ChatFormatting.DARK_PURPLE)
                .append(Component.literal(player.getGameProfile().getName()).withStyle(ChatFormatting.WHITE));
        }
        if (isPatient(player)) {
            return Component.literal(isEnglish(player) ? "[Patient] " : "[Pacjent] ").withStyle(ChatFormatting.LIGHT_PURPLE)
                .append(Component.literal(player.getGameProfile().getName()).withStyle(ChatFormatting.WHITE));
        }
        return Component.literal(isEnglish(player) ? "[Consultant] " : "[Konsultant] ").withStyle(ChatFormatting.AQUA)
            .append(Component.literal(player.getGameProfile().getName()).withStyle(ChatFormatting.WHITE));
    }

    private static void syncCollisionRule(ServerPlayer player) {
        Scoreboard scoreboard = player.getScoreboard();
        PlayerTeam team = scoreboard.getPlayerTeam("konsultanci");
        if (team == null) {
            team = scoreboard.addPlayerTeam("konsultanci");
            team.setCollisionRule(Team.CollisionRule.NEVER);
        }
        String name = player.getScoreboardName();
        if (isConsultant(player)) {
            scoreboard.addPlayerToTeam(name, team);
        } else if (scoreboard.getPlayersTeam(name) == team) {
            scoreboard.removePlayerFromTeam(name, team);
        }
    }

    private static void syncPatientRank(ServerPlayer player) {
        if (player.getServer() == null) {
            return;
        }
        String command = "ftbranks add " + player.getGameProfile().getName() + " member";
        player.getServer().getCommands().performPrefixedCommand(
            player.getServer().createCommandSourceStack().withPermission(4).withSuppressedOutput(), command
        );
    }

    private static void sendLanguagePrompt(ServerPlayer player) {
        player.sendSystemMessage(Component.literal("Użyj /polski dla języka polskiego lub /english for English.")
            .withStyle(ChatFormatting.YELLOW));
    }

    private static void sendConsultantWelcome(ServerPlayer player) {
        boolean en = isEnglish(player);
        player.sendSystemMessage(Component.literal(en
            ? "Welcome, Consultant. You are in Survival mode, but the hospital world protects blocks, entities, containers, and other players from destructive actions."
            : "Witaj, Konsultancie. Jesteś w trybie Survival, ale świat szpitala chroni bloki, istoty, pojemniki i innych graczy przed niszczeniem.").withStyle(ChatFormatting.AQUA));
        player.sendSystemMessage(Component.literal(en
            ? "You have infinite food, no collision, mobs ignore you, and containers are view-only unless a permission item says otherwise. Spruce interfaces and your spruce signs remain usable."
            : "Masz nieskończone nasycenie i brak kolizji, moby cię ignorują, a pojemniki są tylko do podglądu bez odpowiedniego przedmiotu. Świerkowe mechanizmy i twoje tabliczki działają normalnie.").withStyle(ChatFormatting.GRAY));
        player.sendSystemMessage(Component.literal(en
            ? "Permission tools and amulets describe exactly what they enable. The Importer makes ordinary items droppable; ordinary non-consultant items may now always be dropped and recovered."
            : "Narzędzia i amulety uprawnień opisują, co dokładnie umożliwiają. Importer oznacza przedmioty, a zwykłe przedmioty niekonsultanta można zawsze wyrzucać i odzyskiwać.").withStyle(ChatFormatting.GRAY));
        player.sendSystemMessage(Component.literal(en
            ? "Use or drop the Passage Staff to enter the unrestricted consultant world and return to your saved position. Use the Mirror of Returning for your respawn point; use it again quickly for world spawn."
            : "Użyj lub wyrzuć Laskę Przejścia, aby wejść do swobodnego świata konsultantów i wrócić na zapisaną pozycję. Lustro Powrotu prowadzi do punktu odrodzenia, a szybkie ponowne użycie na spawn świata.").withStyle(ChatFormatting.LIGHT_PURPLE));
        player.sendSystemMessage(Component.literal(en
            ? "A patient or director crafts the protected consultant tools for you. Admission codes are redeemed with /przyjecie <code>."
            : "Chronione narzędzia konsultanta wytwarza dla ciebie Pacjent lub Ordynator. Kod przyjęcia wykorzystasz przez /przyjecie <kod>.").withStyle(ChatFormatting.GOLD));
    }

    private static void refreshLocalizedInventory(ServerPlayer player) {
        for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
            localizeConsultantItem(player.getInventory().getItem(slot), player, true);
        }
        player.inventoryMenu.broadcastChanges();
    }

    private static int setLanguage(ServerPlayer player, boolean english) {
        RoleData.get(player.getServer()).setEnglish(player.getUUID(), english);
        audit(player, "LANGUAGE", english ? "english" : "polski");
        ensureWelcomeBook(player);
        refreshLocalizedInventory(player);
        player.refreshTabListName();
        player.sendSystemMessage(Component.literal(english ? "Language set to English." : "Ustawiono język polski.")
            .withStyle(ChatFormatting.GREEN));
        if (isConsultant(player)) {
            sendConsultantWelcome(player);
        }
        return 1;
    }

    @SubscribeEvent
    public void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        if (isPatient(player)) {
            RoleData.get(player.getServer()).addPatient(player.getUUID());
            syncPatientRank(player);
            player.setGameMode(GameType.SURVIVAL);
        } else if (isConsultant(player)) {
            SUPPRESS_DEFAULT_ITEMS.remove(player.getUUID());
            player.setGameMode(GameType.SURVIVAL);
            NEXT_SIGN_GRANT_TICK.put(player.getUUID(), (long) player.getServer().getTickCount() + SIGN_GRANT_INTERVAL_TICKS);
            ensureSignRemover(player);
            ensureTravelStaff(player);
        }
        syncCollisionRule(player);
        player.refreshTabListName();
        sendLanguagePrompt(player);
        if (RoleData.get(player.getServer()).hasLanguage(player.getUUID()) && isConsultant(player)) {
            ensureWelcomeBook(player);
            sendConsultantWelcome(player);
        }
        audit(player, "LOGIN", isOperator(player) ? "ordynator" : isPatient(player) ? "pacjent" : "konsultant");
    }

    @SubscribeEvent
    public void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase == TickEvent.Phase.END && isConsultant(event.player)) {
            ServerPlayer player = (ServerPlayer) event.player;
            if (player.gameMode.getGameModeForPlayer() != GameType.SURVIVAL) {
                player.setGameMode(GameType.SURVIVAL);
            }
            player.getFoodData().setFoodLevel(20);
            player.getFoodData().setSaturation(20.0F);
            if (player.containerMenu != player.inventoryMenu) {
                ContainerSnapshot snapshot = CONTAINER_SNAPSHOTS.get(player.getUUID());
                if (snapshot == null || snapshot.containerId() != player.containerMenu.containerId) {
                    CONTAINER_SNAPSHOTS.put(player.getUUID(), ContainerSnapshot.capture(player));
                } else if (snapshot.hasConsultantDeposit(player)) {
                    snapshot.restore(player);
                    auditDenied(player, "DENY_CONTAINER_DEPOSIT", "consultant_item");
                    player.displayClientMessage(Component.literal(tr(player,
                        "Przedmiotów konsultanta nie można wkładać do pojemników.",
                        "Consultant items cannot be placed in containers."))
                        .withStyle(ChatFormatting.RED), true);
                } else if (isRestrictedConsultant(player) && !hasPermission(player, "take")) {
                    snapshot.restore(player);
                } else {
                    CONTAINER_SNAPSHOTS.put(player.getUUID(), ContainerSnapshot.capture(player));
                }
            } else {
                CONTAINER_SNAPSHOTS.remove(player.getUUID());
            }

            long now = player.getServer().getTickCount();
            long nextGrant = NEXT_SIGN_GRANT_TICK.computeIfAbsent(
                player.getUUID(), ignored -> now + SIGN_GRANT_INTERVAL_TICKS
            );
            if (now >= nextGrant) {
                NEXT_SIGN_GRANT_TICK.put(player.getUUID(), now + SIGN_GRANT_INTERVAL_TICKS);
                if (!SUPPRESS_DEFAULT_ITEMS.contains(player.getUUID()) && !hasSpruceSign(player)) {
                    player.getInventory().add(makePlaceableSpruceSign(player));
                    player.inventoryMenu.broadcastChanges();
                }
            }
            if (player.tickCount % 20 == 0) {
                ensureSignRemover(player);
                ensureTravelStaff(player);
                ensureWelcomeBook(player);
                for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
                    ItemStack item = player.getInventory().getItem(slot);
                    if (item.hasTag() && item.getTag().getLong(CONSULTANT_EXPIRES_AT) > 0L
                        && item.getTag().getLong(CONSULTANT_EXPIRES_AT) <= System.currentTimeMillis()) {
                        audit(player, "ITEM_EXPIRED", BuiltInRegistries.ITEM.getKey(item.getItem()).toString());
                        player.getInventory().setItem(slot, ItemStack.EMPTY);
                        player.displayClientMessage(Component.literal(tr(player,
                            "Wygasł przedmiot uprawnień.", "A permission item expired."))
                            .withStyle(ChatFormatting.YELLOW), true);
                    } else {
                        localizeConsultantItem(item, player, false);
                    }
                }
                long cleanupTime = player.level().getGameTime();
                PENDING_BLOCK_DROPS.entrySet().removeIf(entry ->
                    entry.getKey().dimension().equals(player.level().dimension())
                        && cleanupTime - entry.getValue().gameTime() > 5
                );
            }
        }
    }

    @SubscribeEvent
    public void onContainerOpen(PlayerContainerEvent.Open event) {
        if (event.getEntity() instanceof ServerPlayer player
            && isConsultant(player)) {
            CONTAINER_SNAPSHOTS.put(player.getUUID(), ContainerSnapshot.capture(player));
        }
    }

    @SubscribeEvent
    public void onContainerClose(PlayerContainerEvent.Close event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            ContainerSnapshot snapshot = CONTAINER_SNAPSHOTS.remove(player.getUUID());
            if (snapshot != null && (snapshot.hasConsultantDeposit(player)
                || (isRestrictedConsultant(player) && !hasPermission(player, "take")))) {
                snapshot.restore(player);
            }
        }
    }

    @SubscribeEvent
    public void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            ContainerSnapshot snapshot = CONTAINER_SNAPSHOTS.remove(player.getUUID());
            if (snapshot != null && (snapshot.hasConsultantDeposit(player)
                || (isRestrictedConsultant(player) && !hasPermission(player, "take")))) {
                snapshot.restore(player);
            }
            NEXT_SIGN_GRANT_TICK.remove(player.getUUID());
            LAST_MIRROR_USE_TICK.remove(player.getUUID());
        }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void onTabName(PlayerEvent.TabListNameFormat event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            event.setDisplayName(roleName(player));
        }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void onDisplayName(PlayerEvent.NameFormat event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            event.setDisplayname(roleName(player));
        }
    }

    @SubscribeEvent
    public void onContainerInteraction(PlayerInteractEvent.RightClickBlock event) {
        if (isTravelStaff(event.getItemStack()) && event.getEntity() instanceof ServerPlayer player) {
            useTravelStaff(player);
            event.setCanceled(true);
            event.setCancellationResult(InteractionResult.SUCCESS);
            return;
        }
        if (isReturnMirror(event.getItemStack()) && event.getEntity() instanceof ServerPlayer player) {
            useReturnMirror(player);
            event.setCanceled(true);
            event.setCancellationResult(InteractionResult.SUCCESS);
            return;
        }
        var state = event.getLevel().getBlockState(event.getPos());
        boolean isContainer = event.getLevel().getBlockEntity(event.getPos()) instanceof Container
            || state.getMenuProvider(event.getLevel(), event.getPos()) != null;
        if (isConsultant(event.getEntity())
            && isConsultantEquipment(event.getItemStack())
            && !isPlaceableSpruceSign(event.getItemStack())) {
            if (isContainer) {
                event.setUseItem(net.minecraftforge.eventbus.api.Event.Result.DENY);
            } else {
                event.setCanceled(true);
                event.setCancellationResult(InteractionResult.FAIL);
            }
            return;
        }
        if (!isRestrictedConsultant(event.getEntity())) {
            return;
        }
        if (isPlaceableSpruceSign(event.getItemStack())) {
            return;
        }
        if (event.getItemStack().getItem() instanceof BlockItem blockItem
            && (permitsBlock(event.getEntity().getOffhandItem(), event.getEntity(), "place", blockItem.getBlock().defaultBlockState())
                || inventoryPermitsBlock(event.getEntity(), "place-amulet", blockItem.getBlock().defaultBlockState()))) {
            event.setUseItem(net.minecraftforge.eventbus.api.Event.Result.ALLOW);
            return;
        }
        if (isContainer || isSpruceBlock(state)) {
            event.setUseItem(net.minecraftforge.eventbus.api.Event.Result.DENY);
            return;
        }
        event.setCanceled(true);
        event.setCancellationResult(InteractionResult.FAIL);
        event.getEntity().displayClientMessage(
            Component.literal(tr(event.getEntity(),
                "Konsultanci mogą używać bloków świerkowych i przeglądać pojemniki.",
                "Consultants can use spruce blocks and inspect containers."))
                .withStyle(ChatFormatting.RED), true
        );
    }

    @SubscribeEvent
    public void onContainerEntityInteraction(PlayerInteractEvent.EntityInteract event) {
        if (isTravelStaff(event.getItemStack()) && event.getEntity() instanceof ServerPlayer player) {
            useTravelStaff(player);
            event.setCanceled(true);
            event.setCancellationResult(InteractionResult.SUCCESS);
            return;
        }
        if (isReturnMirror(event.getItemStack()) && event.getEntity() instanceof ServerPlayer player) {
            useReturnMirror(player);
            event.setCanceled(true);
            event.setCancellationResult(InteractionResult.SUCCESS);
            return;
        }
        if (isConsultant(event.getEntity()) && isConsultantEquipment(event.getItemStack())
            && !(event.getTarget() instanceof Container)) {
            event.setCanceled(true);
            event.setCancellationResult(InteractionResult.FAIL);
            return;
        }
        if (isRestrictedConsultant(event.getEntity()) && !(event.getTarget() instanceof Container)) {
            event.setCanceled(true);
            event.setCancellationResult(InteractionResult.FAIL);
        }
    }

    @SubscribeEvent
    public void onSpecificEntityInteraction(PlayerInteractEvent.EntityInteractSpecific event) {
        if (isTravelStaff(event.getItemStack()) && event.getEntity() instanceof ServerPlayer player) {
            useTravelStaff(player);
            event.setCanceled(true);
            event.setCancellationResult(InteractionResult.SUCCESS);
            return;
        }
        if (isReturnMirror(event.getItemStack()) && event.getEntity() instanceof ServerPlayer player) {
            useReturnMirror(player);
            event.setCanceled(true);
            event.setCancellationResult(InteractionResult.SUCCESS);
            return;
        }
        if (isConsultant(event.getEntity()) && isConsultantEquipment(event.getItemStack())) {
            event.setCanceled(true);
            event.setCancellationResult(InteractionResult.FAIL);
            return;
        }
        if (isRestrictedConsultant(event.getEntity())) {
            event.setCanceled(true);
            event.setCancellationResult(InteractionResult.FAIL);
        }
    }

    @SubscribeEvent
    public void onItemUse(PlayerInteractEvent.RightClickItem event) {
        ItemStack stack = event.getItemStack();
        if (isTravelStaff(stack) && event.getEntity() instanceof ServerPlayer player) {
            useTravelStaff(player);
            event.setCanceled(true);
            event.setCancellationResult(InteractionResult.SUCCESS);
            return;
        }
        if (isReturnMirror(stack) && event.getEntity() instanceof ServerPlayer player) {
            useReturnMirror(player);
            event.setCanceled(true);
            event.setCancellationResult(InteractionResult.SUCCESS);
            return;
        }
        if (!isConsultant(event.getEntity())) {
            return;
        }
        if (isExtractor(stack) && event.getEntity() instanceof ServerPlayer player) {
            extractConsultantItems(player);
            event.setCanceled(true);
            event.setCancellationResult(InteractionResult.SUCCESS);
            return;
        }
        if (event.getHand() == InteractionHand.MAIN_HAND
            && isImporter(stack)
            && event.getEntity() instanceof ServerPlayer player) {
            importOffhandItem(player);
            event.setCanceled(true);
            event.setCancellationResult(InteractionResult.SUCCESS);
            return;
        }
        if (isFreedomDimension(event.getEntity())) {
            return;
        }
        if (!stack.isEdible() && !isSpruceSignItem(stack) && !isWelcomeBook(stack)) {
            event.setCanceled(true);
            event.setCancellationResult(InteractionResult.FAIL);
        }
    }

    @SubscribeEvent
    public void onFarmlandTrample(BlockEvent.FarmlandTrampleEvent event) {
        if (event.getEntity() instanceof Player player && isRestrictedConsultant(player)) {
            event.setCanceled(true);
            auditDenied(player, "DENY_TRAMPLE", event.getPos().toShortString());
        }
    }

    @SubscribeEvent
    public void onBreak(BlockEvent.BreakEvent event) {
        if (isRestrictedConsultant(event.getPlayer())) {
            ItemStack held = event.getPlayer().getMainHandItem();
            boolean signAllowed = isSpruceSign(event.getState()) && isSignRemover(held);
            boolean stoneAllowed = (event.getState().is(Blocks.STONE) || event.getState().is(Blocks.COBBLESTONE))
                && isConsultantPickaxe(held);
            boolean permissionAllowed = permitsBlock(held, event.getPlayer(), "mine", event.getState());
            boolean amuletAllowed = inventoryPermitsBlock(event.getPlayer(), "mine-amulet", event.getState());
            if (!signAllowed && !stoneAllowed && !permissionAllowed && !amuletAllowed) {
                event.setCanceled(true);
                auditDenied(event.getPlayer(), "DENY_BREAK", BuiltInRegistries.BLOCK.getKey(event.getState().getBlock()).toString());
            } else if (event.getPlayer() instanceof ServerPlayer player) {
                rememberBlockDrops(player, event.getPos());
            }
        }
    }

    @SubscribeEvent
    public void onItemEntitySpawn(EntityJoinLevelEvent event) {
        if (!(event.getLevel() instanceof ServerLevel level) || !(event.getEntity() instanceof ItemEntity item)) {
            return;
        }
        BlockDropKey key = new BlockDropKey(level.dimension(), item.blockPosition());
        PendingBlockDrop pending = PENDING_BLOCK_DROPS.get(key);
        if (pending != null && level.getGameTime() - pending.gameTime() <= 5) {
            item.getItem().getOrCreateTag().putString(MINE_LOOT_OWNER, pending.owner().toString());
        }
    }

    @SubscribeEvent
    public void onPlace(BlockEvent.EntityPlaceEvent event) {
        if (event.getEntity() instanceof Player player
            && isConsultant(player)
            && !isFreedomDimension(player)
            && !isSpruceSign(event.getPlacedBlock())
            && !permitsBlock(player.getOffhandItem(), player, "place", event.getPlacedBlock())
            && !inventoryPermitsBlock(player, "place-amulet", event.getPlacedBlock())) {
            event.setCanceled(true);
            auditDenied(player, "DENY_PLACE", BuiltInRegistries.BLOCK.getKey(event.getPlacedBlock().getBlock()).toString());
        }
    }

    @SubscribeEvent
    public void onPvp(LivingAttackEvent event) {
        boolean victimIsConsultant = event.getEntity() instanceof Player victim && isRestrictedConsultant(victim);
        boolean attackerIsConsultant = event.getSource().getEntity() instanceof Player attacker && isRestrictedConsultant(attacker);
        boolean pvpAgainstConsultant = victimIsConsultant && event.getSource().getEntity() instanceof Player;
        boolean allowedHostileAttack = event.getEntity() instanceof Enemy
            && event.getSource().getEntity() instanceof Player attacker
            && isConsultantSword(attacker.getMainHandItem());
        boolean allowedByPermission = event.getSource().getEntity() instanceof Player attacker
            && permitsEntity(attacker.getMainHandItem(), attacker, event.getEntity());
        boolean allowedByAmulet = event.getSource().getEntity() instanceof Player attacker
            && inventoryPermitsEntity(attacker, "attack-amulet", event.getEntity());
        if (pvpAgainstConsultant || (attackerIsConsultant && !allowedHostileAttack && !allowedByPermission && !allowedByAmulet)) {
            event.setCanceled(true);
            if (event.getSource().getEntity() instanceof Player attacker) {
                auditDenied(attacker, "DENY_ATTACK", BuiltInRegistries.ENTITY_TYPE.getKey(event.getEntity().getType()).toString());
            }
        }
    }

    @SubscribeEvent
    public void onAttackEntity(AttackEntityEvent event) {
        if (isRestrictedConsultant(event.getEntity())
            && (!(event.getTarget() instanceof Enemy)
                || !isConsultantSword(event.getEntity().getMainHandItem()))
            && !permitsEntity(event.getEntity().getMainHandItem(), event.getEntity(), event.getTarget())
            && !inventoryPermitsEntity(event.getEntity(), "attack-amulet", event.getTarget())) {
            event.setCanceled(true);
            auditDenied(event.getEntity(), "DENY_ATTACK", BuiltInRegistries.ENTITY_TYPE.getKey(event.getTarget().getType()).toString());
        }
    }

    @SubscribeEvent
    public void onMobTarget(LivingChangeTargetEvent event) {
        if (event.getNewTarget() instanceof Player player && isRestrictedConsultant(player)) {
            Provocation provocation = HOSTILE_PROVOCATIONS.get(event.getEntity().getUUID());
            int now = player.getServer() == null ? Integer.MAX_VALUE : player.getServer().getTickCount();
            if (provocation == null
                || !provocation.consultant().equals(player.getUUID())
                || provocation.expiresAtTick() < now) {
                event.setNewTarget(null);
            }
        }
    }

    @SubscribeEvent
    public void onHostileProvoked(LivingHurtEvent event) {
        if (event.getEntity() instanceof Enemy
            && event.getSource().getEntity() instanceof ServerPlayer attacker
            && isRestrictedConsultant(attacker)
            && attacker.getServer() != null) {
            HOSTILE_PROVOCATIONS.put(event.getEntity().getUUID(), new Provocation(
                attacker.getUUID(), attacker.getServer().getTickCount() + HOSTILE_ANGER_TICKS
            ));
            audit(attacker, "HOSTILE_PROVOKED", BuiltInRegistries.ENTITY_TYPE.getKey(event.getEntity().getType()).toString());
        }
    }

    @SubscribeEvent
    public void onHostileTick(LivingEvent.LivingTickEvent event) {
        if (!(event.getEntity() instanceof Mob mob)
            || !(mob instanceof Enemy)
            || mob.tickCount % 20 != 0
            || !(mob.getTarget() instanceof ServerPlayer target)
            || !isRestrictedConsultant(target)) {
            return;
        }
        Provocation provocation = HOSTILE_PROVOCATIONS.get(mob.getUUID());
        int now = target.getServer() == null ? Integer.MAX_VALUE : target.getServer().getTickCount();
        if (provocation == null
            || !provocation.consultant().equals(target.getUUID())
            || provocation.expiresAtTick() < now) {
            HOSTILE_PROVOCATIONS.remove(mob.getUUID());
            mob.setTarget(null);
        }
    }

    @SubscribeEvent
    public void onItemPickup(EntityItemPickupEvent event) {
        if (isRestrictedConsultant(event.getEntity())) {
            if (hasPermission(event.getEntity(), "pickup")) {
                return;
            }
            ItemStack stack = event.getItem().getItem();
            if (isOwnedImportedItem(stack, event.getEntity()) || isOwnedLoot(stack, event.getEntity())) {
                if (stack.hasTag()) {
                    stack.getTag().remove(KILL_LOOT_OWNER);
                    stack.getTag().remove(MINE_LOOT_OWNER);
                }
                return;
            } else if (isSpruceSignItem(stack)) {
                makeSpruceSignsPlaceable(stack, event.getEntity());
            } else if (isConsultantEquipment(stack)) {
                return;
            } else {
                event.setCanceled(true);
                auditDenied(event.getEntity(), "DENY_PICKUP", BuiltInRegistries.ITEM.getKey(stack.getItem()).toString());
            }
        }
    }

    @SubscribeEvent
    public void onItemCrafted(PlayerEvent.ItemCraftedEvent event) {
        ItemStack crafted = event.getCrafting();
        if (!isConsultantEquipment(crafted)) {
            return;
        }
        String craftedId = BuiltInRegistries.ITEM.getKey(crafted.getItem()).toString();
        if (isConsultant(event.getEntity()) && !isReturnMirror(crafted)) {
            event.getCrafting().setCount(0);
            event.getEntity().displayClientMessage(Component.literal(tr(event.getEntity(),
                "Przedmioty konsultanta musi wytworzyć i przekazać Pacjent lub Ordynator.",
                "A Patient or Director must craft and hand over protected consultant items."
            )).withStyle(ChatFormatting.RED), true);
            auditDenied(event.getEntity(), "DENY_CRAFT", craftedId);
            return;
        }
        localizeConsultantItem(crafted, event.getEntity(), true);
        audit(event.getEntity(), "CONSULTANT_ITEM_CRAFTED", craftedId);
    }

    @SubscribeEvent
    public void onPlayerDrops(LivingDropsEvent event) {
        if (event.getEntity() instanceof Mob
            && event.getSource().getEntity() instanceof Player killer
            && isConsultant(killer)) {
            String owner = killer.getUUID().toString();
            event.getDrops().forEach(item -> item.getItem().getOrCreateTag().putString(KILL_LOOT_OWNER, owner));
        }
        if (event.getEntity() instanceof Player player && isConsultant(player)) {
            event.getDrops().removeIf(item -> isConsultantEquipment(item.getItem()));
        }
    }

    @SubscribeEvent
    public void onItemToss(ItemTossEvent event) {
        if (event.getPlayer() instanceof ServerPlayer player
            && isConsultant(player)
            && isTravelStaff(event.getEntity().getItem())) {
            ItemStack returned = event.getEntity().getItem().copy();
            player.getInventory().add(returned);
            player.inventoryMenu.broadcastChanges();
            event.setCanceled(true);
            useTravelStaff(player);
            return;
        }
        if (isConsultant(event.getPlayer()) && isConsultantEquipment(event.getEntity().getItem())) {
            ItemStack returned = event.getEntity().getItem().copy();
            event.getPlayer().getInventory().add(returned);
            event.getPlayer().inventoryMenu.broadcastChanges();
            event.setCanceled(true);
        } else if (isConsultant(event.getPlayer())) {
            event.getEntity().getItem().getOrCreateTag().putString(KILL_LOOT_OWNER, event.getPlayer().getUUID().toString());
        }
    }

    @SubscribeEvent
    public void onMount(EntityMountEvent event) {
        if (event.isMounting() && event.getEntityMounting() instanceof Player player && isRestrictedConsultant(player)) {
            event.setCanceled(true);
            auditDenied(player, "DENY_MOUNT", BuiltInRegistries.ENTITY_TYPE.getKey(event.getEntityBeingMounted().getType()).toString());
            player.displayClientMessage(
                Component.literal(tr(player,
                    "Konsultanci nie mogą wsiadać do pojazdów ani na wierzchowce.",
                    "Consultants cannot enter vehicles or mount entities here."))
                    .withStyle(ChatFormatting.RED), true
            );
        }
    }

    @SubscribeEvent
    public void onConsultantSleep(PlayerSleepInBedEvent event) {
        if (isRestrictedConsultant(event.getEntity())) {
            event.setResult(Player.BedSleepingProblem.OTHER_PROBLEM);
            event.getEntity().displayClientMessage(Component.literal(tr(event.getEntity(),
                "Konsultanci nie są liczeni do limitu snu.",
                "Consultants are excluded from the sleep requirement."))
                .withStyle(ChatFormatting.YELLOW), true);
        }
    }

    @SubscribeEvent
    public void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || event.getServer().getTickCount() % 20 != 0) {
            return;
        }
        var server = event.getServer();
        int currentTick = server.getTickCount();
        HOSTILE_PROVOCATIONS.entrySet().removeIf(entry -> entry.getValue().expiresAtTick() < currentTick);
        long auditCutoff = System.currentTimeMillis() - 60_000L;
        LAST_AUDIT.entrySet().removeIf(entry -> entry.getValue() < auditCutoff);
        GameRules.IntegerValue rule = server.overworld().getGameRules().getRule(GameRules.RULE_PLAYERS_SLEEPING_PERCENTAGE);
        RoleData data = RoleData.get(server);
        int base = data.baseSleepPercentage(rule.get());
        List<ServerPlayer> active = server.overworld().players().stream()
            .filter(player -> !player.isSpectator()).toList();
        if (active.isEmpty()) {
            if (rule.get() != base) rule.set(base, server);
            return;
        }
        long eligible = active.stream().filter(player -> !isConsultant(player)).count();
        int desiredSleeping = (int) Math.ceil(eligible * base / 100.0D);
        int adjusted = desiredSleeping <= 0
            ? base
            : (int) Math.floor((desiredSleeping - 1) * 100.0D / active.size()) + 1;
        adjusted = Math.max(0, Math.min(100, adjusted));
        if (rule.get() != adjusted) {
            rule.set(adjusted, server);
        }
    }

    @SubscribeEvent
    public void onRegisterCommands(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("przyjecie")
            .then(Commands.argument("kod", StringArgumentType.word())
                .executes(context -> redeem(
                    context.getSource().getPlayerOrException(),
                    StringArgumentType.getString(context, "kod")
                ))));

        event.getDispatcher().register(Commands.literal("przyjecie-kod")
            .requires(source -> source.hasPermission(4))
            .then(Commands.literal("generuj")
                .executes(context -> generate(context.getSource(), 1))
                .then(Commands.argument("liczba", IntegerArgumentType.integer(1, 50))
                    .executes(context -> generate(
                        context.getSource(), IntegerArgumentType.getInteger(context, "liczba")
                    ))))
            .then(Commands.literal("lista")
                .executes(context -> list(context.getSource()))));

        event.getDispatcher().register(Commands.literal("konsultant-item")
            .requires(source -> source.hasPermission(4))
            .then(Commands.literal("clear")
                .then(Commands.argument("gracz", StringArgumentType.word())
                    .executes(context -> clearConsultantItems(
                        context.getSource(), StringArgumentType.getString(context, "gracz")
                    ))))
            .then(Commands.literal("give")
                .then(Commands.argument("gracz", EntityArgument.player())
                    .then(Commands.argument("item", ResourceLocationArgument.id())
                        .then(permissionNoTargetsCommand("take"))
                        .then(permissionNoTargetsCommand("pickup"))
                        .then(permissionTargetsCommand("attack"))
                        .then(permissionTargetsCommand("mine"))
                        .then(permissionTargetsCommand("place"))
                        .then(permissionTargetsCommand("attack-amulet"))
                        .then(permissionTargetsCommand("mine-amulet"))
                        .then(permissionTargetsCommand("place-amulet")))))
            .then(Commands.literal("give-timed")
                .then(Commands.argument("gracz", EntityArgument.player())
                    .then(Commands.argument("item", ResourceLocationArgument.id())
                        .then(Commands.argument("sekundy", IntegerArgumentType.integer(1, 31_536_000))
                            .then(permissionNoTargetsCommand("take", true))
                            .then(permissionNoTargetsCommand("pickup", true))
                            .then(permissionTargetsCommand("attack", true))
                            .then(permissionTargetsCommand("mine", true))
                            .then(permissionTargetsCommand("place", true))
                            .then(permissionTargetsCommand("attack-amulet", true))
                            .then(permissionTargetsCommand("mine-amulet", true))
                            .then(permissionTargetsCommand("place-amulet", true))))))
            .then(Commands.literal("preset")
                .then(Commands.literal("list")
                    .executes(context -> listPresets(context.getSource())))
                .then(Commands.literal("give")
                    .then(Commands.argument("gracz", EntityArgument.player())
                        .then(Commands.argument("preset", StringArgumentType.word())
                            .suggests((context, builder) -> SharedSuggestionProvider.suggest(ITEM_PRESETS, builder))
                            .executes(context -> givePreset(
                                context.getSource(), EntityArgument.getPlayer(context, "gracz"),
                                StringArgumentType.getString(context, "preset"), 0L)))))
                .then(Commands.literal("give-timed")
                    .then(Commands.argument("gracz", EntityArgument.player())
                        .then(Commands.argument("preset", StringArgumentType.word())
                            .suggests((context, builder) -> SharedSuggestionProvider.suggest(ITEM_PRESETS, builder))
                            .then(Commands.argument("sekundy", IntegerArgumentType.integer(1, 31_536_000))
                                .executes(context -> givePreset(
                                    context.getSource(), EntityArgument.getPlayer(context, "gracz"),
                                    StringArgumentType.getString(context, "preset"),
                                    expiryFromContext(context)))))))));

        event.getDispatcher().register(Commands.literal("konsultant")
            .then(Commands.literal("status")
                .executes(context -> showStatus(context.getSource(), context.getSource().getPlayerOrException()))
                .then(Commands.argument("gracz", EntityArgument.player())
                    .requires(source -> source.hasPermission(4))
                    .executes(context -> showStatus(context.getSource(), EntityArgument.getPlayer(context, "gracz"))))));

        event.getDispatcher().register(Commands.literal("konsultant-log")
            .requires(source -> source.hasPermission(4))
            .executes(context -> showAudit(context.getSource(), "", 1))
            .then(Commands.literal("page")
                .then(Commands.argument("strona", IntegerArgumentType.integer(1))
                    .executes(context -> showAudit(context.getSource(), "", IntegerArgumentType.getInteger(context, "strona")))))
            .then(Commands.literal("player")
                .then(Commands.argument("gracz", StringArgumentType.word())
                    .executes(context -> showAudit(context.getSource(), StringArgumentType.getString(context, "gracz"), 1))
                    .then(Commands.argument("strona", IntegerArgumentType.integer(1))
                        .executes(context -> showAudit(context.getSource(), StringArgumentType.getString(context, "gracz"),
                            IntegerArgumentType.getInteger(context, "strona"))))))
            .then(Commands.literal("clear")
                .executes(context -> clearAudit(context.getSource()))));

        event.getDispatcher().register(Commands.literal("polski")
            .executes(context -> setLanguage(context.getSource().getPlayerOrException(), false)));
        event.getDispatcher().register(Commands.literal("english")
            .executes(context -> setLanguage(context.getSource().getPlayerOrException(), true)));
    }

    private static int clearConsultantItems(net.minecraft.commands.CommandSourceStack source, String playerName) {
        var server = source.getServer();
        ServerPlayer online = server.getPlayerList().getPlayerByName(playerName);
        if (online != null) {
            backupPlayerData(server, online.getUUID());
            int removed = 0;
            for (int slot = 0; slot < online.getInventory().getContainerSize(); slot++) {
                if (isConsultantEquipment(online.getInventory().getItem(slot))) {
                    online.getInventory().setItem(slot, ItemStack.EMPTY);
                    removed++;
                }
            }
            online.inventoryMenu.broadcastChanges();
            int finalRemoved = removed;
            source.sendSuccess(() -> Component.literal("Usunięto przedmioty konsultanta: " + finalRemoved), true);
            audit(server, source.getTextName(), "ITEM_CLEAR", playerName + " removed=" + finalRemoved);
            return 1;
        }
        var profile = server.getProfileCache().get(playerName);
        if (profile.isEmpty()) {
            source.sendFailure(Component.literal("Nie znaleziono gracza: " + playerName));
            return 0;
        }
        File file = server.getWorldPath(LevelResource.PLAYER_DATA_DIR)
            .resolve(profile.get().getId() + ".dat").toFile();
        if (!file.isFile()) {
            source.sendFailure(Component.literal("Brak zapisanych danych gracza: " + playerName));
            return 0;
        }
        try {
            backupPlayerData(server, profile.get().getId());
            CompoundTag playerTag = NbtIo.readCompressed(file);
            int removed = removeConsultantEntries(playerTag.getList("Inventory", Tag.TAG_COMPOUND));
            removed += removeConsultantEntries(playerTag.getList("EnderItems", Tag.TAG_COMPOUND));
            NbtIo.writeCompressed(playerTag, file);
            int finalRemoved = removed;
            source.sendSuccess(() -> Component.literal(
                "Usunięto zapisane przedmioty konsultanta gracza " + playerName + ": " + finalRemoved
            ), true);
            audit(server, source.getTextName(), "ITEM_CLEAR_OFFLINE", playerName + " removed=" + finalRemoved);
            return 1;
        } catch (IOException exception) {
            source.sendFailure(Component.literal("Nie udało się odczytać danych gracza: " + exception.getMessage()));
            return 0;
        }
    }

    private static int removeConsultantEntries(ListTag items) {
        int before = items.size();
        items.removeIf(raw -> isRecognizedSavedConsultantItem((CompoundTag) raw));
        return before - items.size();
    }

    private static boolean isRecognizedSavedConsultantItem(CompoundTag savedStack) {
        CompoundTag tag = savedStack.getCompound("tag");
        if (!tag.getBoolean(CONSULTANT_ITEM_MARKER)) {
            return false;
        }
        String itemId = savedStack.getString("id");
        return tag.getBoolean(SIGN_REMOVER_MARKER)
            || tag.getBoolean(CONSULTANT_SWORD_MARKER)
            || tag.getBoolean(CONSULTANT_PICKAXE_MARKER)
            || tag.getBoolean(EXTRACTOR_MARKER)
            || tag.getBoolean(IMPORTER_MARKER)
            || tag.getBoolean(TRAVEL_STAFF_MARKER)
            || tag.getBoolean(RETURN_MIRROR_MARKER)
            || tag.getBoolean(WELCOME_BOOK_MARKER)
            || tag.contains(CONSULTANT_ACTION, Tag.TAG_STRING)
            || ((itemId.equals("minecraft:spruce_sign") || itemId.equals("minecraft:spruce_hanging_sign"))
                && tag.contains("CanPlaceOn", Tag.TAG_LIST));
    }

    private static void backupPlayerData(net.minecraft.server.MinecraftServer server, UUID playerId) {
        File source = server.getWorldPath(LevelResource.PLAYER_DATA_DIR).resolve(playerId + ".dat").toFile();
        if (!source.isFile()) {
            return;
        }
        File backup = new File(source.getParentFile(), source.getName() + ".psychiatryk-backup-" + Instant.now().toEpochMilli());
        try {
            Files.copy(source.toPath(), backup.toPath(), StandardCopyOption.COPY_ATTRIBUTES);
        } catch (IOException ignored) {
        }
    }

    private static com.mojang.brigadier.builder.LiteralArgumentBuilder<net.minecraft.commands.CommandSourceStack>
    permissionNoTargetsCommand(String action) {
        return permissionNoTargetsCommand(action, false);
    }

    private static com.mojang.brigadier.builder.LiteralArgumentBuilder<net.minecraft.commands.CommandSourceStack>
    permissionNoTargetsCommand(String action, boolean timed) {
        return Commands.literal(action)
            .executes(context -> givePermissionItem(
                context.getSource(), EntityArgument.getPlayer(context, "gracz"),
                ResourceLocationArgument.getId(context, "item"), action, "", "", 0,
                timed ? expiryFromContext(context) : 0L
            ))
            .then(permissionNearCommand(action, false, timed));
    }

    private static com.mojang.brigadier.builder.LiteralArgumentBuilder<net.minecraft.commands.CommandSourceStack>
    permissionTargetsCommand(String action) {
        return permissionTargetsCommand(action, false);
    }

    private static com.mojang.brigadier.builder.LiteralArgumentBuilder<net.minecraft.commands.CommandSourceStack>
    permissionTargetsCommand(String action, boolean timed) {
        return Commands.literal(action)
            .then(Commands.argument("cele", StringArgumentType.greedyString())
                .executes(context -> givePermissionItem(
                    context.getSource(), EntityArgument.getPlayer(context, "gracz"),
                    ResourceLocationArgument.getId(context, "item"), action,
                    StringArgumentType.getString(context, "cele"), "", 0,
                    timed ? expiryFromContext(context) : 0L
                )))
            .then(permissionNearCommand(action, true, timed));
    }

    private static com.mojang.brigadier.builder.LiteralArgumentBuilder<net.minecraft.commands.CommandSourceStack>
    permissionNearCommand(String action, boolean hasTargets, boolean timed) {
        var radius = Commands.argument("promien", IntegerArgumentType.integer(1, 512));
        if (hasTargets) {
            radius.then(Commands.argument("cele", StringArgumentType.greedyString())
                .executes(context -> givePermissionItem(
                    context.getSource(), EntityArgument.getPlayer(context, "gracz"),
                    ResourceLocationArgument.getId(context, "item"), action,
                    StringArgumentType.getString(context, "cele"),
                    StringArgumentType.getString(context, "kotwica"),
                    IntegerArgumentType.getInteger(context, "promien"), timed ? expiryFromContext(context) : 0L
                )));
        } else {
            radius.executes(context -> givePermissionItem(
                context.getSource(), EntityArgument.getPlayer(context, "gracz"),
                ResourceLocationArgument.getId(context, "item"), action, "",
                StringArgumentType.getString(context, "kotwica"),
                IntegerArgumentType.getInteger(context, "promien"), timed ? expiryFromContext(context) : 0L
            ));
        }
        return Commands.literal("near")
            .then(Commands.argument("kotwica", StringArgumentType.word()).then(radius));
    }

    private static int givePermissionItem(
        net.minecraft.commands.CommandSourceStack source,
        ServerPlayer player,
        ResourceLocation itemId,
        String action,
        String targets,
        String near,
        int radius,
        long expiresAt
    ) {
        Item item = ForgeRegistries.ITEMS.getValue(itemId);
        if (item == null || item == Items.AIR) {
            source.sendFailure(Component.literal(tr(source, "Nieznany przedmiot: ", "Unknown item: ") + itemId));
            return 0;
        }
        if (player.getInventory().getFreeSlot() < 0) {
            source.sendFailure(Component.literal(tr(source, "Ekwipunek gracza jest pełny.", "The player's inventory is full.")));
            return 0;
        }
        ItemStack stack = makePermissionItem(item, action, targets, near, radius, player, expiresAt);
        player.getInventory().add(stack);
        player.inventoryMenu.broadcastChanges();
        source.sendSuccess(() -> Component.literal(
            "Nadano " + player.getGameProfile().getName() + ": " + itemId + " [" + action + "]"
                + (near.isEmpty() ? "" : " near " + near + " (" + radius + ")")
        ).withStyle(ChatFormatting.GREEN), true);
        audit(source.getServer(), source.getTextName(), "ITEM_GIVE",
            player.getGameProfile().getName() + " " + itemId + " " + action
                + (expiresAt > 0L ? " expires=" + expiresAt : ""));
        return 1;
    }

    private static long expiryFromContext(com.mojang.brigadier.context.CommandContext<net.minecraft.commands.CommandSourceStack> context) {
        return System.currentTimeMillis() + IntegerArgumentType.getInteger(context, "sekundy") * 1000L;
    }

    private static ItemStack presetItem(String preset, ServerPlayer player, long expiresAt) {
        ItemStack stack = switch (preset.toLowerCase()) {
            case "pickup" -> makePermissionItem(Items.FEATHER, "pickup", "", "", 0, player, expiresAt);
            case "container-key" -> makePermissionItem(Items.TRIPWIRE_HOOK, "take", "", "", 0, player, expiresAt);
            case "hostile-amulet" -> makePermissionItem(Items.AMETHYST_SHARD, "attack-amulet", "hostile", "", 0, player, expiresAt);
            case "rock-amulet" -> makePermissionItem(Items.FLINT, "mine-amulet", "rock", "", 0, player, expiresAt);
            case "sign" -> makePlaceableSpruceSign(player);
            case "sign-remover" -> makeSignRemover(player);
            case "sword" -> makeConsultantSword(new ItemStack(Items.STONE_SWORD), player);
            case "pickaxe" -> makeConsultantPickaxe(new ItemStack(Items.STONE_PICKAXE), player);
            case "importer" -> makeImporter(new ItemStack(Items.RECOVERY_COMPASS), player);
            case "extractor" -> makeExtractor(new ItemStack(Items.SHEARS), player);
            case "passage-staff" -> makeTravelStaff(player);
            case "return-mirror" -> makeReturnMirror(new ItemStack(Items.ECHO_SHARD), player);
            default -> ItemStack.EMPTY;
        };
        if (!stack.isEmpty() && expiresAt > 0L) {
            stack.getOrCreateTag().putLong(CONSULTANT_EXPIRES_AT, expiresAt);
            localizeConsultantItem(stack, player, true);
        }
        return stack;
    }

    private static int givePreset(net.minecraft.commands.CommandSourceStack source, ServerPlayer player,
                                  String preset, long expiresAt) {
        ItemStack stack = presetItem(preset, player, expiresAt);
        if (stack.isEmpty()) {
            source.sendFailure(Component.literal(tr(source, "Nieznany preset: ", "Unknown preset: ") + preset));
            return 0;
        }
        if (player.getInventory().getFreeSlot() < 0) {
            source.sendFailure(Component.literal(tr(source, "Ekwipunek gracza jest pełny.", "The player's inventory is full.")));
            return 0;
        }
        player.getInventory().add(stack);
        player.inventoryMenu.broadcastChanges();
        source.sendSuccess(() -> Component.literal(tr(source, "Nadano preset ", "Granted preset ") + preset
            + " -> " + player.getGameProfile().getName()).withStyle(ChatFormatting.GREEN), true);
        audit(source.getServer(), source.getTextName(), "PRESET_GIVE", player.getGameProfile().getName() + " " + preset
            + (expiresAt > 0L ? " expires=" + expiresAt : ""));
        return 1;
    }

    private static int listPresets(net.minecraft.commands.CommandSourceStack source) {
        source.sendSuccess(() -> Component.literal(tr(source, "Presety: ", "Presets: ") + String.join(", ", ITEM_PRESETS))
            .withStyle(ChatFormatting.AQUA), false);
        return ITEM_PRESETS.size();
    }

    private static String roleLabel(ServerPlayer player, boolean english) {
        if (isOperator(player)) return english ? "Director" : "Ordynator";
        if (isPatient(player)) return english ? "Patient" : "Pacjent";
        return english ? "Consultant" : "Konsultant";
    }

    private static int showStatus(net.minecraft.commands.CommandSourceStack source, ServerPlayer player) {
        boolean en = source.getEntity() instanceof Player viewer && isEnglish(viewer);
        source.sendSuccess(() -> Component.literal((en ? "Consultant status: " : "Status konsultanta: ")
            + player.getGameProfile().getName()).withStyle(ChatFormatting.AQUA), false);
        source.sendSuccess(() -> Component.literal((en ? "Role: " : "Rola: ") + roleLabel(player, en)
            + " | " + (en ? "language: " : "język: ") + (isEnglish(player) ? "English" : "Polski")
            + " | " + (en ? "world: " : "świat: ")
            + (isFreedomDimension(player) ? (en ? "unrestricted" : "swobodny") : (en ? "protected" : "chroniony"))), false);
        int permissions = 0;
        long now = System.currentTimeMillis();
        for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
            ItemStack stack = player.getInventory().getItem(slot);
            if (!stack.hasTag() || !stack.getTag().contains(CONSULTANT_ACTION, Tag.TAG_STRING)) continue;
            permissions++;
            String action = stack.getTag().getString(CONSULTANT_ACTION);
            String targets = stack.getTag().getString(CONSULTANT_TARGETS);
            String near = stack.getTag().getString(CONSULTANT_NEAR);
            long expiresAt = stack.getTag().getLong(CONSULTANT_EXPIRES_AT);
            String expiry = expiresAt <= 0L ? (en ? "permanent" : "bezterminowy")
                : Math.max(0L, (expiresAt - now + 999L) / 1000L) + "s";
            boolean active = hasPermissionAction(stack, action) && permissionConditionSatisfied(stack, player);
            source.sendSuccess(() -> Component.literal("- " + action
                + (targets.isEmpty() ? "" : " [" + targets + "]")
                + (near.isEmpty() ? "" : " near " + near + "/" + stack.getTag().getInt(CONSULTANT_RADIUS))
                + " | " + expiry + " | " + (active ? (en ? "active" : "aktywne") : (en ? "inactive" : "nieaktywne")))
                .withStyle(active ? ChatFormatting.GREEN : ChatFormatting.YELLOW), false);
        }
        if (permissions == 0) {
            source.sendSuccess(() -> Component.literal(en ? "No permission items." : "Brak przedmiotów uprawnień.")
                .withStyle(ChatFormatting.GRAY), false);
        }
        return permissions;
    }

    private static int showAudit(net.minecraft.commands.CommandSourceStack source, String playerFilter, int page) {
        List<RoleData.AuditEntry> entries = RoleData.get(source.getServer()).auditLog().stream()
            .filter(entry -> playerFilter.isEmpty() || entry.actor().equalsIgnoreCase(playerFilter)
                || entry.detail().toLowerCase().contains(playerFilter.toLowerCase()))
            .toList();
        int perPage = 10;
        int pages = Math.max(1, (entries.size() + perPage - 1) / perPage);
        int actual = Math.min(page, pages);
        source.sendSuccess(() -> Component.literal("Psychiatryk audit " + actual + "/" + pages + " (" + entries.size() + ")")
            .withStyle(ChatFormatting.GOLD), false);
        int start = Math.max(0, entries.size() - actual * perPage);
        int end = entries.size() - (actual - 1) * perPage;
        for (int index = end - 1; index >= start; index--) {
            RoleData.AuditEntry entry = entries.get(index);
            source.sendSuccess(() -> Component.literal(LOG_TIME.format(Instant.ofEpochMilli(entry.time()))
                + " | " + entry.actor() + " | " + entry.action() + " | " + entry.detail()).withStyle(ChatFormatting.GRAY), false);
        }
        return entries.size();
    }

    private static int clearAudit(net.minecraft.commands.CommandSourceStack source) {
        RoleData.get(source.getServer()).clearAudit();
        LOGGER.info("[Psychiatryk Audit] {} cleared the persistent audit log", source.getTextName());
        source.sendSuccess(() -> Component.literal(tr(source, "Wyczyszczono dziennik.", "Audit log cleared."))
            .withStyle(ChatFormatting.YELLOW), true);
        return 1;
    }

    private static int redeem(ServerPlayer player, String rawCode) {
        String code = rawCode.toLowerCase();
        RoleData data = RoleData.get(player.getServer());
        if (isPatient(player)) {
            player.sendSystemMessage(Component.literal(tr(player, "Masz już rolę Pacjent.", "You already have the Patient role."))
                .withStyle(ChatFormatting.YELLOW));
            return 0;
        }
        if (!data.consumeCode(code)) {
            player.sendSystemMessage(Component.literal(tr(player,
                "Kod jest nieprawidłowy albo został już użyty.", "The code is invalid or has already been used."))
                .withStyle(ChatFormatting.RED));
            return 0;
        }
        data.addPatient(player.getUUID());
        NEXT_SIGN_GRANT_TICK.remove(player.getUUID());
        syncPatientRank(player);
        player.setGameMode(GameType.SURVIVAL);
        syncCollisionRule(player);
        player.refreshTabListName();
        player.sendSystemMessage(Component.literal(tr(player,
            "Przyjęcie zakończone. Otrzymujesz rolę Pacjent.", "Admission complete. You now have the Patient role."))
            .withStyle(ChatFormatting.GREEN));
        audit(player, "ADMISSION_REDEEMED", "role=pacjent");
        return 1;
    }

    private static int generate(net.minecraft.commands.CommandSourceStack source, int count) {
        RoleData data = RoleData.get(source.getServer());
        for (int i = 0; i < count; i++) {
            String code;
            do {
                code = CodeGenerator.generate();
            } while (data.codes().contains(code));
            data.addCode(code);
            String generatedCode = code;
            source.sendSuccess(() -> Component.literal(generatedCode).withStyle(ChatFormatting.GREEN), false);
        }
        source.sendSuccess(() -> Component.literal("Wygenerowano: " + count + "; aktywnych kodów: " + data.codes().size()), false);
        audit(source.getServer(), source.getTextName(), "ADMISSION_CODES_GENERATED", "count=" + count);
        return count;
    }

    private static int list(net.minecraft.commands.CommandSourceStack source) {
        List<String> codes = RoleData.get(source.getServer()).codes().stream().sorted().toList();
        if (codes.isEmpty()) {
            source.sendSuccess(() -> Component.literal("Brak aktywnych kodów."), false);
            return 0;
        }
        source.sendSuccess(() -> Component.literal("Aktywne jednorazowe kody (" + codes.size() + "):"), false);
        codes.forEach(code -> source.sendSuccess(() -> Component.literal(code).withStyle(ChatFormatting.GREEN), false));
        return codes.size();
    }
}
