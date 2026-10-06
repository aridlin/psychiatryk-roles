package pl.aridlin.kukirin;
import net.minecraft.world.item.*;
import net.minecraft.core.registries.*;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.registries.DeferredRegister;
/** Scooter-only creative tab: never loads another mod's items or client classes. */
public final class PsychiatrykCreative {
 private static final DeferredRegister<CreativeModeTab> TABS=DeferredRegister.create(Registries.CREATIVE_MODE_TAB,"goplanska_kukirin");
 static {TABS.register("scooters",()->CreativeModeTab.builder().title(Component.literal("Portable Scooters")).icon(()->new ItemStack(Kukirin.ITEM.get())).displayItems((parameters,out)->{
 out.accept(Kukirin.ITEM.get());out.accept(Kukirin.DISMANTLER_ITEM.get());for(var preset:ScooterPresets.create(parameters.holders()))out.accept(preset);for(var rental:ScooterRental.presets())out.accept(rental);
 }).build());}
 public static void register(net.neoforged.bus.api.IEventBus bus){TABS.register(bus);}
}
