package pl.aridlin.kukirin.mixin;
import com.wf.gemrender.entity.GemRenderEntityVisual;
import com.wf.gemrender.render.GemRenderInstance;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
@Mixin(GemRenderEntityVisual.class)
public interface ScooterVisualAccessor {
 @Accessor("instance") GemRenderInstance goplanskaScooterInstance();
}
