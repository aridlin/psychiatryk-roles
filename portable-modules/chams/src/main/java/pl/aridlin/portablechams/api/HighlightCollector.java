package pl.aridlin.portablechams.api;

import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.AABB;

/** Valid only during a provider callback on the client render thread. No hidden server data is fetched. */
public interface HighlightCollector {
    /** Living entities and dropped items use their actual textured model silhouette. */
    void entity(Entity entity, HighlightStyle style);
    /** Current loaded block's selection shape; excludes its own cells from occlusion. */
    void block(BlockPos pos, HighlightStyle style);
    /** Caller-managed stable identity and world-space bounds. */
    void box(UUID id, AABB bounds, HighlightStyle style, boolean excludeOwnBlocks);
    /** Horizontal area at bounds.minY; useful for construction boundaries. */
    void plane(UUID id, AABB bounds, HighlightStyle style);
    /** Camera-facing cross at bounds centre; useful for temporary markings. */
    void glyph(UUID id, AABB bounds, HighlightStyle style);
}
