package pl.aridlin.psychiatrykroles.runtime.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.model.geom.EntityModelSet;
import net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderDispatcher;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;

/** Eight vertices per item, with a static fallback and no render-time decode or model bake. */
public final class RuntimeItemRenderer extends BlockEntityWithoutLevelRenderer {
    private static final ResourceLocation FALLBACK=ResourceLocation.parse("minecraft:textures/item/paper.png");

    public RuntimeItemRenderer(BlockEntityRenderDispatcher dispatcher,EntityModelSet models){super(dispatcher,models);}

    @Override public void renderByItem(ItemStack stack,ItemDisplayContext context,PoseStack pose,
                                       MultiBufferSource buffers,int light,int overlay){
        ResourceLocation texture=RuntimeItemVisuals.texture(stack);
        if(texture==null)texture=FALLBACK;
        VertexConsumer out=buffers.getBuffer(RenderType.entityCutoutNoCull(texture));
        quad(out,pose,light,0,0,1,0,0,.5f,1,0,.5f,1,1,.5f,0,1,.5f);
        quad(out,pose,light,0,0,-1,1,0,.5f,0,0,.5f,0,1,.5f,1,1,.5f);
    }

    private static void quad(VertexConsumer out,PoseStack pose,int light,float nx,float ny,float nz,
                             float ax,float ay,float az,float bx,float by,float bz,
                             float cx,float cy,float cz,float dx,float dy,float dz){
        vertex(out,pose,light,nx,ny,nz,ax,ay,az,0,1);
        vertex(out,pose,light,nx,ny,nz,bx,by,bz,1,1);
        vertex(out,pose,light,nx,ny,nz,cx,cy,cz,1,0);
        vertex(out,pose,light,nx,ny,nz,dx,dy,dz,0,0);
    }

    private static void vertex(VertexConsumer out,PoseStack pose,int light,float nx,float ny,float nz,
                               float x,float y,float z,float u,float v){
        out.addVertex(pose.last().pose(),x,y,z).setColor(255,255,255,255)
            .setUv(u,v).setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(pose.last(),nx,ny,nz);
    }
}
