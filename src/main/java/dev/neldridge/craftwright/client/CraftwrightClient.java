package dev.neldridge.craftwright.client;

import dev.neldridge.craftwright.Craftwright;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;

@Mod(value = Craftwright.MOD_ID, dist = Dist.CLIENT)
public final class CraftwrightClient {
    public CraftwrightClient(IEventBus bus) { bus.addListener(CraftwrightClient::screens); }
    private static void screens(RegisterMenuScreensEvent event) { event.register(Craftwright.CRAFTER_MENU.get(), CrafterScreen::new); }
}
