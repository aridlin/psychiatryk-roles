package pl.aridlin.psychiatrykroles;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.RandomAccessFile;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OldWorldImportTest {
    @TempDir Path directory;

    @Test
    void refusesPartialImportsAndReadsTheVerifiedOldSpawn() throws Exception {
        assertThrows(IOException.class, () -> OldWorldImport.readManifest(directory));
        Files.writeString(directory.resolve(OldWorldImport.READY_FILE),
            "{\"version\":1,\"sourceDataVersion\":3465,\"spawn\":{\"x\":988,\"y\":67,\"z\":567}}");
        assertEquals(new OldWorldImport.Manifest(3465, new BlockPos(988, 67, 567)),
            OldWorldImport.readManifest(directory));
        Files.writeString(directory.resolve(OldWorldImport.READY_FILE),
            "{\"version\":0,\"sourceDataVersion\":3465,\"spawn\":{\"x\":988,\"y\":67,\"z\":567}}");
        assertThrows(IOException.class, () -> OldWorldImport.readManifest(directory));
    }

    @Test
    void checksTheActualAnvilChunkLocationIncludingNegativeCoordinates() throws Exception {
        int x = -513;
        int z = 520;
        assertFalse(OldWorldImport.hasImportedChunk(directory, x, z));
        writeRegionEntry(x, z, 2, 1, 3 * 4096L);
        assertTrue(OldWorldImport.hasImportedChunk(directory, x, z));
        assertFalse(OldWorldImport.hasImportedChunk(directory, x + 16, z));
        assertFalse(OldWorldImport.hasImportedChunk(directory, x, z - 16));
        writeRegionEntry(x, z, 3, 1, 3 * 4096L);
        assertFalse(OldWorldImport.hasImportedChunk(directory, x, z),
            "A header pointing beyond the available sectors is not an imported chunk");
    }

    @Test
    void declaresArchiveAsASeparateOverworldDimension() throws Exception {
        try (var stream = getClass().getResourceAsStream("/data/psychiatryk_roles/dimension/old_overworld.json")) {
            assertTrue(stream != null);
            JsonObject json = JsonParser.parseReader(new InputStreamReader(stream, StandardCharsets.UTF_8)).getAsJsonObject();
            assertEquals("minecraft:overworld", json.get("type").getAsString());
            JsonObject generator = json.getAsJsonObject("generator");
            assertEquals("minecraft:flat", generator.get("type").getAsString());
            assertEquals("minecraft:the_void", generator.getAsJsonObject("settings")
                .get("biome").getAsString());
            assertEquals("minecraft:air", generator.getAsJsonObject("settings")
                .getAsJsonArray("layers").get(0).getAsJsonObject().get("block").getAsString());
        }
    }

    private void writeRegionEntry(int blockX, int blockZ, int sector, int length, long fileLength) throws Exception {
        int chunkX = Math.floorDiv(blockX, 16);
        int chunkZ = Math.floorDiv(blockZ, 16);
        int regionX = Math.floorDiv(chunkX, 32);
        int regionZ = Math.floorDiv(chunkZ, 32);
        Path regionDir = Files.createDirectories(directory.resolve("region"));
        Path region = regionDir.resolve("r." + regionX + "." + regionZ + ".mca");
        int local = Math.floorMod(chunkX, 32) + 32 * Math.floorMod(chunkZ, 32);
        try (RandomAccessFile file = new RandomAccessFile(region.toFile(), "rw")) {
            file.setLength(fileLength);
            file.seek(local * 4L);
            file.writeByte((sector >> 16) & 0xff);
            file.writeByte((sector >> 8) & 0xff);
            file.writeByte(sector & 0xff);
            file.writeByte(length);
        }
    }
}
