package pl.aridlin.peebheadqa;

import java.nio.file.*;
import java.util.*;
import java.lang.reflect.*;
import com.mojang.blaze3d.vertex.*;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import pl.aridlin.psychiatrykroles.peeb.client.*;

/** Tests the supplied original rig and the actual composed render classes. */
public final class PeebRenderRegressionTest {
    static int checks, poses, coloredVertices;
    static void check(boolean okay, String message) { checks++; if (!okay) throw new AssertionError(message); }
    static void near(float a, float b, String message) { check(Math.abs(a - b) < 0.00001F, message); }

    static void turnChecks() {
        double nativeSpeed = 7.0 * 1.4851409 / 20.0;
        for (float current : new float[]{-179, -100, -5, 0, 89, 179})
            for (float goal : new float[]{-178, -90, 0, 75, 178})
                for (double speed : new double[]{0.001, nativeSpeed, nativeSpeed * 2, nativeSpeed * 20})
                    for (float seconds : new float[]{0, 1F / 144, 1F / 60, .05F, .1F}) {
                        double x = -Math.sin(Math.toRadians(goal)) * speed;
                        double z = Math.cos(Math.toRadians(goal)) * speed;
                        float actual = PeebTurning.towardMovement(current, x, z, seconds);
                        float alpha = (float)Math.clamp(.2 * (speed * 20 / 1.4851409 / 7) * seconds * 60, 0, 1);
                        Quaternionf reference = new Quaternionf().rotationY((float)Math.toRadians(-current))
                            .slerp(new Quaternionf().rotationY((float)Math.toRadians(-goal)), alpha);
                        Quaternionf converted = new Quaternionf().rotationY((float)Math.toRadians(-actual));
                        check(1 - Math.abs(reference.dot(converted)) < .000001F, "V0.05 yaw Quaternion.Slerp equivalence");
                    }
        near(PeebTurning.towardMovement(90, 0, 0, .1F), 90, "stationary body never follows camera");
        near(PeebTurning.towardMovement(90, 0, nativeSpeed, 1F / 60), 72, "actual prefab normal-speed turn is .2/frame");
        near(PeebTurning.towardMovement(180, nativeSpeed, 0, 1F / 60), 198, "east turns toward Minecraft yaw -90 by shortest arc");
        near(PeebTurning.towardMovement(31, Double.NaN, 0, .05F), 31, "invalid velocity remains stable");
        near(PeebTurning.towardMovement(31, 1, 0, Float.NaN), 31, "invalid elapsed time remains stable");
        for (float partial : new float[]{0, .25F, .5F, 1}) {
            float mounted = PeebRenderer.mountedBodyYaw(partial, -145, 70, 20, 40, true);
            near(mounted, 20 + 20 * partial, "mounted Peeb remains scooter-aligned");
        }
    }

