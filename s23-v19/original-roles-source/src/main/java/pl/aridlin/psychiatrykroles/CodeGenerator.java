package pl.aridlin.psychiatrykroles;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.List;

final class CodeGenerator {
    private static final List<String> NUMBERS = List.of("2137", "69", "420", "1337");
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final List<String> WORDS = loadWords();

    private CodeGenerator() {
    }

    static String generate() {
        return WORDS.get(RANDOM.nextInt(WORDS.size())) + "-"
            + NUMBERS.get(RANDOM.nextInt(NUMBERS.size())) + "-"
            + WORDS.get(RANDOM.nextInt(WORDS.size()));
    }

    private static List<String> loadWords() {
        InputStream stream = CodeGenerator.class.getResourceAsStream("/bip39-english.txt");
        if (stream == null) {
            throw new IllegalStateException("Missing BIP-39 word list");
        }
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
            List<String> words = reader.lines().filter(line -> !line.isBlank()).toList();
            if (words.size() != 2048) {
                throw new IllegalStateException("Expected 2048 BIP-39 words, got " + words.size());
            }
            return words;
        } catch (IOException e) {
            throw new IllegalStateException("Could not load BIP-39 word list", e);
        }
    }
}
