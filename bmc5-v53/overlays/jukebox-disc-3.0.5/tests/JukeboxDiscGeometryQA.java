import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import java.util.ArrayList;
import java.util.List;
import pl.aridlin.psychiatrykroles.jukebox.client.JukeboxCoverGeometry;

/** Exercises the real submitted geometry, not a duplicate implementation. */
public final class JukeboxDiscGeometryQA {
    private static int checks;
    private static void check(boolean value, String description) {
        checks++;
        if (!value) throw new AssertionError(description);
    }
    private static void near(double actual, double expected, double epsilon, String description) {
        check(Math.abs(actual - expected) <= epsilon, description + ": " + actual + " != " + expected);
    }
    private static final class V {
        float x, y, z, u, v, nx, ny, nz;
        int r, g, b, a;
    }
    private static final class Capture implements VertexConsumer {
        final List<V> vertices = new ArrayList<>();
        V current;
        public VertexConsumer addVertex(float x, float y, float z) {
            current = new V(); current.x=x; current.y=y; current.z=z; vertices.add(current); return this;
        }
        public VertexConsumer setColor(int r,int g,int b,int a) {
            current.r=r; current.g=g; current.b=b; current.a=a; return this;
        }
        public VertexConsumer setUv(float u,float v) {current.u=u;current.v=v;return this;}
        public VertexConsumer setUv1(int u,int v) {return this;}
        public VertexConsumer setUv2(int u,int v) {return this;}
        public VertexConsumer setNormal(float x,float y,float z) {current.nx=x;current.ny=y;current.nz=z;return this;}
    }
    private static void submitted(Capture c, boolean topOnly) {
        check(c.vertices.size() > 0 && c.vertices.size() % 4 == 0, "Complete quads");
        for (V v : c.vertices) {
            check(Float.isFinite(v.x)&&Float.isFinite(v.y)&&Float.isFinite(v.z), "Finite position");
            check(v.u>=0&&v.u<=1&&v.v>=0&&v.v<=1, "UV inside source texture");
            check(v.a==255, "Solid disc pixels");
            check(v.y>1.0F, "Disc above block top");
            if (topOnly) near(v.ny, 1.0, 1e-6, "Top normal");
        }
        if (topOnly) for (int i=0;i<c.vertices.size();i+=4) {
            V a=c.vertices.get(i), b=c.vertices.get(i+1), d=c.vertices.get(i+2);
            double up=(b.z-a.z)*(d.x-a.x)-(b.x-a.x)*(d.z-a.z);
            check(up>0, "Top-facing winding");
        }
    }
    public static void main(String[] args) {
        near(JukeboxCoverGeometry.rotationDegrees(0.0),0,1e-8,"Initial rotation");
        near(JukeboxCoverGeometry.rotationDegrees(0.45),90,1e-8,"Quarter rotation");
        near(JukeboxCoverGeometry.rotationDegrees(1.0),200,1e-8,"33 1/3 RPM");
        near(JukeboxCoverGeometry.rotationDegrees(1.8),0,1e-8,"Loop period");
        near(JukeboxCoverGeometry.rotationDegrees(1800000.45),90,1e-6,"Long-session precision");
        for (double invalid : new double[]{-1,Double.NaN,Double.POSITIVE_INFINITY}) near(JukeboxCoverGeometry.rotationDegrees(invalid),0,1e-8,"Invalid timestamp");
        Capture rim=new Capture();JukeboxCoverGeometry.vinylRim(new PoseStack(),rim,0xf000f0);submitted(rim,false);
        Capture art=new Capture();JukeboxCoverGeometry.vinylArt(new PoseStack(),art,0xf000f0,0);submitted(art,true);
        Capture vinyl0=new Capture(),label0=new Capture(),vinyl90=new Capture(),label90=new Capture();
        JukeboxCoverGeometry.vinylFallback(new PoseStack(),vinyl0,0xf000f0,0);
        JukeboxCoverGeometry.vinylFallbackArt(new PoseStack(),label0,0xf000f0,0);
        JukeboxCoverGeometry.vinylFallback(new PoseStack(),vinyl90,0xf000f0,0.45);
        JukeboxCoverGeometry.vinylFallbackArt(new PoseStack(),label90,0xf000f0,0.45);
        submitted(vinyl0,true);submitted(label0,true);submitted(vinyl90,true);submitted(label90,true);
        int stripeStart=JukeboxCoverGeometry.SECTORS*4;
        for (int i=stripeStart;i<vinyl0.vertices.size();i++) {
            V a=vinyl0.vertices.get(i),b=vinyl90.vertices.get(i);
            near(b.x-0.5,-(a.z-0.5),1e-6,"Paint rotates on actual mesh");
            near(b.z-0.5,a.x-0.5,1e-6,"Paint rotates on actual mesh");
        }
        check(vinyl0.vertices.get(stripeStart).r!=vinyl0.vertices.get(stripeStart+4).r,"Asymmetric paint colors");
        V uv0=art.vertices.get(0);
        Capture art90=new Capture();JukeboxCoverGeometry.vinylArt(new PoseStack(),art90,0xf000f0,0.45);
        check(Math.abs(uv0.u-art90.vertices.get(0).u)>0.01,"Cover UV rotates");
        PoseStack cameraRelative=new PoseStack();cameraRelative.translate(37-36.2,64-65.6,-19-(-16));
        Capture transformed=new Capture();JukeboxCoverGeometry.vinylArt(cameraRelative,transformed,0xf000f0,0);
        near(transformed.vertices.get(0).x,0.8+art.vertices.get(0).x,1e-6,"One camera subtraction on x");
        near(transformed.vertices.get(0).y,-1.6+art.vertices.get(0).y,1e-6,"One camera subtraction on y");
        near(transformed.vertices.get(0).z,-3+art.vertices.get(0).z,1e-6,"One camera subtraction on z");
        System.out.println("{\"success\":true,\"checks\":"+checks+",\"rim_vertices\":"+rim.vertices.size()+",\"cover_vertices\":"+art.vertices.size()+",\"fallback_vertices\":"+(vinyl0.vertices.size()+label0.vertices.size())+",\"native_pixels_verified\":false}");
    }
}
