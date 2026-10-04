package pl.aridlin.psychiatrykroles;

import net.minecraft.ChatFormatting;
import net.minecraft.world.InteractionResult;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.ChatType;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.PlayerChatMessage;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.world.item.component.WritableBookContent;
import net.minecraft.world.item.component.WrittenBookContent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.ServerChatEvent;

import java.util.ArrayList;
import java.util.List;

@EventBusSubscriber(modid = PsychiatrykRoles.MOD_ID)
public final class ChatBook {
    private static final String MARKER = "psychiatrykChatBook";
    private static final int MAX_LINES = 32;
    private static final int MAX_LENGTH = 256;

    private ChatBook() {}

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void preventPlacement(PlayerInteractEvent.RightClickBlock event) {
        if (!isChatBook(event.getItemStack())) return;
        // Cancel both block use and item use-on-block on both sides. PASS routes
        // to normal RightClickItem, preserving editing and sneak-to-send.
        event.setCancellationResult(InteractionResult.PASS);
        event.setCanceled(true);
    }

    static ItemStack blankCopy(ItemStack stack) {
        ItemStack blank = stack.transmuteCopy(Items.WRITABLE_BOOK);
        blank.remove(DataComponents.WRITTEN_BOOK_CONTENT);
        blank.set(DataComponents.WRITABLE_BOOK_CONTENT, WritableBookContent.EMPTY);
        return blank;
    }

    static boolean isChatBook(ItemStack stack) {
        return (stack.is(Items.WRITABLE_BOOK) || stack.is(Items.WRITTEN_BOOK))
            && ItemTagCompat.read(stack).getBoolean(MARKER);
    }

    static void localize(ItemStack stack, boolean english) {
        stack.set(DataComponents.CUSTOM_NAME, Component.literal(english ? "Chat Book" : "Książka Czatu")
            .withStyle(ChatFormatting.AQUA));
        List<Component> lore = new ArrayList<>();
        for (String line : english ? new String[] {
            "Write one message or /command per line.", "Sneak-right-click to send all lines as yourself.",
            "Commands use your normal permissions. Limit: 32 lines.",
            "Pages clear after sending. Cannot be placed on a lectern."
        } : new String[] {
            "Wpisz jedną wiadomość lub /komendę w wierszu.", "Kucnij i kliknij PPM, aby wysłać wiersze jako siebie.",
            "Komendy używają twoich uprawnień. Limit: 32 wiersze.",
            "Strony czyszczą się po wysłaniu. Nie można odłożyć na pulpit."
        }) lore.add(Component.literal(line).withStyle(ChatFormatting.GRAY));
        stack.set(DataComponents.LORE, new ItemLore(lore));
    }

    static List<String> lines(ItemStack stack) {
        List<String> result = new ArrayList<>();
        if (!isChatBook(stack)) return result;
        List<String> pages;
        if (stack.is(Items.WRITTEN_BOOK)) {
            WrittenBookContent content = stack.get(DataComponents.WRITTEN_BOOK_CONTENT);
            pages = content == null ? List.of() : content.getPages(false).stream().map(Component::getString).toList();
        } else {
            WritableBookContent content = stack.get(DataComponents.WRITABLE_BOOK_CONTENT);
            pages = content == null ? List.of() : content.getPages(false).toList();
        }
        for (String text : pages) {
            for (String raw : text.split("\\R", -1)) {
                String line = raw.trim();
                if (!line.isEmpty()) result.add(line);
            }
        }
        return result;
    }

    static void send(ServerPlayer player, ItemStack stack) {
        if (player.getCooldowns().isOnCooldown(stack.getItem())) return;
        List<String> lines = lines(stack);
        boolean english = PsychiatrykRoles.isEnglish(player);
        if (lines.isEmpty()) {
            player.sendSystemMessage(Component.literal(english ? "Write in the book first." : "Najpierw wpisz tekst w książce."));
            return;
        }
        if (lines.size() > MAX_LINES || lines.stream().anyMatch(line -> line.length() > MAX_LENGTH)) {
            player.sendSystemMessage(Component.literal(english
                ? "Chat Book limit: 32 lines, 256 characters per line."
                : "Limit Książki Czatu: 32 wiersze, 256 znaków na wiersz.").withStyle(ChatFormatting.RED));
            return;
        }
        player.getCooldowns().addCooldown(stack.getItem(), 20);
        // Consume the validated batch before commands can move or replace the item.
        // Keep its marker, name, lore and other components; signed copies become editable.
        ItemStack blank = blankCopy(stack);
        for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
            if (player.getInventory().getItem(slot) == stack) {
                player.getInventory().setItem(slot, blank);
                break;
            }
        }
        player.getCooldowns().addCooldown(Items.WRITABLE_BOOK, 20);
        player.getInventory().setChanged();
        player.containerMenu.broadcastChanges();
        for (String line : lines) {
            if (line.startsWith("/")) {
                player.getServer().getCommands().performPrefixedCommand(player.createCommandSourceStack(), line);
            } else {
                ServerChatEvent event = new ServerChatEvent(player, line, Component.literal(line));
                NeoForge.EVENT_BUS.post(event);
                if (!event.isCanceled()) {
                    player.getServer().getPlayerList().broadcastChatMessage(
                        // A book action has no client chat signature. Use the server-generated
                        // chat packet while ChatType still supplies the player's display name.
                        PlayerChatMessage.system(line).withUnsignedContent(event.getMessage()),
                        player, ChatType.bind(ChatType.CHAT, player));
                }
            }
        }
    }
}
