package dev.neldridge.craftwright;

import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Containers;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.*;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.transfer.*;
import net.neoforged.neoforge.transfer.energy.*;
import net.neoforged.neoforge.transfer.item.*;
import net.neoforged.neoforge.transfer.transaction.*;

public class CrafterBlockEntity extends BlockEntity implements MenuProvider {
    public static final int INPUTS = 18, OUTPUTS = 9, MAX_PATTERNS = 12;
    public final CrafterTier tier;
    public final ItemStacksResourceHandler inputs = storage(INPUTS);
    public final ItemStacksResourceHandler outputs = storage(OUTPUTS);
    /** Nine ghost ingredients and one expected output per pattern. Never exposed as real inventory. */
    public final ItemStacksResourceHandler patterns = storage(MAX_PATTERNS * 10);
    public final ItemStacksResourceHandler upgrades = new ItemStacksResourceHandler(4) {
        @Override public boolean isValid(int slot, ItemResource resource) {
            return resource.toStack().getItem() instanceof UpgradeItem u && u.kind.ordinal() == slot;
        }
        @Override protected int getCapacity(int slot, ItemResource resource) {
            return switch (slot) { case 0 -> MachineBalance.MAX_SPEED; case 1 -> tier.maxPatterns - tier.basePatterns; default -> 1; };
        }
        @Override protected void onContentsChanged(int slot, ItemStack previous) { setChanged(); }
    };
    private final SideMode[] sides = {SideMode.OUTPUT, SideMode.INPUT, SideMode.INPUT, SideMode.INPUT, SideMode.INPUT, SideMode.INPUT};
    public final SimpleEnergyHandler energy;
    private final EnergyHandler energyInput;
    private int progress, nextPattern, activePattern = -1, transferFace;
    private final int[] patternProgress = new int[MAX_PATTERNS];
    public int status; // 0 idle, 1 missing input, 2 output full, 3 no power, 4 working, 5 invalid pattern
    public boolean enabled = true;
    private RecipeMap cachedMap;
    private final CraftingRecipe[] recipeCache = new CraftingRecipe[MAX_PATTERNS];
    private final boolean[] recipeChecked = new boolean[MAX_PATTERNS];

