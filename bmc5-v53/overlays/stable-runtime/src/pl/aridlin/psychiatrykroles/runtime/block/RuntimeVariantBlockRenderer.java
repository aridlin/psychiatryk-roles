package pl.aridlin.psychiatrykroles.runtime.block;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;

/** A bounded six-face renderer: no display entities, commands, scripts or dynamic registry entries. */
public final class RuntimeVariantBlockRenderer implements BlockEntityRenderer<RuntimeVariantBlockEntity> {
    private static final ResourceLocation MISSING=ResourceLocation.parse("minecraft:textures/block/obsidian.png");
    private static final RuntimeVariantVisuals.Box MISSING_BOX=new RuntimeVariantVisuals.Box(0,0,0,1,1,1,"",255,255,255);
    public RuntimeVariantBlockRenderer(BlockEntityRendererProvider.Context ignored){}
    @Override public int getViewDistance(){return 32;}
    @Override public void render(RuntimeVariantBlockEntity entity,float partial,PoseStack pose,MultiBufferSource buffers,int light,int overlay){
        RuntimeVariantVisuals.Variant variant=RuntimeVariantVisuals.get(entity.variantId());
        if(variant==null){
            drawBox(MISSING_BOX,MISSING,pose,buffers,light);
            return;
        }
        for(RuntimeVariantVisuals.Box box:variant.boxes()){
            drawBox(box,box.textureLocation()==null?MISSING:box.textureLocation(),pose,buffers,light);
        }
    }
    private static void drawBox(RuntimeVariantVisuals.Box box,ResourceLocation texture,PoseStack pose,MultiBufferSource buffers,int light){
        VertexConsumer out=buffers.getBuffer(RenderType.entityCutoutNoCull(texture));
        float x0=box.x0(),x1=box.x1(),y0=box.y0(),y1=box.y1(),z0=box.z0(),z1=box.z1();
        quad(out,pose,box,light,0,-1,0,x0,y0,z1,x1,y0,z1,x1,y0,z0,x0,y0,z0); // bottom
        quad(out,pose,box,light,0,1,0,x0,y1,z0,x1,y1,z0,x1,y1,z1,x0,y1,z1);  // top
        quad(out,pose,box,light,0,0,-1,x1,y0,z0,x0,y0,z0,x0,y1,z0,x1,y1,z0); // north
        quad(out,pose,box,light,0,0,1,x0,y0,z1,x1,y0,z1,x1,y1,z1,x0,y1,z1);  // south
        quad(out,pose,box,light,-1,0,0,x0,y0,z0,x0,y0,z1,x0,y1,z1,x0,y1,z0); // west
        quad(out,pose,box,light,1,0,0,x1,y0,z1,x1,y0,z0,x1,y1,z0,x1,y1,z1);  // east
    }
    private static void quad(VertexConsumer out,PoseStack pose,RuntimeVariantVisuals.Box box,int light,
            float nx,float ny,float nz,float ax,float ay,float az,float bx,float by,float bz,
            float cx,float cy,float cz,float dx,float dy,float dz){
        vertex(out,pose,box,light,nx,ny,nz,ax,ay,az,0,1);
        vertex(out,pose,box,light,nx,ny,nz,bx,by,bz,1,1);
        vertex(out,pose,box,light,nx,ny,nz,cx,cy,cz,1,0);
        vertex(out,pose,box,light,nx,ny,nz,dx,dy,dz,0,0);
    }
    private static void vertex(VertexConsumer out,PoseStack pose,RuntimeVariantVisuals.Box box,int light,
            float nx,float ny,float nz,float x,float y,float z,float u,float v){
        out.addVertex(pose.last().pose(),x,y,z).setColor(box.red(),box.green(),box.blue(),255)
            .setUv(u,v).setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(pose.last(),nx,ny,nz);
    }
}
