package pl.aridlin.psychiatrykroles;

import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

/** Short, in-game guide to the mod's player features and operator tools. */
final class PsychiatrykHelpCommands {
    private PsychiatrykHelpCommands() {}

    static void register(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("psychiatrykhelp")
            .executes(context -> overview(context.getSource()))
            .then(Commands.literal("roles").executes(context -> roles(context.getSource())))
            .then(Commands.literal("items").executes(context -> items(context.getSource())))
            .then(Commands.literal("poker").executes(context -> poker(context.getSource())))
            .then(Commands.literal("buildcopy")
                .requires(source -> source.hasPermission(2))
                .executes(context -> buildcopy(context.getSource())))
            .then(Commands.literal("admin")
                .requires(source -> source.hasPermission(4))
                .executes(context -> admin(context.getSource()))));
    }

    private static int overview(CommandSourceStack source) {
        heading(source, "Psychiatryk — pomoc", "Psychiatryk — help");
        line(source, "/psychiatrykhelp roles — role, przyjęcie i język",
            "/psychiatrykhelp roles — roles, admission and language");
        line(source, "/psychiatrykhelp items — drzwi, klapy i przedmioty",
            "/psychiatrykhelp items — doors, trapdoors and items");
        line(source, "/psychiatrykhelp poker — stół i interfejs pokera",
            "/psychiatrykhelp poker — tables and poker interface");
        if (source.hasPermission(2)) {
            line(source, "/psychiatrykhelp buildcopy — kopiowanie budowli w trybie kreatywnym",
                "/psychiatrykhelp buildcopy — creative build transfer");
        }
        if (source.hasPermission(4)) {
            line(source, "/psychiatrykhelp admin — narzędzia operatora",
                "/psychiatrykhelp admin — operator tools");
        }
        return 1;
    }

    private static int roles(CommandSourceStack source) {
        heading(source, "Role i język", "Roles and language");
        line(source, "Konsultant ma ochronę świata; sprawdź ograniczenia i przedmioty: /konsultant status",
            "Consultants have world protections; inspect restrictions and permission items: /konsultant status");
        line(source, "Pacjent gra swobodnie; sprawdź status: /pacjent status",
            "Patients play normally; inspect your status: /pacjent status");
        line(source, "Kod przyjęcia wykorzystasz przez /przyjecie <kod>.",
            "Redeem an admission code with /przyjecie <code>.");
        line(source, "Pacjent może nadać lub cofnąć dostęp kontraktora: /zatrudnic <nick>, /wyjebac <nick>.",
            "Patients can grant or revoke contractor access: /zatrudnic <player>, /wyjebac <player>.");
        line(source, "Język zmienisz przez /polski lub /english.",
            "Change language with /polski or /english.");
        return 1;
    }

    private static int items(CommandSourceStack source) {
        heading(source, "Przedmioty Psychiatryk", "Psychiatryk items");
        line(source, "Receptury znajdziesz w JEI lub w książce receptur.",
            "Find recipes in JEI or the recipe book.");
        line(source, "Drzwi Pustki i Klapy Pustki powstają w połączonych parach. Otwórz jedną stronę i przejdź przez czarną płaszczyznę, także między wymiarami.",
            "Void Doors and Void Trapdoors craft as linked pairs. Open one end and cross the dark plane, including between dimensions.");
        line(source, "Opcjonalny kod Drzwi Pustki ustawisz, zmieniając nazwę świeżo wykonanej pary w kowadle; przy otwieraniu wpiszesz go w oknie kowadła.",
            "Set an optional Void Door code by renaming the freshly crafted pair in an anvil; enter it in the anvil prompt when opening.");
        line(source, "Książka Czatu: wpisz wiadomości lub komendy po jednej wierszami, potem kucnij i kliknij PPM.",
            "Chat Book: write messages or commands one per line, then sneak-right-click to send them.");
        line(source, "Laska Przejścia i Lustro Powrotu pomagają konsultantom podróżować.",
            "The Passage Staff and Mirror of Returning help consultants travel.");
        return 1;
    }

    private static int poker(CommandSourceStack source) {
        heading(source, "Poker", "Poker");
        line(source, "Otwórz stół przez /poker gui lub odbierz przedmiot stołu przez /poker gui item.",
            "Open the table with /poker gui or get its item with /poker gui item.");
        line(source, "Dostępne stoły: /poker list; nowy stół: /poker create <nazwa>; dołączanie: /poker join <nazwa>.",
            "List tables: /poker list; create: /poker create <name>; join: /poker join <name>.");
        line(source, "Pełne zasady i komendy: /poker help. Saldo żetonów: /poker bank.",
            "Full rules and commands: /poker help. Chip balance: /poker bank.");
        return 1;
    }

    private static int buildcopy(CommandSourceStack source) {
        heading(source, "Kopiowanie budowli", "Build transfer");
        line(source, "Potrzebujesz moda klienckiego, trybu kreatywnego i uprawnień operatora.",
            "Requires the client mod, Creative mode and operator permissions.");
        line(source, "Limit: 262 144 bloków, do 128 na oś; cały obszar źródłowy i docelowy musi być załadowany.",
            "Limit: 262,144 blocks, up to 128 per axis; the whole source and destination must be loaded.");
        line(source, "Podczas wklejania pozostań online i utrzymaj obszar docelowy załadowany.",
            "Stay online and keep the destination loaded while the paste runs.");
        line(source, "Postaw oba bloki narożne i kliknij każdy PPM; /buildcopy save <nazwa> zapisuje zaznaczenie lokalnie.",
            "Place the two corner blocks and right-click each; /buildcopy save <name> stores the selection locally.");
        line(source, "/buildcopy preview <nazwa> pokazuje podgląd przy pierwszym narożniku; /buildcopy place wysyła go do sprawdzenia i umieszczenia.",
            "/buildcopy preview <name> shows a preview at the first corner; /buildcopy place sends it for server validation and placement.");
        line(source, "Jeśli masz nietkniętą kopię starego świata, otwórz ją w tych samych współrzędnych i użyj /buildcopy compare-pristine <nazwa>.",
            "If you have a pristine copy of the old world, open it at the same coordinates and run /buildcopy compare-pristine <name>.");
        return 1;
    }

    private static int admin(CommandSourceStack source) {
        heading(source, "Narzędzia operatora", "Operator tools");
        line(source, "/oldworld enter [x y z] i /oldworld return — archiwalny świat i powrót.",
            "/oldworld enter [x y z] and /oldworld return — archived world and return.");
        line(source, "/restartin <czas> (np. 5m), /restartin status, /restartin cancel — zaplanowany restart.",
            "/restartin <duration> (for example 5m), /restartin status, /restartin cancel — scheduled restart.");
        line(source, "/przyjecie-kod generuj [liczba] i /przyjecie-kod lista — kody przyjęcia.",
            "/przyjecie-kod generuj [count] and /przyjecie-kod lista — admission codes.");
        line(source, "/konsultant-item preset list i /konsultant-log — przedmioty uprawnień i dziennik.",
            "/konsultant-item preset list and /konsultant-log — permission items and audit log.");
        return 1;
    }

    private static boolean english(CommandSourceStack source) {
        return source.getEntity() instanceof ServerPlayer player && PsychiatrykRoles.isEnglish(player);
    }

    private static void heading(CommandSourceStack source, String polish, String english) {
        source.sendSuccess(() -> Component.literal(english(source) ? english : polish)
            .withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD), false);
    }

    private static void line(CommandSourceStack source, String polish, String english) {
        source.sendSuccess(() -> Component.literal(english(source) ? english : polish)
            .withStyle(ChatFormatting.GRAY), false);
    }
}