    public CrafterBlockEntity(BlockPos pos, BlockState state) {
        super(Craftwright.CRAFTER_BE.get(), pos, state);
        tier = ((CrafterBlock) state.getBlock()).tier;
        energy = new SimpleEnergyHandler(tier.energyCapacity) {
            @Override protected void onEnergyChanged(int previous) { setChanged(); }
        };
        energyInput = new DelegatingEnergyHandler(energy) {
            @Override public int extract(int amount, TransactionContext tx) { return 0; }
        };
    }
    private ItemStacksResourceHandler storage(int size) {
        return new ItemStacksResourceHandler(size) {
            @Override protected void onContentsChanged(int slot, ItemStack previous) { setChanged(); }
        };
    }
    public EnergyHandler energyInput() { return energyInput; }
    public SideMode sideMode(Direction side) { return sides[side.ordinal()]; }
    public void cycleSide(Direction side) {
        sides[side.ordinal()] = sideMode(side).next();
        // Existing cached capability objects also consult the current mode on each operation.
        if (level != null) level.invalidateCapabilities(worldPosition);
        setChanged();
    }
    public int speed() { return upgrades.getAmountAsInt(0); }
    public int patternUpgrades() { return upgrades.getAmountAsInt(1); }
    public int transferTier(int slot) {
        return upgrades.getResource(slot).toStack().getItem() instanceof UpgradeItem u ? u.tier : 0;
    }
    public int capacity() { return MachineBalance.patternCapacity(tier, patternUpgrades()); }
    public int craftTicks() { return MachineBalance.craftTicks(tier, speed()); }
    public int workFe() {
        return MachineBalance.workFe(tier == CrafterTier.REGULAR ? CraftwrightConfig.REGULAR_FE.get() : CraftwrightConfig.ADVANCED_FE.get(), speed(), patternUpgrades(), transferTier(2), transferTier(3));
    }
    public int progress() { return progress; }
    public ItemStack patternStack(int pattern, int cell) {
        int slot = pattern * 10 + cell;
        return patterns.getResource(slot).toStack(patterns.getAmountAsInt(slot));
    }
    public List<ItemStack> patternGrid(int pattern) {
        List<ItemStack> grid = new ArrayList<>(9);
        for (int i = 0; i < 9; i++) grid.add(patternStack(pattern, i));
        return grid;
    }
    public void setPattern(int pattern, List<ItemStack> grid, ItemStack output) {
        for (int i = 0; i < 9; i++) setStack(patterns, pattern * 10 + i, grid.get(i).copyWithCount(1));
        setStack(patterns, pattern * 10 + 9, output.copy());
        recipeChecked[pattern] = false;
        patternProgress[pattern] = 0;
        if (activePattern == pattern) { activePattern = -1; progress = 0; }
        setChanged();
    }
    public static void setStack(ItemStacksResourceHandler handler, int slot, ItemStack stack) {
        handler.set(slot, ItemResource.of(stack), stack.isEmpty() ? 0 : stack.getCount());
    }
    public ResourceHandler<ItemResource> itemsForSide(Direction side) {
        // An unsided request still cannot bypass input/output direction restrictions.
        return new ResourceHandler<>() {
            @Override public int size() { return INPUTS + OUTPUTS; }
            private boolean input(int i) { return i < INPUTS; }
            private int index(int i) { Objects.checkIndex(i, size()); return input(i) ? i : i - INPUTS; }
            private ResourceHandler<ItemResource> handler(int i) { return input(i) ? inputs : outputs; }
            private SideMode mode() { return side == null ? SideMode.BOTH : sideMode(side); }
            @Override public ItemResource getResource(int i) { return handler(i).getResource(index(i)); }
            @Override public long getAmountAsLong(int i) { return handler(i).getAmountAsLong(index(i)); }
            @Override public long getCapacityAsLong(int i, ItemResource r) { return isValid(i, r) ? handler(i).getCapacityAsLong(index(i), r) : 0; }
            @Override public boolean isValid(int i, ItemResource r) { index(i); return input(i) && mode().input && inputs.isValid(i, r); }
            @Override public int insert(int i, ItemResource r, int amount, TransactionContext tx) { return isValid(i, r) ? inputs.insert(i, r, amount, tx) : 0; }
            @Override public int extract(int i, ItemResource r, int amount, TransactionContext tx) { index(i); return !input(i) && mode().output ? outputs.extract(i - INPUTS, r, amount, tx) : 0; }
        };
    }
    /** A manually edited expected output must be produced by a real recipe for the exact grid. */
    public static CraftingRecipe findRecipe(ServerLevel level, List<ItemStack> grid, ItemStack expected) {
        CraftingInput input = CraftingInput.of(3, 3, grid);
        return level.recipeAccess().recipeMap().getRecipesFor(RecipeType.CRAFTING, input, level)
            .map(RecipeHolder::value)
            .filter(r -> expected.isEmpty() || ItemStack.matches(r.assemble(input), expected))
            .findFirst().orElse(null);
    }
    private CraftingRecipe recipe(ServerLevel level, int pattern) {
        RecipeMap map = level.recipeAccess().recipeMap();
        if (map != cachedMap) { cachedMap = map; Arrays.fill(recipeChecked, false); }
        if (!recipeChecked[pattern]) {
            recipeCache[pattern] = findRecipe(level, patternGrid(pattern), patternStack(pattern, 9));
            recipeChecked[pattern] = true;
        }
        return recipeCache[pattern];
    }
    public static void tick(Level level, BlockPos pos, BlockState state, CrafterBlockEntity be) {
        if (!(level instanceof ServerLevel server)) return;
        if (!be.enabled) { be.status = 0; return; }
        if (level.getGameTime() % CraftwrightConfig.TRANSFER_INTERVAL.get() == 0) be.transfer(server);
        int capacity = be.capacity();
        be.status = 0;
        for (int n = 0; n < capacity; n++) {
            int p = (be.nextPattern + n) % capacity;
            if (be.patternStack(p, 9).isEmpty()) continue;
            CraftingRecipe recipe = be.recipe(server, p);
            if (recipe == null) { be.status = Math.max(be.status, 5); continue; }
            int result = be.attemptCraft(server, p, recipe, false);
            if (result != 4) { be.status = Math.max(be.status, result); continue; }
            if (be.activePattern != p) { be.progress = be.patternProgress[p]; be.activePattern = p; }
            int cost = be.workFe();
            if (be.energy.getAmountAsInt() < cost) { be.status = 3; return; }
            if (be.progress + 1 >= be.craftTicks()) {
                if (be.attemptCraft(server, p, recipe, true) == 4) {
                    be.progress = 0;
                    be.patternProgress[p] = 0;
                    be.nextPattern = (p + 1) % capacity;
                    be.activePattern = -1;
                }
            } else {
                try (Transaction tx = Transaction.openRoot()) {
                    be.energy.extract(cost, tx);
                    tx.commit();
                }
                be.patternProgress[p] = ++be.progress;
            }
            be.status = 4;
            be.setChanged();
            return;
        }
        // Keep partially completed patterns when ingredients or output space are temporarily unavailable.
    }
    /** Inputs, all outputs, recipe remainders, and final-tick energy commit together or roll back. */
    private int attemptCraft(ServerLevel level, int p, CraftingRecipe recipe, boolean commit) {
        List<ItemStack> actual = new ArrayList<>(9);
        try (Transaction tx = Transaction.openRoot()) {
            for (ItemStack ghost : patternGrid(p)) {
                if (ghost.isEmpty()) { actual.add(ItemStack.EMPTY); continue; }
                ItemResource resource = ItemResource.of(ghost);
                if (inputs.extract(resource, 1, tx) != 1) return 1;
                actual.add(ghost.copyWithCount(1));
            }
            CraftingInput input = CraftingInput.of(3, 3, actual);
            if (!recipe.matches(input, level)) return 5;
            ItemStack result = recipe.assemble(input);
            if (result.isEmpty() || !ItemStack.matches(result, patternStack(p, 9))) return 5;
            if (!insertOutput(result, tx)) return 2;
            for (ItemStack remainder : recipe.getRemainingItems(input)) if (!insertOutput(remainder, tx)) return 2;
            if (commit) {
                int cost = workFe();
                if (energy.extract(cost, tx) != cost) return 3;
                tx.commit();
            }
            return 4;
        }
    }
    private boolean insertOutput(ItemStack stack, TransactionContext tx) {
        return stack.isEmpty() || ResourceHandlerUtil.insertStacking(outputs, ItemResource.of(stack), stack.getCount(), tx) == stack.getCount();
    }
    private boolean wanted(ItemResource resource) {
        for (int p = 0; p < capacity(); p++) {
            if (patternStack(p, 9).isEmpty()) continue;
            for (int cell = 0; cell < 9; cell++) if (ItemResource.of(patternStack(p, cell)).equals(resource)) return true;
        }
        return false;
    }
    void transfer(ServerLevel level) {
        int pull = transferTier(2), push = transferTier(3);
        int pullLeft = MachineBalance.transferAmount(pull), pushLeft = MachineBalance.transferAmount(push);
        if (pullLeft + pushLeft == 0) return;
        for (int n = 0; n < 6; n++) {
            Direction face = Direction.values()[(transferFace + n) % 6];
            if (sideMode(face) == SideMode.DISABLED) continue;
            BlockPos target = worldPosition.relative(face);
            if (!level.hasChunkAt(target)) continue;
            ResourceHandler<ItemResource> neighbor = level.getCapability(Capabilities.Item.BLOCK, target, face.getOpposite());
            if (neighbor == null) continue;
            if (sideMode(face).input && pullLeft > 0) pullLeft -= movePowered(neighbor, inputs, pullLeft, pull, true);
            if (sideMode(face).output && pushLeft > 0) pushLeft -= movePowered(outputs, neighbor, pushLeft, push, false);
        }
        transferFace = (transferFace + 1) % 6;
    }
    private int movePowered(ResourceHandler<ItemResource> from, ResourceHandler<ItemResource> to, int budget, int tier, boolean filter) {
        int cost = CraftwrightConfig.TRANSFER_FE.get() * tier;
        int affordable = Math.min(budget, energy.getAmountAsInt() / cost);
        if (affordable == 0) return 0;
        try (Transaction tx = Transaction.openRoot()) {
            int moved = ResourceHandlerUtil.moveStacking(from, to, r -> !filter || wanted(r), affordable, tx);
            if (moved > 0 && energy.extract(moved * cost, tx) == moved * cost) { tx.commit(); return moved; }
            return 0;
        }
    }
    @Override public void preRemoveSideEffects(BlockPos pos, BlockState state) {
        if (level instanceof ServerLevel) {
            for (ItemStacksResourceHandler handler : List.of(inputs, outputs, upgrades)) {
                for (int i = 0; i < handler.size(); i++) {
                    Containers.dropItemStack(level, pos.getX(), pos.getY(), pos.getZ(), handler.getResource(i).toStack(handler.getAmountAsInt(i)));
                    handler.set(i, ItemResource.EMPTY, 0);
                }
            }
        }
        super.preRemoveSideEffects(pos, state);
    }
    @Override protected void saveAdditional(ValueOutput out) {
        super.saveAdditional(out);
        inputs.serialize(out.child("inputs")); outputs.serialize(out.child("outputs"));
        upgrades.serialize(out.child("upgrades")); patterns.serialize(out.child("patterns"));
        energy.serialize(out.child("energy"));
        for (Direction side : Direction.values()) out.putInt("side_" + side.getName(), sideMode(side).ordinal());
        out.putInt("progress", progress); out.putInt("nextPattern", nextPattern); out.putInt("activePattern", activePattern);
        out.putBoolean("enabled", enabled);
        for (int p = 0; p < MAX_PATTERNS; p++) out.putInt("patternProgress_" + p, patternProgress[p]);
    }
    @Override protected void loadAdditional(ValueInput in) {
        super.loadAdditional(in);
        inputs.deserialize(in.childOrEmpty("inputs")); outputs.deserialize(in.childOrEmpty("outputs"));
        upgrades.deserialize(in.childOrEmpty("upgrades")); patterns.deserialize(in.childOrEmpty("patterns"));
        energy.deserialize(in.childOrEmpty("energy"));
        energy.set(Math.min(energy.getAmountAsInt(), tier.energyCapacity));
        for (Direction side : Direction.values()) sides[side.ordinal()] = SideMode.values()[Math.clamp(in.getIntOr("side_" + side.getName(), sides[side.ordinal()].ordinal()), 0, 3)];
        progress = Math.clamp(in.getIntOr("progress", 0), 0, 39);
        nextPattern = Math.floorMod(in.getIntOr("nextPattern", 0), tier.maxPatterns);
        activePattern = Math.clamp(in.getIntOr("activePattern", -1), -1, tier.maxPatterns - 1);
        enabled = in.getBooleanOr("enabled", true);
        for (int p = 0; p < MAX_PATTERNS; p++) patternProgress[p] = Math.clamp(in.getIntOr("patternProgress_" + p, 0), 0, tier.baseTicks - 1);
        Arrays.fill(recipeChecked, false);
    }
    @Override public Component getDisplayName() { return Component.translatable(tier == CrafterTier.REGULAR ? "block.craftwright.crafter" : "block.craftwright.advanced_crafter"); }
    @Override public AbstractContainerMenu createMenu(int id, Inventory inventory, Player player) { return new CrafterMenu(id, inventory, this); }
}
