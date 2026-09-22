package pl.aridlin.psychiatrykroles;

public final class LoginRejectionMessages {
    private static final String HELP = "info.goplanska.pl";

    private LoginRejectionMessages() {}

    public static String rewriteForgeLoginMessage(String original) {
        if (original.contains("require Forge to be installed")) {
            return "Potrzebujesz Forge 1.20.1 i paczki modów. Instrukcja: " + HELP
                + "\nForge 1.20.1 and the server modpack are required. Setup: " + HELP;
        }
        if (original.contains("not impl compatible") || original.contains("net version")) {
            return "Niekompatybilna wersja Forge. Pobierz właściwą konfigurację: " + HELP
                + "\nIncompatible Forge version. Get the correct setup: " + HELP;
        }
        return original;
    }
}
