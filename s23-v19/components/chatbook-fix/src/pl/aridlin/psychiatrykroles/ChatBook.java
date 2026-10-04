package pl.aridlin.psychiatrykroles;

import net.minecraft.ChatFormatting;
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

final class ChatBook {
    private static final String MARKER = "psychiatrykChatBook";
    private static final int MAX_LINES = 32;
    private static final int MAX_LENGTH = 256;

    private ChatBook() {}

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
            "Commands use your normal permissions. Limit: 32 lines."
        } : new String[] {
            "Wpisz jedną wiadomość lub /komendę w wierszu.", "Kucnij i kliknij PPM, aby wysłać wiersze jako siebie.",
            "Komendy używają twoich uprawnień. Limit: 32 wiersze."
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
