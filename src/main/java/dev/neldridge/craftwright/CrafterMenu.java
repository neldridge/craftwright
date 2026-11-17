package dev.neldridge.craftwright;

import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.*;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.*;
import net.neoforged.neoforge.transfer.item.ResourceHandlerSlot;

public class CrafterMenu extends AbstractContainerMenu {
    public static final int PATTERN_START = 31, DRAFT_START = 43, REMAINDER_START = 53, PLAYER_START = 62;
    public static final int SAVE = 20, DELETE = 21, ADD = 22, AUTO_OUTPUT = 23, TOGGLE = 24;
    public final CrafterBlockEntity machine;
    public final CrafterTier tier;
    private final Player player;
    private final BlockPos pos;
    private final SimpleContainer previews = new SimpleContainer(12);
    private final SimpleContainer draft = new SimpleContainer(10);
    private final SimpleContainer remainders = new SimpleContainer(9);
    private int selected, valid;
    /** Client-only presentation tab: 0 patterns, 1 inventory, 2 sides. */
    public int view;
    public final ContainerData data;

    public CrafterMenu(int id, Inventory inventory, FriendlyByteBuf buf) {
        this(id, inventory, buf.readBlockPos(), buf.readEnum(CrafterTier.class));
    }
    private CrafterMenu(int id, Inventory inventory, BlockPos pos, CrafterTier tier) {
        this(id, inventory, new CrafterBlockEntity(pos, (tier == CrafterTier.REGULAR ? Craftwright.CRAFTER.get() : Craftwright.ADVANCED_CRAFTER.get()).defaultBlockState()));
    }
    public CrafterMenu(int id, Inventory inventory, CrafterBlockEntity machine) {
        super(Craftwright.CRAFTER_MENU.get(), id);
        this.machine = machine;
        tier = machine.tier;
        player = inventory.player;
        pos = machine.getBlockPos();
        boolean server = player.level() instanceof ServerLevel;
        for (int i = 0; i < CrafterBlockEntity.INPUTS; i++) addSlot(new ResourceHandlerSlot(machine.inputs, machine.inputs::set, i, 12 + i % 9 * 18, 62 + i / 9 * 18) {
            @Override public boolean isActive() { return view == 1; }
        });
        for (int i = 0; i < CrafterBlockEntity.OUTPUTS; i++) addSlot(new ResourceHandlerSlot(machine.outputs, machine.outputs::set, i, 186 + i % 3 * 18, 62 + i / 3 * 18) {
            @Override public boolean mayPlace(ItemStack stack) { return false; }
            @Override public boolean isActive() { return view == 1; }
        });
        for (int i = 0; i < 4; i++) addSlot(new ResourceHandlerSlot(machine.upgrades, machine.upgrades::set, i, 250 + i % 2 * 28, 62 + i / 2 * 28) {
            @Override public boolean isActive() { return view == 1; }
        });
        for (int i = 0; i < 12; i++) addSlot(ghost(previews, i, 12 + i % 4 * 18, 58 + i / 4 * 18));
        for (int i = 0; i < 9; i++) addSlot(ghost(draft, i, 106 + i % 3 * 18, 58 + i / 3 * 18));
        addSlot(ghost(draft, 9, 186, 76));
        for (int i = 0; i < 9; i++) addSlot(ghost(remainders, i, -1000, -1000));
        addStandardInventorySlots(inventory, 12, 156);
        data = server ? new ContainerData() {
            @Override public int get(int i) {
                return switch (i) {
                    case 0 -> machine.energy.getAmountAsInt() & 65535;
                    case 1 -> machine.energy.getAmountAsInt() >>> 16;
                    case 2 -> machine.progress(); case 3 -> machine.craftTicks(); case 4 -> machine.capacity();
                    case 5 -> machine.status; case 6 -> selected; case 7 -> valid;
                    case 8 -> machine.workFe() & 65535; case 9 -> machine.enabled ? 1 : 0;
                    case 16 -> machine.workFe() >>> 16;
                    default -> i >= 10 && i < 16 ? machine.sideMode(Direction.values()[i - 10]).ordinal() : 0;
                };
            }
            @Override public void set(int i, int value) {}
            @Override public int getCount() { return 17; }
        } : new SimpleContainerData(17);
        addDataSlots(data);
        if (server) loadPattern(0);
    }
    private Slot ghost(SimpleContainer inventory, int containerIndex, int x, int y) {
        return new Slot(inventory, containerIndex, x, y) {
            // Slot.index is the overall menu index, not the index in the preview container.
            @Override public boolean isActive() { return view == 0 && inventory != remainders && (inventory != previews || containerIndex < tier.maxPatterns); }
            @Override public boolean mayPickup(Player player) { return false; }
            @Override public boolean mayPlace(ItemStack stack) { return false; }
        };
    }
    public int energy() { return (data.get(0) & 65535) | (data.get(1) & 65535) << 16; }
    public int workFe() { return (data.get(8) & 65535) | (data.get(16) & 65535) << 16; }
    public int selected() { return data.get(6); }
    public int capacity() { return data.get(4); }
    public ItemStack preview(int index) { return previews.getItem(index); }
    public ItemStack remainder(int index) { return remainders.getItem(index); }
    private List<ItemStack> draftGrid() {
        List<ItemStack> result = new ArrayList<>(9);
        for (int i = 0; i < 9; i++) result.add(draft.getItem(i).copy());
        return result;
    }
    private void loadPattern(int index) {
        selected = index;
        for (int i = 0; i < 10; i++) draft.setItem(i, machine.patternStack(index, i));
        updateDraft(false);
    }
    private void updateDraft(boolean autoOutput) {
        if (!(player.level() instanceof ServerLevel level)) return;
        List<ItemStack> grid = draftGrid();
        CraftingRecipe recipe = CrafterBlockEntity.findRecipe(level, grid, autoOutput ? ItemStack.EMPTY : draft.getItem(9));
        CraftingInput input = CraftingInput.of(3, 3, grid);
        if (autoOutput) draft.setItem(9, recipe == null ? ItemStack.EMPTY : recipe.assemble(input));
        valid = recipe != null && !draft.getItem(9).isEmpty() ? 1 : 0;
        remainders.clearContent();
        if (valid == 1) {
            var returned = recipe.getRemainingItems(input);
            for (int i = 0; i < returned.size() && i < 9; i++) remainders.setItem(i, returned.get(i));
        }
    }
    /** Apply a recipe-viewer transfer to the draft only. Real inventories and saved patterns are untouched. */
    public boolean importPattern(Player sender, int pattern, net.minecraft.resources.Identifier recipeId, List<ItemStack> ingredients) {
        if (sender != player || !stillValid(sender) || !(sender.level() instanceof ServerLevel level)
            || pattern != selected || pattern < 0 || pattern >= machine.capacity() || ingredients.size() != 9) return false;
        var key = net.minecraft.resources.ResourceKey.create(net.minecraft.core.registries.Registries.RECIPE, recipeId);
        var holder = level.recipeAccess().byKey(key).orElse(null);
        if (holder == null || !(holder.value() instanceof CraftingRecipe recipe)) return false;
        List<ItemStack> grid = ingredients.stream().map(s -> s.isEmpty() ? ItemStack.EMPTY : s.copyWithCount(1)).toList();
        var input = CraftingInput.of(3, 3, grid);
        if (!recipe.matches(input, level)) return false;
        ItemStack result = recipe.assemble(input);
        if (result.isEmpty()) return false;
        for (int i = 0; i < 9; i++) draft.setItem(i, grid.get(i));
        draft.setItem(9, result);
        updateDraft(false);
        broadcastChanges();
        return valid == 1;
    }
    @Override public void broadcastChanges() {
        if (player.level() instanceof ServerLevel) {
            for (int i = 0; i < 12; i++) previews.setItem(i, machine.patternStack(i, 9));
            // Recipe reloads must also invalidate an open editor's validation state.
            updateDraft(false);
        }
        super.broadcastChanges();
    }
    @Override public boolean clickMenuButton(Player player, int button) {
        if (!stillValid(player) || !(player.level() instanceof ServerLevel)) return false;
        if (button >= 0 && button < machine.capacity()) { loadPattern(button); return true; }
        if (button >= 30 && button < 36) { machine.cycleSide(Direction.values()[button - 30]); return true; }
        switch (button) {
            case SAVE -> {
                updateDraft(false);
                if (selected < machine.capacity() && valid == 1) machine.setPattern(selected, draftGrid(), draft.getItem(9));
                else return false;
            }
            case DELETE -> { machine.setPattern(selected, Collections.nCopies(9, ItemStack.EMPTY), ItemStack.EMPTY); loadPattern(selected); }
            case ADD -> {
                for (int i = 0; i < machine.capacity(); i++) if (machine.patternStack(i, 9).isEmpty()) { loadPattern(i); return true; }
                return false;
            }
            case AUTO_OUTPUT -> updateDraft(true);
            case TOGGLE -> { machine.enabled = !machine.enabled; machine.setChanged(); }
            default -> { return false; }
        }
        broadcastChanges();
        return true;
    }
    @Override public void clicked(int slot, int button, ContainerInput input, Player player) {
        if (!stillValid(player)) return;
        if (slot >= PATTERN_START && slot < PLAYER_START) {
            if (!(player.level() instanceof ServerLevel)) return;
            if (slot < DRAFT_START) {
                int index = slot - PATTERN_START;
                if (index < machine.capacity()) loadPattern(index);
            } else if (slot < REMAINDER_START && input == ContainerInput.PICKUP) {
                int index = slot - DRAFT_START;
                ItemStack held = getCarried();
                draft.setItem(index, button == 1 || held.isEmpty() ? ItemStack.EMPTY : held.copyWithCount(index == 9 ? held.getCount() : 1));
                updateDraft(index < 9);
            }
            broadcastChanges();
            return;
        }
        super.clicked(slot, button, input, player);
    }
    @Override public boolean canDragTo(Slot slot) { return slot.index < PATTERN_START || slot.index >= PLAYER_START; }
    @Override public boolean canTakeItemForPickAll(ItemStack stack, Slot slot) { return canDragTo(slot) && super.canTakeItemForPickAll(stack, slot); }
    @Override public ItemStack quickMoveStack(Player player, int index) {
        if (index < 0 || index >= slots.size() || index >= PATTERN_START && index < PLAYER_START) return ItemStack.EMPTY;
        Slot slot = slots.get(index);
        if (!slot.hasItem()) return ItemStack.EMPTY;
        ItemStack stack = slot.getItem(), original = stack.copy();
        if (index < PATTERN_START) {
            if (!moveItemStackTo(stack, PLAYER_START, slots.size(), true)) return ItemStack.EMPTY;
        } else if (stack.getItem() instanceof UpgradeItem upgrade) {
            int target = 27 + upgrade.kind.ordinal();
            if (!moveItemStackTo(stack, target, target + 1, false)) return ItemStack.EMPTY;
        } else if (!moveItemStackTo(stack, 0, CrafterBlockEntity.INPUTS, false)) return ItemStack.EMPTY;
        if (stack.isEmpty()) slot.setByPlayer(ItemStack.EMPTY); else slot.setByPlayer(stack);
        slot.onTake(player, stack);
        return original;
    }
    @Override public boolean stillValid(Player player) {
        if (player.level().isClientSide()) return true;
        return player.level().getBlockEntity(pos) == machine && player.distanceToSqr(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5) <= 64;
    }
}
