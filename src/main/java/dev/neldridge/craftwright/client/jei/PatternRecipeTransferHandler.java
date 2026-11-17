package dev.neldridge.craftwright.client.jei;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import dev.neldridge.craftwright.CrafterMenu;
import dev.neldridge.craftwright.Craftwright;
import dev.neldridge.craftwright.client.CrafterScreen;
import dev.neldridge.craftwright.network.*;
import mezz.jei.api.constants.RecipeTypes;
import mezz.jei.api.gui.ingredient.IRecipeSlotsView;
import mezz.jei.api.recipe.RecipeIngredientRole;
import mezz.jei.api.recipe.transfer.*;
import mezz.jei.api.recipe.types.IRecipeType;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.*;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;
import org.jspecify.annotations.Nullable;

public final class PatternRecipeTransferHandler implements IRecipeTransferHandler<CrafterMenu, RecipeHolder<CraftingRecipe>> {
    private final IRecipeTransferHandlerHelper helper;
    public PatternRecipeTransferHandler(IRecipeTransferHandlerHelper helper) { this.helper = helper; }
    @Override public Class<? extends CrafterMenu> getContainerClass() { return CrafterMenu.class; }
    @Override public Optional<MenuType<CrafterMenu>> getMenuType() { return Optional.of(Craftwright.CRAFTER_MENU.get()); }
    @Override public IRecipeType<RecipeHolder<CraftingRecipe>> getRecipeType() { return RecipeTypes.CRAFTING; }

    /** JEI's nine INPUT slot views are already positioned in its displayed 3x3 grid, including empty cells. */
    private @Nullable List<ItemStack> grid(IRecipeSlotsView slots) {
        var inputs = slots.getSlotViews(RecipeIngredientRole.INPUT);
        if (inputs.size() != 9) return null;
        List<ItemStack> grid = new ArrayList<>(9);
        for (var slot : inputs) {
            var stack = slot.getDisplayedItemStack().orElseGet(() -> slot.getItemStacks().findFirst().orElse(ItemStack.EMPTY));
            grid.add(stack.isEmpty() ? ItemStack.EMPTY : stack.copyWithCount(1));
        }
        return grid;
    }
    private @Nullable IRecipeTransferError validate(CrafterMenu menu, RecipeHolder<CraftingRecipe> recipe, Player player, @Nullable List<ItemStack> grid) {
        if (menu.selected() < 0 || menu.selected() >= menu.capacity()) return error("jei_locked");
        if (grid == null) return error("jei_unsupported");
        var input = CraftingInput.of(3, 3, grid);
        if (!recipe.value().matches(input, player.level()) || recipe.value().assemble(input).isEmpty()) return error("jei_invalid");
        return null;
    }
    private IRecipeTransferError error(String key) { return helper.createUserErrorWithTooltip(Component.translatable("gui.craftwright." + key)); }
    @Override public @Nullable IRecipeTransferError transferRecipe(IRecipeTransferContext<RecipeHolder<CraftingRecipe>, CrafterMenu> context, boolean doTransfer) {
        var menu = context.getContainer();
        var recipe = context.getRecipe();
        var grid = grid(context.getRecipeSlots());
        var error = validate(menu, recipe, context.getPlayer(), grid);
        if (error != null || !doTransfer) return error;
        PatternTransferFeedback.await(menu.containerId, context.getTransferId(), success -> {
            if (success && context.getScreen() instanceof CrafterScreen screen) screen.showPatterns();
            context.completeRecipeTransfer(success ? RecipeTransferResult.SUCCESS : RecipeTransferResult.REJECTED);
        });
        ClientPacketDistributor.sendToServer(new PatternTransferPayload(menu.containerId, context.getTransferId(), menu.selected(), recipe.id().identifier(), grid));
        return null;
    }
    @SuppressWarnings("removal")
    @Override public @Nullable IRecipeTransferError transferRecipe(CrafterMenu menu, RecipeHolder<CraftingRecipe> recipe, IRecipeSlotsView slots, Player player, boolean maxTransfer, boolean doTransfer) {
        // Retain JEI's legacy entry point; the current API above waits for server confirmation.
        var grid = grid(slots);
        var error = validate(menu, recipe, player, grid);
        if (error != null || !doTransfer) return error;
        menu.view = 0;
        ClientPacketDistributor.sendToServer(new PatternTransferPayload(menu.containerId, -1, menu.selected(), recipe.id().identifier(), grid));
        return null;
    }
}
