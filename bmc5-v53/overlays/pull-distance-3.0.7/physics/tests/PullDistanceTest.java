import net.minecraft.world.phys.Vec3;
import pl.aridlin.psychiatrykroles.peeb.PeebAdventuresPhysics;
public final class PullDistanceTest {
    static int checks;
    static void check(boolean ok, String name) { checks++; if (!ok) throw new AssertionError(name); }
    static void near(double x,double y,String name) { check(Math.abs(x-y)<1e-9,name); }
    public static void main(String[] args) {
        for (double stop : new double[]{0,.1,.5,1,3,8,32}) {
            double rope=8, target=Math.min(8,stop);
            for(int i=0;i<240;i++) {
                double next=PeebAdventuresPhysics.reel(rope,8,stop,.05);
                check(next>=Math.min(rope,target)-1e-9 && next<=Math.max(rope,target)+1e-9,"monotonic bounded takeup");
                rope=next;
            }
            near(rope,target,"exact configurable rest target");
        }
        near(PeebAdventuresPhysics.reel(0,8,0,.05),0,"zero stays zero");
        near(PeebAdventuresPhysics.reel(8,8,.05),PeebAdventuresPhysics.reel(8,8,0,.05),"legacy helper defaults full pull");
        near(PeebAdventuresPhysics.targetLength(4,32),4,"stop never exceeds attachment range");
        Vec3 incoming=new Vec3(2,.3,0);
        for(double distance:new double[]{3,1,.5,.1,.001}) {
            Vec3 hook=new Vec3(0,distance,0);
            Vec3 pulled=PeebAdventuresPhysics.pull(Vec3.ZERO,incoming,hook,0,1,1,.05);
            near(pulled.x,incoming.x,"incoming sideways momentum survives close pull");
            check(pulled.y>incoming.y,"still pulls inside old three-block gap");
            check(Double.isFinite(pulled.y),"zero-rest spring finite");
            Vec3 bounded=PeebAdventuresPhysics.addWithinBudget(incoming,pulled,.1,.2);
            near(bounded.x,incoming.x,"force budget preserves existing momentum");
            check(bounded.subtract(incoming).length()<=.2+1e-9,"close-anchor force budget");
        }
        Vec3 slack=PeebAdventuresPhysics.pull(Vec3.ZERO,incoming,new Vec3(0,.5,0),1,1,1,.05);
        check(slack.equals(incoming),"configured slack radius gives no extra pull");
        check(PeebAdventuresPhysics.pull(Vec3.ZERO,incoming,Vec3.ZERO,0,1,1,.05).equals(incoming),"at anchor no artificial stop or NaN");
        Vec3 coast=PeebAdventuresPhysics.coastStep(incoming,Vec3.ZERO,false,false,.05);
        near(coast.x,incoming.x,"release coast momentum retained");
        System.out.println("{\"success\":true,\"checks\":"+checks+",\"full_pull_zero_rest\":true,\"configurable_slack\":true,\"close_anchor_momentum\":true,\"game_launched\":false}");
    }
}
