package pl.aridlin.psychiatrykroles;

import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.properties.Half;
import net.minecraft.world.phys.Vec3;
import qouteall.imm_ptl.core.portal.Portal;
import qouteall.imm_ptl.core.portal.PortalManipulation;

/** Derived portals. Door saved data remains the sole source of pairing and chunk tickets. */
final class ImmersiveVoidPortals {
    static final String TAG = "psychiatrykImmersiveVoidPortal";
    record Endpoint(ServerLevel level, Vec3 center, Vec3 widthAxis, Vec3 heightAxis, double width, double height) {}
    record Link(Endpoint a, Endpoint b, List<Portal> portals) {}
    private final String kind;
    private final Map<UUID, Link> links = new HashMap<>();
    private final Set<UUID> active = new HashSet<>();
    ImmersiveVoidPortals(String kind) { this.kind = kind; }
    static Endpoint door(ServerLevel level, BlockPos pos, Direction facing) {
        return new Endpoint(level, VoidDoorGeometry.center(pos, facing).add(0, .96, 0),
            new Vec3(VoidExitDirections.exit(level,pos,facing).getStepZ(), 0, -VoidExitDirections.exit(level,pos,facing).getStepX()), new Vec3(0, 1, 0), .83, 1.86);
    }
    static Endpoint trapdoor(ServerLevel level, BlockPos pos, Half half) {
        return new Endpoint(level, Vec3.atBottomCenterOf(pos).add(0, half == Half.TOP ? .9 : .1, 0),
            new Vec3(1,0,0), new Vec3(0,0,-1), .84, .84);
    }
    void begin() { active.clear(); }
    void link(UUID pair, Endpoint a, Endpoint b) {
        active.add(pair);
        Link old = links.get(pair);
        if (old != null && old.a.equals(a) && old.b.equals(b) && old.portals.stream().noneMatch(Portal::isRemoved)) return;
        remove(pair);
        Portal forward = create(a, b);
        Portal reverse = PortalManipulation.createReversePortal(forward, Portal.ENTITY_TYPE);
        Portal flipped = PortalManipulation.createFlippedPortal(forward, Portal.ENTITY_TYPE);
        Portal reverseFlipped = PortalManipulation.createFlippedPortal(reverse, Portal.ENTITY_TYPE);
        List<Portal> portals = List.of(forward, reverse, flipped, reverseFlipped);
        for (Portal portal : portals) {
            portal.addTag(TAG);
            portal.addTag("psychiatrykVoidPortal:" + kind + ":" + pair);
            portal.setInvulnerable(true);
            portal.setNoGravity(true);
            if (!((ServerLevel)portal.level()).addFreshEntity(portal)) {
                portals.forEach(Portal::discard);
                throw new IllegalStateException("Could not create linked Void Portal");
            }
        }
        links.put(pair, new Link(a,b,portals));
    }
    private static Portal create(Endpoint a, Endpoint b) {
        Portal p = Portal.ENTITY_TYPE.create(a.level);
        if (p == null) throw new IllegalStateException("Immersive Portals entity missing");
        p.setPos(a.center.x, a.center.y, a.center.z);
        p.setOrientationAndSize(a.widthAxis, a.heightAxis, a.width, a.height);
        p.setDestinationDimension(b.level.dimension());
        p.setDestination(b.center);
        p.setOtherSideOrientation(PortalManipulation.getPortalOrientationQuaternion(b.widthAxis, b.heightAxis));
        p.setScaleTransformation(1);
        p.setInteractable(true);
        return p;
    }
    void end() { for (UUID id : List.copyOf(links.keySet())) if (!active.contains(id)) remove(id); }
    private void remove(UUID id) { Link old = links.remove(id); if (old != null) old.portals.forEach(Portal::discard); }
    void clear() { for (UUID id : List.copyOf(links.keySet())) remove(id); active.clear(); }
}
