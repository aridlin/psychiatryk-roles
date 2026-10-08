import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Random;
import net.minecraft.world.phys.Vec3;
import pl.aridlin.psychiatrykroles.peeb.PeebConfig;
import pl.aridlin.psychiatrykroles.peeb.PeebGrapple;

/** Calls the real compiled game physics; no copied or stubbed equations. */
public final class RopeHookQA {
    private static int checks;
    private static final PeebConfig.Values SETTINGS = PeebConfig.DEFAULT;
    private static void check(boolean condition, String name) {
        checks++;
        if (!condition) throw new AssertionError(name);
    }
    private static void near(double expected, double actual, double tolerance, String name) {
        check(Double.isFinite(actual) && Math.abs(expected - actual) <= tolerance,
                name + ": expected=" + expected + " actual=" + actual);
    }
    private static void vector(Vec3 expected, Vec3 actual, String name) {
        near(0, expected.distanceTo(actual), 1e-11, name);
    }
    private static Vec3 tangent(Vec3 vector, Vec3 axis) {
        return vector.subtract(axis.scale(vector.dot(axis)));
    }
    public static void main(String[] args) throws Exception {
        Vec3 zero = Vec3.ZERO;
        Vec3 slackVelocity = new Vec3(-.12, .07, .19);
        vector(slackVelocity, PeebGrapple.pullVelocity(new Vec3(0, 0, 3), slackVelocity, zero, 5, SETTINGS, false), "slack rope has no winch or push");
        vector(new Vec3(0,0,-.2), PeebGrapple.constrainVelocity(new Vec3(0,0,7), new Vec3(0,0,-.2), zero, 2, SETTINGS, false), "safety does not cancel inward motion outside rest length");
        near(0, PeebGrapple.tension(2, .3, 3, SETTINGS), 0, "slack positive radial motion has no winch");
        near(0, PeebGrapple.tension(4, -1, 3, SETTINGS), 0, "winch cannot push outward when incoming speed exceeds damping");
        check(PeebGrapple.pullVelocity(new Vec3(0,0,6), zero, zero, 3, SETTINGS, false).z < 0, "stationary stretched rope pulls toward anchor");
        vector(new Vec3(.18, 0, .18), PeebGrapple.constrainVelocity(new Vec3(0,0,4.57), new Vec3(.18,0,.3), zero, 4, SETTINGS, false), "safety limits only outward component");

        Random rng = new Random(9821);
        for (int i=0; i<1200; i++) {
            Vec3 axis = new Vec3(rng.nextDouble()-.5,rng.nextDouble()-.5,rng.nextDouble()-.5).normalize();
            Vec3 velocity = new Vec3(rng.nextDouble()-.5,rng.nextDouble()-.5,rng.nextDouble()-.5).scale(.14);
            Vec3 offset = axis.scale(3+rng.nextDouble()*3);
            Vec3 pulled = PeebGrapple.pullVelocity(offset,velocity,zero,2,SETTINGS,false);
            vector(tangent(velocity,axis), tangent(pulled,axis), "winch retains tangential momentum "+i);
            check(pulled.subtract(velocity).dot(axis) <= 1e-12, "winch never adds outward momentum "+i);
            check(pulled.length() <= SETTINGS.maxSpeed()+1e-12, "speed stays bounded "+i);
        }
        for (int i=0; i<400; i++) {
            Vec3 offset = new Vec3(.5 + rng.nextDouble()*7, 1.2, rng.nextDouble()*2);
            Vec3 velocity = new Vec3(rng.nextDouble()*.2-.1, -.08, rng.nextDouble()*.2-.1);
            Vec3 pulled = PeebGrapple.pullVelocity(offset,velocity,zero,2,SETTINGS,true);
            near(-.08, pulled.y, 1e-12, "floor rope never adds downward force "+i);
        }
        Vec3 takeoff = PeebGrapple.pullVelocity(new Vec3(5,1.2,0),new Vec3(0,.42,0),zero,2,SETTINGS,true);
        check(takeoff.y > .25, "normal jump remains free to lift off");

        double rest = 8;
        for (int i=0; i<400; i++) {
            double next = PeebGrapple.reelLength(rest, SETTINGS);
            check(next <= rest && next >= 2, "reel is monotonic and bounded "+i);
            check(rest-next <= PeebGrapple.MAX_REEL_PER_TICK+1e-12, "reel never snaps "+i);
            rest = next;
        }
        near(2,rest,0,"reel settles exactly at two blocks");
        vector(PeebGrapple.pullVelocity(new Vec3(0,0,1.9),new Vec3(.1,0,0),zero,rest,SETTINGS,false), new Vec3(.1,0,0), "reached anchor stop does not become a fixed rod");
        near(8,PeebGrapple.predictedRestLength(8,0,SETTINGS),0,"new packet rest length preserved");
        near(PeebGrapple.reelLength(8,SETTINGS),PeebGrapple.predictedRestLength(8,1,SETTINGS),0,"one native tick predicts one reel step");
        near(PeebGrapple.predictedRestLength(8,2,SETTINGS),PeebGrapple.predictedRestLength(8,1000000,SETTINGS),0,"late packet cannot run away or use frame clock");
        near(8,PeebGrapple.predictedRestLength(8,-5,SETTINGS),0,"negative packet age is clamped");

        // A ground wall grapple starts moving a stationary player, converges, and
        // keeps the feet on the ground. Gravity is native support, not rope push.
        Vec3 ground = new Vec3(0,1.2,7.5), groundVelocity=zero;
        double groundRest=PeebGrapple.initialRopeLength(ground.length(),8);
        double groundTravel=0;
        int groundArrivalTick=-1;
        for (int tick=0; tick<350; tick++) {
            groundRest=PeebGrapple.reelLength(groundRest,SETTINGS);
            groundVelocity=PeebGrapple.pullVelocity(ground,new Vec3(groundVelocity.x*.546,-.08,groundVelocity.z*.546),zero,groundRest,SETTINGS,true);
            groundTravel += Math.abs(groundVelocity.z);
            ground=ground.add(groundVelocity.x,0,groundVelocity.z);
            if (groundArrivalTick<0 && ground.length()<=2.05) groundArrivalTick=tick+1;
            near(1.2,ground.y,0,"floor support survives pull "+tick);
        }
        check(groundArrivalTick>0 && groundArrivalTick<=40,"default long-range hook arrives within two seconds");
        check(groundTravel>5,"ground grapple actively reels a stationary player");
        check(ground.length()<2.03,"ground rope converges near rest length rather than fixed original radius");
        check(groundVelocity.horizontalDistance()<.01,"ground pull settles without oscillation");

        // Hang from an overhead anchor with gravity: bounded compliant tension,
        // not a rigid position projection, must support the character smoothly.
        Vec3 hanging=new Vec3(0,-6.8,0), hangingVelocity=zero;
        double hangingRest=6.8, largestSpeed=0;
        for (int tick=0; tick<600; tick++) {
            hangingRest=PeebGrapple.reelLength(hangingRest,SETTINGS);
            hangingVelocity=PeebGrapple.pullVelocity(hanging,hangingVelocity.scale(.98).add(0,-.08,0),zero,hangingRest,SETTINGS,false);
            hanging=hanging.add(hangingVelocity);
            largestSpeed=Math.max(largestSpeed,hangingVelocity.length());
            check(hanging.length()<8.15,"overhead anchor stays in range "+tick);
            check(hangingVelocity.length()<=.8+1e-12,"overhead speed stays bounded "+tick);
        }
        check(hanging.y>-3,"overhead grapple lifts a stationary character");
        check(hanging.length()>=1.8 && hanging.length()<=2.05,"taut rope hangs without elastic stretch or bungee");
        check(hangingVelocity.length()<.01,"overhead winch settles smoothly");

        // Fast take-up: before reeling, the line is slack, then a single native
        // reel tick gives a clear hook impulse rather than weak elastic creep.
        double takeUpRest = PeebGrapple.reelLength(8,SETTINGS);
        Vec3 impulse = PeebGrapple.pullVelocity(new Vec3(0,0,8),zero,zero,takeUpRest,SETTINGS,false);
        check(impulse.z<=-.23 && impulse.z>=-.25,"first taut tick gives decisive controlled pull");
        check(impulse.length()<=PeebGrapple.DEFAULT_PULL_ACCELERATION+1e-12,"winch and constraint share one impulse budget");
        // Already-fast inward motion is retained, including its release momentum.
        vector(new Vec3(0,0,-.4),PeebGrapple.pullVelocity(new Vec3(0,0,3),new Vec3(0,0,-.4),zero,2.99,SETTINGS,false),"incoming speed is not braked by the winch");
        // Pure tangent at a taut line becomes a circular arc. No position
        // projection is used, and the next step stays at the rope radius.
        Vec3 pendulum = new Vec3(0,-2,0), arcVelocity=new Vec3(.2,0,0);
        Vec3 curved = PeebGrapple.pullVelocity(pendulum,arcVelocity,zero,2,SETTINGS,false);
        near(.2,curved.x,1e-12,"taut arc keeps tangent speed");
        check(curved.y>0 && curved.y<.02,"taut rope adds small inward centripetal component");
        near(2,pendulum.add(curved).length(),1e-10,"taut next-step arc has exact rope radius");
        vector(arcVelocity,PeebGrapple.pullVelocity(new Vec3(0,-1,0),arcVelocity,zero,2,SETTINGS,false),"slack line permits free momentum");
        // Native 20 Hz prediction advances the exact shared rest-length function.
        double authoritative=8,received=8; long receivedTick=0;
        for(int tick=1;tick<=40;tick++) {
            authoritative=PeebGrapple.reelLength(authoritative,SETTINGS);
            if(tick%2==0) {received=authoritative; receivedTick=tick;}
            near(authoritative,PeebGrapple.predictedRestLength(received,tick-receivedTick,SETTINGS),1e-12,"two-tick sync gives identical server/client reel "+tick);
        }

        for (double strength: new double[]{.05,.3,1}) {
            for (double range: new double[]{1,8,32}) {
                PeebConfig.Values settings=new PeebConfig.Values(range,.4,.8,strength,true);
                double start=PeebGrapple.initialRopeLength(range*.8,range);
                for(int tick=0;tick<100;tick++) {
                    start=PeebGrapple.reelLength(start,settings);
                    check(start>=Math.min(2,range) && start<=range,"live config rest bounds");
                    Vec3 v=PeebGrapple.pullVelocity(new Vec3(range*.8,1.2,0),zero,zero,start,settings,true);
                    check(Double.isFinite(v.length()) && v.length()<=.8,"live config finite bounded motion");
                }
            }
        }
        vector(zero,PeebGrapple.pullVelocity(new Vec3(Double.NaN,0,0),zero,zero,3,SETTINGS,false),"nonfinite input fails closed");
        String report="{\n  \"success\": true,\n  \"checks\": "+checks+",\n  \"ground_final_distance\": "+ground.length()+",\n  \"ground_travel\": "+groundTravel+",\n  \"ground_arrival_ticks\": "+groundArrivalTick+",\n  \"first_taut_tick_speed\": "+impulse.length()+",\n  \"overhead_final_distance\": "+hanging.length()+",\n  \"overhead_max_speed\": "+largestSpeed+",\n  \"native_game_launch_verified\": false,\n  \"human_grapple_feel_verified\": false\n}\n";
        Files.writeString(Path.of(args[0]),report);
        System.out.print(report);
    }
}
