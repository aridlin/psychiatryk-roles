package pl.aridlin.psychiatrykroles;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.shapes.BooleanOp;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/** Whether a neighbor's collision shape covers the actual frame strip facing the portal. */
final class PortalFrameOcclusion {
    private PortalFrameOcclusion() {}

    static boolean covers(ServerLevel level, BlockPos neighbor, VoxelShape strip) {
        VoxelShape collision = level.getBlockState(neighbor).getCollisionShape(level, neighbor);
        return !Shapes.joinIsNotEmpty(strip, collision, BooleanOp.ONLY_FIRST);
    }

    static VoxelShape sideStrip(Direction neighborSide, double spanMin, double spanMax,
                                double bottom, double top) {
        double edge = .005;
        return switch (neighborSide) {
            case NORTH -> Shapes.box(spanMin, bottom, 1 - edge, spanMax, top, 1);
            case SOUTH -> Shapes.box(spanMin, bottom, 0, spanMax, top, edge);
            case WEST -> Shapes.box(1 - edge, bottom, spanMin, 1, top, spanMax);
            case EAST -> Shapes.box(0, bottom, spanMin, edge, top, spanMax);
            default -> throw new IllegalArgumentException("Frame edge must be horizontal");
        };
    }

    static VoxelShape topStrip(boolean widthX, double depthMin, double depthMax) {
        return widthX ? Shapes.box(.01, 0, depthMin, .99, .005, depthMax)
            : Shapes.box(depthMin, 0, .01, depthMax, .005, .99);
    }
}
