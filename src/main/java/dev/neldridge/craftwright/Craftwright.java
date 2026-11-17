package dev.neldridge.craftwright;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.neoforged.neoforge.common.extensions.IMenuTypeExtension;
import net.neoforged.neoforge.registries.*;

@Mod(Craftwright.MOD_ID)
public final class Craftwright {
    public static final String MOD_ID = "craftwright";
    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(MOD_ID);
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(MOD_ID);
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES = DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, MOD_ID);
    public static final DeferredRegister<MenuType<?>> MENUS = DeferredRegister.create(Registries.MENU, MOD_ID);
    public static final DeferredRegister<CreativeModeTab> TABS = DeferredRegister.create(Registries.CREATIVE_MODE_TAB, MOD_ID);
    public static final DeferredBlock<CrafterBlock> CRAFTER = BLOCKS.registerBlock("crafter", p -> new CrafterBlock(p, CrafterTier.REGULAR), p -> p.strength(3.5f).requiresCorrectToolForDrops());
    public static final DeferredBlock<CrafterBlock> ADVANCED_CRAFTER = BLOCKS.registerBlock("advanced_crafter", p -> new CrafterBlock(p, CrafterTier.ADVANCED), p -> p.strength(5f).requiresCorrectToolForDrops());
    public static final DeferredItem<?> CRAFTER_ITEM = ITEMS.registerSimpleBlockItem(CRAFTER);
    public static final DeferredItem<?> ADVANCED_ITEM = ITEMS.registerSimpleBlockItem(ADVANCED_CRAFTER);
    public static final DeferredItem<UpgradeItem> SPEED = upgrade("speed_upgrade", UpgradeItem.Kind.SPEED, 1);
    public static final DeferredItem<UpgradeItem> PATTERN = upgrade("pattern_upgrade", UpgradeItem.Kind.PATTERN, 1);
    public static final List<DeferredItem<UpgradeItem>> PULL = transferUpgrades("pull", UpgradeItem.Kind.PULL);
    public static final List<DeferredItem<UpgradeItem>> PUSH = transferUpgrades("push", UpgradeItem.Kind.PUSH);
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<CrafterBlockEntity>> CRAFTER_BE = BLOCK_ENTITIES.register("crafter", () -> new BlockEntityType<>(CrafterBlockEntity::new, CRAFTER.get(), ADVANCED_CRAFTER.get()));
    public static final DeferredHolder<MenuType<?>, MenuType<CrafterMenu>> CRAFTER_MENU = MENUS.register("crafter", () -> IMenuTypeExtension.create(CrafterMenu::new));
    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> TAB = TABS.register("craftwright", () -> CreativeModeTab.builder()
        .title(Component.translatable("itemGroup.craftwright")).icon(() -> CRAFTER_ITEM.get().getDefaultInstance())
        .displayItems((parameters, output) -> ITEMS.getEntries().forEach(i -> output.accept(i.get()))).build());
    private static DeferredItem<UpgradeItem> upgrade(String id, UpgradeItem.Kind kind, int tier) {
        return ITEMS.registerItem(id, p -> new UpgradeItem(p, kind, tier));
    }
    private static List<DeferredItem<UpgradeItem>> transferUpgrades(String prefix, UpgradeItem.Kind kind) {
        var items = new ArrayList<DeferredItem<UpgradeItem>>();
        for (int tier = 1; tier <= 4; tier++) items.add(upgrade(prefix + "_upgrade_" + tier, kind, tier));
        return List.copyOf(items);
    }
    public Craftwright(IEventBus bus, ModContainer container) {
        BLOCKS.register(bus); ITEMS.register(bus); BLOCK_ENTITIES.register(bus); MENUS.register(bus); TABS.register(bus);
        container.registerConfig(ModConfig.Type.SERVER, CraftwrightConfig.SPEC);
        bus.addListener(this::capabilities);
    }
    private void capabilities(RegisterCapabilitiesEvent event) {
        event.registerBlockEntity(Capabilities.Item.BLOCK, CRAFTER_BE.get(), (be, side) -> be.itemsForSide(side));
        event.registerBlockEntity(Capabilities.Energy.BLOCK, CRAFTER_BE.get(), (be, side) -> be.energyInput());
    }
}
