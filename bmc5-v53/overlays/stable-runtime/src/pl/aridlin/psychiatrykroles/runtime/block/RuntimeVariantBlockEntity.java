package pl.aridlin.psychiatrykroles.runtime.block;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/** The only per-placement state sent to clients is a bounded variant identifier. */
public final class RuntimeVariantBlockEntity extends BlockEntity {
    public static final String DEFAULT_VARIANT="missing";
    private static final String NBT_KEY="Variant";
    private String variantId=DEFAULT_VARIANT;

    public RuntimeVariantBlockEntity(BlockPos pos,BlockState state){super(RuntimeBlocks.RUNTIME_ENTITY.get(),pos,state);}
    public String variantId(){return variantId;}
    public static boolean validVariantId(String id){return id!=null&&id.length()<=64&&id.matches("[a-z0-9_-]+(?:/[a-z0-9_-]+){0,2}");}
    public static String requireVariantId(String id){
        if(!validVariantId(id))throw new IllegalArgumentException("Invalid runtime block variant ID");
        return id;
    }
    public void setVariantId(String id){
        requireVariantId(id);
        if(id.equals(variantId))return;
        variantId=id;setChanged();
        if(level!=null&&!level.isClientSide)level.sendBlockUpdated(worldPosition,getBlockState(),getBlockState(),3);
    }
    @Override protected void loadAdditional(CompoundTag tag,HolderLookup.Provider registry){
        super.loadAdditional(tag,registry);
        String saved=tag.getString(NBT_KEY);
        variantId=validVariantId(saved)?saved:DEFAULT_VARIANT;
    }
    @Override protected void saveAdditional(CompoundTag tag,HolderLookup.Provider registry){
        super.saveAdditional(tag,registry);tag.putString(NBT_KEY,variantId);
    }
    @Override public CompoundTag getUpdateTag(HolderLookup.Provider registry){return saveWithoutMetadata(registry);}
    @Override public Packet<ClientGamePacketListener> getUpdatePacket(){return ClientboundBlockEntityDataPacket.create(this);}
}
