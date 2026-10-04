package pl.aridlin.psychiatrykroles;

import net.minecraft.core.registries.Registries;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.world.inventory.MenuType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/** Stable container identity; poker actions and balances remain server-owned. */
final class PokerMenus {
    private static final DeferredRegister<MenuType<?>> MENUS =
        DeferredRegister.create(Registries.MENU, PsychiatrykRoles.MOD_ID);

    static final DeferredHolder<MenuType<?>, MenuType<PokerMenu>> TYPE =
        MENUS.register("poker", () -> new MenuType<>(PokerMenu::new, FeatureFlags.DEFAULT_FLAGS));

    static void register(IEventBus modBus) { MENUS.register(modBus); }

    private PokerMenus() {}
}
