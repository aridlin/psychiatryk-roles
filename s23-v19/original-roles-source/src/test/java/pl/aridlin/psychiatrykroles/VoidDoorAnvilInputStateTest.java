package pl.aridlin.psychiatrykroles;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

class VoidDoorAnvilInputStateTest {
    @Test
    void onlyNewInsertionsClearTheField() {
        VoidDoorAnvilInputState state = new VoidDoorAnvilInputState();
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();

        assertEquals(VoidDoorAnvilInputState.Update.VANILLA, state.onInput(null));
        assertEquals(VoidDoorAnvilInputState.Update.CLEAR, state.onInput(first));
        assertEquals(VoidDoorAnvilInputState.Update.KEEP, state.onInput(first),
            "A refresh of the same pair must preserve the code being typed");
        assertEquals(VoidDoorAnvilInputState.Update.CLEAR, state.onInput(second),
            "Replacing the pair must start with an empty field");
        assertEquals(VoidDoorAnvilInputState.Update.VANILLA, state.onInput(null));
        assertEquals(VoidDoorAnvilInputState.Update.CLEAR, state.onInput(first),
            "Removing and reinserting a pair must clear the field again");
    }
}