    public static void main(String[] args) throws Exception {
        turnChecks();
        PeebMesh mesh = PeebRigFixture.load(Path.of(args[0]));
        int[][] eyeGroups = (int[][])PeebRigFixture.get(mesh, "eyeVertices");
        check(eyeGroups[0].length > 0 && eyeGroups[1].length > 0, "actual original eye geometry");
        check(Arrays.stream(PeebMesh.class.getDeclaredMethods()).noneMatch(m -> m.getName().equals("protectEyes")), "detached eye-pushing method removed");
        for (String clip : List.of("idle", "run", "midair"))
            for (float seconds : new float[]{.03F, .18F, .4F}) {
                PeebMesh.Pose baseline = PeebRigFixture.pose(mesh, clip, seconds);
                mesh.animate(baseline, PeebRigFixture.motion(clip, seconds), 0, 0, 0);
                Vector3f[] original = (Vector3f[])PeebRigFixture.get(baseline, "skinned");
                for (float yaw : new float[]{-180, -90, -65, 0, 65, 90, 180})
                    for (float pitch : new float[]{-90, 0, 90}) {
                        PeebMesh.Pose pose = PeebRigFixture.pose(mesh, clip, seconds);
                        mesh.animate(pose, PeebRigFixture.motion(clip, seconds), yaw, pitch, 0);
                        Vector3f[] vertices = (Vector3f[])PeebRigFixture.get(pose, "skinned");
                        poses++;
                        for (int i = 0; i < original.length; i++)
                            check(vertices[i].distance(original[i]) < .000001F, "camera yaw/pitch leaves original complete rig intact");
                        mesh.aimTusk(pose, new Vector3f(3, 4, 6), .05F);
                        vertices = (Vector3f[])PeebRigFixture.get(pose, "skinned");
                        for (int[] group : eyeGroups) for (int eye : group)
                            check(vertices[eye].distance(original[eye]) < .000001F, "tusk targeting cannot displace eye shell/pupil");
                    }
            }
        // Run original imported animation keyframes with camera look extremes.
        for (String clip : List.of("idle", "run", "midair")) {
            var animation = PeebRigFixture.read(Path.of(args[0]).resolve(clip + "-animation-30fps.json"));
            int frames = animation.getAsJsonArray("tracks").get(0).getAsJsonObject().getAsJsonArray("samples").size();
            float fps = animation.get("fps").getAsFloat();
            for (int frame = 0; frame < frames; frame += 5) {
                float seconds = frame / fps;
                PeebMesh.Pose a = PeebRigFixture.pose(mesh, clip, seconds), b = PeebRigFixture.pose(mesh, clip, seconds);
                mesh.animate(a, PeebRigFixture.motion(clip, seconds), 0, 0, 0);
                mesh.animate(b, PeebRigFixture.motion(clip, seconds), 180, -90, 0);
                Vector3f[] va = (Vector3f[])PeebRigFixture.get(a, "skinned"), vb = (Vector3f[])PeebRigFixture.get(b, "skinned");
                for (int[] group : eyeGroups) for (int eye : group)
                    check(va[eye].distance(vb[eye]) < .000001F, "imported keyframe eye binding survives camera turns");
            }
        }
        dyeChecks(mesh);
        System.out.println("{\"success\":true,\"checks\":" + checks + ",\"poses\":" + poses
            + ",\"colored_vertices\":" + coloredVertices + ",\"game_launched\":false}");
    }

    static void dyeChecks(PeebMesh mesh) throws Exception {
        PeebMesh.Pose pose = PeebRigFixture.pose(mesh, "idle", .18F);
        mesh.animate(pose, PeebRigFixture.motion("idle", .18F), 0, 0, 0);
        int[][] triangles = (int[][])PeebRigFixture.get(mesh, "triangles");
        boolean[] skins = (boolean[])PeebRigFixture.get(mesh, "skinMaterials");
        int[] original = (int[])PeebRigFixture.get(mesh, "colors");
        ArrayList<Integer> colors = new ArrayList<>();
        VertexConsumer recorder = (VertexConsumer)Proxy.newProxyInstance(VertexConsumer.class.getClassLoader(),
            new Class<?>[]{VertexConsumer.class}, (proxy, method, parameters) -> {
                if (method.getName().equals("setColor") && parameters.length == 1) colors.add((Integer)parameters[0]);
                if (method.getReturnType() == VertexConsumer.class) return proxy;
                if (method.getReturnType() == boolean.class) return false;
                return null;
            });
        for (int dye : new int[]{0xFFFFFF, 0xF9801D, 0x80C71F, 0xC74EBD, 0x3AB3DA, 0x1D1D21}) {
            colors.clear(); mesh.draw(pose, new PoseStack(), recorder, 0, 0, dye);
            int submitted = 0, tuskVertices = 0;
            int[][] indices = (int[][])PeebRigFixture.get(mesh, "bones");
            float[][] weights = (float[][])PeebRigFixture.get(mesh, "weights");
            int[] nodes = (int[])PeebRigFixture.get(mesh, "boneNodes");
            int[] nose = (int[])PeebRigFixture.get(mesh, "noseNodes");
            for (int group = 0; group < triangles.length; group++)
                for (int t = 0; t < triangles[group].length; t += 3)
                    for (int corner = 0; corner < 4; corner++) {
                        int vertex = triangles[group][t + Math.min(corner, 2)];
                        int expected = skins[group] ? (original[group] & 0xFF000000 | dye) : original[group];
                        check(colors.get(submitted++) == expected, "actual draw material color including tusk");
                        for (int slot = 0; slot < indices[vertex].length; slot++) {
                            int node = nodes[indices[vertex][slot]];
                            if (weights[vertex][slot] > .99F && (node == nose[0] || node == nose[1] || node == nose[2])) {
                                check(skins[group] && colors.get(submitted - 1) == expected, "fully tusk-bound vertex dyed");
                                tuskVertices++; break;
                            }
                        }
                    }
            check(tuskVertices > 0, "test covers real tusk vertices");
            check(submitted == colors.size(), "no extra geometry/color submissions");
            coloredVertices += submitted;
        }
    }
}
