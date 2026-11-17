package dev.neldridge.craftwright.client.jei;

import dev.neldridge.craftwright.Craftwright;
import dev.neldridge.craftwright.network.PatternTransferFeedback;
import mezz.jei.api.IModPlugin;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.constants.RecipeTypes;
import mezz.jei.api.registration.IRecipeCatalystRegistration;
import mezz.jei.api.registration.IRecipeTransferRegistration;
import net.minecraft.resources.Identifier;

@JeiPlugin
public final class CraftwrightJeiPlugin implements IModPlugin {
    @Override public Identifier getPluginUid() { return Identifier.fromNamespaceAndPath(Craftwright.MOD_ID, "jei"); }
    @Override public void registerRecipeTransferHandlers(IRecipeTransferRegistration registration) {
        registration.addRecipeTransferHandler(new PatternRecipeTransferHandler(registration.getTransferHelper()), RecipeTypes.CRAFTING);
    }
    @Override public void registerRecipeCatalysts(IRecipeCatalystRegistration registration) {
        registration.addCraftingStation(RecipeTypes.CRAFTING, Craftwright.CRAFTER.get(), Craftwright.ADVANCED_CRAFTER.get());
    }
    @Override public void onRuntimeUnavailable() { PatternTransferFeedback.clear(); }
}
