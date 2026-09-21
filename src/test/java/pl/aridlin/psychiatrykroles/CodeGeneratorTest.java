package pl.aridlin.psychiatrykroles;

import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CodeGeneratorTest {
    @Test
    void generatedCodeUsesRequestedShapeAndAllowedNumber() {
        String[] parts = CodeGenerator.generate().split("-");
        assertEquals(3, parts.length);
        assertTrue(Set.of("2137", "69", "420", "1337").contains(parts[1]));
        assertTrue(parts[0].matches("[a-z]+"));
        assertTrue(parts[2].matches("[a-z]+"));
    }
}
