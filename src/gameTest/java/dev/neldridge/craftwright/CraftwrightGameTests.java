package dev.neldridge.craftwright;

import java.util.*;
import java.util.function.Consumer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.*;
import net.minecraft.resources.Identifier;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.event.RegisterGameTestsEvent;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.item.*;
import net.neoforged.neoforge.transfer.transaction.Transaction;

/** Server integration tests; this source set is never packaged in the release JAR. */
@EventBusSubscriber(modid = Craftwright.MOD_ID)
public final class CraftwrightGameTests {
    private static Identifier id(String path) { return Identifier.fromNamespaceAndPath(Craftwright.MOD_ID, path); }
    @SubscribeEvent public static void register(RegisterGameTestsEvent event) {
        var environment = event.registerEnvironment(id("tests"));
        Map<String, Consumer<GameTestHelper>> tests = new LinkedHashMap<>();
        tests.put("regular_crafts_after_40_ticks", h -> {
            var be = machine(h, false); logPattern(be, 0); put(be.inputs, 0, Items.OAK_LOG, 2); be.energy.set(1000);
            tick(h, be, 39);
            check(h, count(be.outputs, Items.OAK_PLANKS) == 0, "Must wait the full 40 ticks");
            tick(h, be, 1);
            check(h, count(be.outputs, Items.OAK_PLANKS) == 4 && count(be.inputs, Items.OAK_LOG) == 1, "Exactly one log becomes four planks");
            check(h, be.energy.getAmountAsInt() == 840, "40 ticks at 4 FE/t costs 160 FE"); h.succeed();
        });
        tests.put("advanced_and_speed_timing", h -> {
            var be = machine(h, true); logPattern(be, 0); put(be.inputs, 0, Items.OAK_LOG, 3); be.energy.set(30000);
            tick(h, be, 9); check(h, count(be.outputs, Items.OAK_PLANKS) == 0, "Advanced waits ten ticks");
            tick(h, be, 1); check(h, count(be.outputs, Items.OAK_PLANKS) == 4, "Advanced crafts on tick ten");
            CrafterBlockEntity.setStack(be.upgrades, 0, new ItemStack(Craftwright.SPEED.get(), 8));
            tick(h, be, 1); check(h, count(be.outputs, Items.OAK_PLANKS) == 8, "Maximum speed crafts once per tick");
            check(h, be.workFe() > 16, "Speed consumes more FE"); h.succeed();
        });
        tests.put("blocked_output_rolls_back", h -> {
            var be = machine(h, false); logPattern(be, 0); put(be.inputs, 0, Items.OAK_LOG, 1); be.energy.set(1000);
            for (int i = 0; i < 9; i++) put(be.outputs, i, Items.COBBLESTONE, 64);
            tick(h, be, 80);
            check(h, count(be.inputs, Items.OAK_LOG) == 1 && be.energy.getAmountAsInt() == 1000, "A blocked machine consumes no ingredients or power");
            CrafterBlockEntity.setStack(be.outputs, 0, ItemStack.EMPTY); tick(h, be, 40);
            check(h, count(be.outputs, Items.OAK_PLANKS) == 4, "Crafting resumes after output space is freed"); h.succeed();
        });
        tests.put("midcraft_output_blockage_preserves_progress", h -> {
            var be = machine(h, false); logPattern(be, 0); put(be.inputs, 0, Items.OAK_LOG, 1); be.energy.set(1000);
            tick(h, be, 20);
            for (int i = 0; i < 9; i++) put(be.outputs, i, Items.COBBLESTONE, 64);
            tick(h, be, 40);
            check(h, be.progress() == 20 && be.energy.getAmountAsInt() == 920, "Output blockage pauses progress and FE use");
            CrafterBlockEntity.setStack(be.outputs, 0, ItemStack.EMPTY); tick(h, be, 20);
            check(h, count(be.outputs, Items.OAK_PLANKS) == 4 && be.energy.getAmountAsInt() == 840, "Craft resumes without repaying completed work"); h.succeed();
        });
        tests.put("no_power_preserves_inputs", h -> {
            var be = machine(h, false); logPattern(be, 0); put(be.inputs, 0, Items.OAK_LOG, 1); tick(h, be, 80);
            check(h, count(be.inputs, Items.OAK_LOG) == 1 && count(be.outputs, Items.OAK_PLANKS) == 0, "No FE means no craft"); h.succeed();
        });
        tests.put("container_remainders_are_atomic", h -> {
            var be = machine(h, false);
            List<ItemStack> grid = List.of(new ItemStack(Items.MILK_BUCKET),new ItemStack(Items.MILK_BUCKET),new ItemStack(Items.MILK_BUCKET),new ItemStack(Items.SUGAR),new ItemStack(Items.EGG),new ItemStack(Items.SUGAR),new ItemStack(Items.WHEAT),new ItemStack(Items.WHEAT),new ItemStack(Items.WHEAT));
            be.setPattern(0, grid, new ItemStack(Items.CAKE));
            for (int i = 0; i < grid.size(); i++) CrafterBlockEntity.setStack(be.inputs, i, grid.get(i));
            be.energy.set(1000);
            for (int i = 0; i < 8; i++) put(be.outputs, i, Items.COBBLESTONE, 64);
            tick(h, be, 40);
            check(h, count(be.inputs, Items.MILK_BUCKET) == 3 && count(be.outputs, Items.CAKE) == 0, "Cake and buckets must all fit before ingredients are consumed");
            CrafterBlockEntity.setStack(be.outputs, 7, ItemStack.EMPTY); tick(h, be, 40);
            check(h, count(be.outputs, Items.CAKE) == 1 && count(be.outputs, Items.BUCKET) == 3, "All three buckets are returned"); h.succeed();
        });
        tests.put("rejects_forged_output", h -> {
            var be = machine(h, false); logPattern(be, 0);
            be.setPattern(0, be.patternGrid(0), new ItemStack(Items.DIAMOND, 64));
            put(be.inputs, 0, Items.OAK_LOG, 1); be.energy.set(1000); tick(h, be, 40);
            check(h, count(be.outputs, Items.DIAMOND) == 0 && count(be.inputs, Items.OAK_LOG) == 1, "Edited outputs cannot create arbitrary items"); h.succeed();
        });
        tests.put("sides_and_cached_handlers", h -> {
            var be = machine(h, false); var north = be.itemsForSide(Direction.NORTH);
            try (var tx = Transaction.openRoot()) { check(h, north.insert(0, ItemResource.of(Items.OAK_LOG), 1, tx) == 1, "Input face accepts input"); tx.commit(); }
            be.cycleSide(Direction.NORTH); // output only
            put(be.outputs, 0, Items.OAK_PLANKS, 4);
            try (var tx = Transaction.openRoot()) {
                check(h, north.insert(0, ItemResource.of(Items.OAK_LOG), 1, tx) == 0, "Cached handler respects changed side mode");
                check(h, north.extract(0, ItemResource.of(Items.OAK_LOG), 1, tx) == 0, "Ingredients are never exposed for extraction");
                check(h, north.extract(18, ItemResource.of(Items.OAK_PLANKS), 1, tx) == 1, "Output face permits extraction");
            }
            be.cycleSide(Direction.NORTH); // both
            try (var tx = Transaction.openRoot()) {
                check(h, north.insert(0, ItemResource.of(Items.OAK_LOG), 1, tx) == 1 && north.extract(18, ItemResource.of(Items.OAK_PLANKS), 1, tx) == 1, "Both mode supports input and output");
            }
            be.cycleSide(Direction.NORTH); // disabled
            try (var tx = Transaction.openRoot()) {
                check(h, north.insert(0, ItemResource.of(Items.OAK_LOG), 1, tx) == 0 && north.extract(18, ItemResource.of(Items.OAK_PLANKS), 1, tx) == 0, "Disabled blocks both directions");
                check(h, be.energyInput().extract(100, tx) == 0, "External devices cannot drain stored energy");
            }
            h.succeed();
        });
        tests.put("patterns_rotate_fairly_and_lock", h -> {
            var be = machine(h, false); logPattern(be, 0);
            var sticks = empty(); sticks.set(0, new ItemStack(Items.OAK_PLANKS)); sticks.set(3, new ItemStack(Items.OAK_PLANKS));
            be.setPattern(1, sticks, new ItemStack(Items.STICK, 4));
            put(be.inputs, 0, Items.OAK_LOG, 8); put(be.inputs, 1, Items.OAK_PLANKS, 8); be.energy.set(10000);
            tick(h, be, 80); check(h, count(be.outputs, Items.STICK) == 0, "Locked patterns do not craft");
            CrafterBlockEntity.setStack(be.upgrades, 1, new ItemStack(Craftwright.PATTERN.get(), 2));
            tick(h, be, 80); check(h, count(be.outputs, Items.STICK) == 4, "Available patterns share the crafting scheduler");
            check(h, be.capacity() == 3, "Regular machine caps at three patterns"); h.succeed();
        });
        tests.put("save_reload_preserves_machine", h -> {
            var be = machine(h, true); logPattern(be, 0); put(be.inputs, 0, Items.OAK_LOG, 5); be.energy.set(123456);
            CrafterBlockEntity.setStack(be.upgrades, 1, new ItemStack(Craftwright.PATTERN.get(), 6)); be.cycleSide(Direction.WEST);
            var saved = be.saveWithFullMetadata(h.getLevel().registryAccess());
            var loaded = (CrafterBlockEntity) BlockEntity.loadStatic(be.getBlockPos(), be.getBlockState(), saved, h.getLevel().registryAccess());
            check(h, loaded != null && loaded.capacity() == 12 && loaded.energy.getAmountAsInt() == 123456, "Energy and upgrades survive reload");
            check(h, count(loaded.inputs, Items.OAK_LOG) == 5 && loaded.patternStack(0, 9).is(Items.OAK_PLANKS) && loaded.sideMode(Direction.WEST) == SideMode.OUTPUT, "Inputs, patterns, and side modes survive reload"); h.succeed();
        });
        tests.put("ghost_editor_is_not_real_inventory", h -> {
            var be = machine(h, false); logPattern(be, 0);
            var player = h.makeMockServerPlayerInLevel(); player.setPos(be.getBlockPos().getX()+0.5,be.getBlockPos().getY()+0.5,be.getBlockPos().getZ()+0.5);
            var menu = new CrafterMenu(1, player.getInventory(), be);
            menu.setCarried(new ItemStack(Items.DIAMOND, 5));
            menu.clicked(CrafterMenu.DRAFT_START, 0, ContainerInput.PICKUP, player);
            check(h, menu.getCarried().getCount() == 5, "Ghost editing never consumes the cursor stack");
            check(h, !menu.clickMenuButton(player, CrafterMenu.SAVE), "Invalid recipe cannot be saved");
            check(h, menu.quickMoveStack(player, CrafterMenu.DRAFT_START).isEmpty(), "Ghost items cannot be shift-clicked into real inventory"); h.succeed();
        });
        tests.put("powered_transfers_share_budget_and_filter", h -> {
            var be = machine(h, false); logPattern(be, 0);
            var above = new BlockPos(2, 2, 2); var north = new BlockPos(2, 1, 1); var below = new BlockPos(2, 0, 2);
            h.setBlock(above, Blocks.CHEST); h.setBlock(north, Blocks.CHEST); h.setBlock(below, Blocks.CHEST);
            var a = h.getBlockEntity(above, net.minecraft.world.level.block.entity.ChestBlockEntity.class);
            var n = h.getBlockEntity(north, net.minecraft.world.level.block.entity.ChestBlockEntity.class);
            var b = h.getBlockEntity(below, net.minecraft.world.level.block.entity.ChestBlockEntity.class);
            a.setItem(0, new ItemStack(Items.OAK_LOG, 20)); n.setItem(0, new ItemStack(Items.OAK_LOG, 20));
            a.setItem(1, new ItemStack(Items.COBBLESTONE, 64));
            CrafterBlockEntity.setStack(be.upgrades, 2, new ItemStack(Craftwright.PULL.get(1).get()));
            CrafterBlockEntity.setStack(be.upgrades, 3, new ItemStack(Craftwright.PUSH.getFirst().get()));
            put(be.outputs, 0, Items.OAK_PLANKS, 4); be.energy.set(1000);
            be.transfer(h.getLevel());
            check(h, count(be.inputs, Items.OAK_LOG) == 8 && count(be.inputs, Items.COBBLESTONE) == 0, "Pull tier 2 moves eight wanted items total across all faces");
            check(h, count(be.outputs, Items.OAK_PLANKS) == 3 && b.getItem(0).getCount() == 1, "Push tier 1 moves one output to the vanilla chest");
            check(h, be.energy.getAmountAsInt() == 966, "Both operations pay energy only for moved items");
            be.energy.set(0); be.transfer(h.getLevel());
            check(h, count(be.inputs, Items.OAK_LOG) == 8 && count(be.outputs, Items.OAK_PLANKS) == 3, "Transfers stop without power");
            h.succeed();
        });
        tests.put("upgrade_balance_bounds", h -> {
            for (var tier : CrafterTier.values()) {
                check(h, MachineBalance.craftTicks(tier, 0) == tier.baseTicks && MachineBalance.craftTicks(tier, 8) == 1, "Speed range has the intended endpoints");
                for (int n = 1; n <= 8; n++) check(h, MachineBalance.craftTicks(tier, n) < MachineBalance.craftTicks(tier, n-1), "Every speed upgrade improves the interval");
            }
            check(h, MachineBalance.patternCapacity(CrafterTier.ADVANCED, 64) == 12, "Advanced caps at twelve");
            check(h, MachineBalance.transferAmount(1) == 1 && MachineBalance.transferAmount(4) == 64, "Transfer tiers have expected limits"); h.succeed();
        });
        tests.forEach((name, action) -> event.registerTest(id(name), new FunctionGameTestInstance(BuiltinTestFunctions.ALWAYS_PASS, new TestData<>(environment, id("empty"), 200, 0, true)) {
            @Override public void run(GameTestHelper helper) { action.accept(helper); }
        }));
    }
    private static CrafterBlockEntity machine(GameTestHelper h, boolean advanced) {
        var pos = new BlockPos(2, 1, 2);
        h.setBlock(pos, advanced ? Craftwright.ADVANCED_CRAFTER.get() : Craftwright.CRAFTER.get());
        return h.getBlockEntity(pos, CrafterBlockEntity.class);
    }
    private static void tick(GameTestHelper h, CrafterBlockEntity be, int ticks) {
        for (int i = 0; i < ticks; i++) CrafterBlockEntity.tick(h.getLevel(), be.getBlockPos(), be.getBlockState(), be);
    }
    private static List<ItemStack> empty() { return new ArrayList<>(Collections.nCopies(9, ItemStack.EMPTY)); }
    private static void logPattern(CrafterBlockEntity be, int index) {
        var grid = empty(); grid.set(0, new ItemStack(Items.OAK_LOG)); be.setPattern(index, grid, new ItemStack(Items.OAK_PLANKS, 4));
    }
    private static void put(ItemStacksResourceHandler handler, int index, Item item, int count) { CrafterBlockEntity.setStack(handler, index, new ItemStack(item, count)); }
    private static int count(ResourceHandler<ItemResource> handler, Item item) {
        int count = 0;
        for (int i = 0; i < handler.size(); i++) if (handler.getResource(i).is(item)) count += handler.getAmountAsInt(i);
        return count;
    }
    private static void check(GameTestHelper h, boolean condition, String message) { h.assertTrue(condition, message); }
}
