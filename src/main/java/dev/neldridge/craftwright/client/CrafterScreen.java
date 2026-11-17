package dev.neldridge.craftwright.client;

import dev.neldridge.craftwright.*;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

public class CrafterScreen extends AbstractContainerScreen<CrafterMenu> {
    private static final int INK = 0xffdedfd8, MUTED = 0xffa0ada9, ACCENT = 0xffd4aa62;
    private Button save, toggle, add, delete, auto;
    private final Button[] sides = new Button[6];
    public CrafterScreen(CrafterMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title, 330, 238);
        inventoryLabelX = 12; inventoryLabelY = 144;
        titleLabelX = 12; titleLabelY = 9;
    }
    private Component text(String key) { return Component.translatable("gui.craftwright." + key); }
    private void send(int id) { if (minecraft != null && minecraft.gameMode != null) minecraft.gameMode.handleInventoryButtonClick(menu.containerId, id); }
    private Button button(int x, int y, int w, String key, int id) {
        return addRenderableWidget(Button.builder(text(key), b -> send(id)).bounds(leftPos + x, topPos + y, w, 18).build());
    }
    @Override protected void init() {
        super.init();
        String[] tabs = {"tab_patterns", "tab_inventory", "tab_sides"};
        for (int i = 0; i < tabs.length; i++) {
            int view = i;
            addRenderableWidget(Button.builder(text(tabs[i]), b -> { menu.view = view; updateButtons(); }).bounds(leftPos + 12 + i * 100, topPos + 28, 98, 18).build());
        }
        add = button(12, 116, 72, "add", CrafterMenu.ADD);
        save = button(106, 116, 48, "save", CrafterMenu.SAVE);
        delete = button(158, 116, 48, "delete", CrafterMenu.DELETE);
        auto = button(178, 99, 45, "auto", CrafterMenu.AUTO_OUTPUT);
        auto.setTooltip(Tooltip.create(text("auto_tooltip")));
        toggle = button(238, 6, 80, "enabled", CrafterMenu.TOGGLE);
        for (Direction side : Direction.values()) {
            int n = side.ordinal();
            sides[n] = button(12 + n % 3 * 102, 62 + n / 3 * 32, 98, "side", 30 + n);
        }
        updateButtons();
    }
    private void updateButtons() {
        save.visible = add.visible = delete.visible = auto.visible = menu.view == 0;
        save.active = menu.data.get(7) == 1 && menu.selected() < menu.capacity();
        toggle.setMessage(text(menu.data.get(9) == 1 ? "enabled" : "paused"));
        for (Direction side : Direction.values()) {
            int mode = menu.data.get(10 + side.ordinal());
            sides[side.ordinal()].visible = menu.view == 2;
            sides[side.ordinal()].setMessage(Component.translatable("gui.craftwright.side_label", Component.translatable("gui.craftwright.direction." + side.getName()), Component.translatable("gui.craftwright.mode." + mode)));
            sides[side.ordinal()].setTooltip(Tooltip.create(Component.translatable("gui.craftwright.mode_tooltip." + mode)));
        }
    }
    @Override protected void containerTick() { super.containerTick(); updateButtons(); }
    @Override public void extractBackground(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
        int x = leftPos, y = topPos;
        g.fill(x, y, x + imageWidth, y + imageHeight, 0xff242f31);
        g.outline(x, y, imageWidth, imageHeight, 0xff64716b);
        g.fill(x + 1, y + 1, x + imageWidth - 1, y + 24, 0xff33423f);
        for (Slot slot : menu.slots) {
            if (!slot.isActive()) continue;
            int sx = x + slot.x - 1, sy = y + slot.y - 1;
            g.fill(sx, sy, sx + 18, sy + 18, 0xff101b1e);
            g.outline(sx, sy, 18, 18, 0xff53635f);
            if (slot.index >= CrafterMenu.PATTERN_START && slot.index < CrafterMenu.DRAFT_START) {
                int p = slot.index - CrafterMenu.PATTERN_START;
                if (p == menu.selected()) g.outline(sx - 1, sy - 1, 20, 20, ACCENT);
                if (p >= menu.capacity()) g.fill(sx, sy, sx + 18, sy + 18, 0xaa64716b);
            }
        }
        if (menu.view == 0) g.text(font, ">", x + 168, y + 80, ACCENT, false);
        int energyWidth = (int) Math.clamp((long) menu.energy() * 130 / menu.tier.energyCapacity, 0, 130);
        g.fill(x + 186, y + 154, x + 316, y + 158, 0xff101b1e);
        g.fill(x + 186, y + 154, x + 186 + energyWidth, y + 158, ACCENT);
        int duration = Math.max(1, menu.data.get(3));
        int progress = menu.data.get(2) * 130 / duration;
        g.fill(x + 186, y + 222, x + 316, y + 225, 0xff101b1e);
        g.fill(x + 186, y + 222, x + 186 + progress, y + 225, 0xff82b797);
    }
    @Override protected void extractLabels(GuiGraphicsExtractor g, int mouseX, int mouseY) {
        g.text(font, title, titleLabelX, titleLabelY, INK, false);
        if (menu.view == 0) {
            g.text(font, text("patterns"), 12, 49, MUTED, false);
            g.text(font, Component.translatable("gui.craftwright.pattern_number", menu.selected() + 1, menu.capacity()), 106, 49, ACCENT, false);
            g.text(font, text("ghost_hint"), 228, 58, MUTED, false);
            g.text(font, text("clear_hint"), 228, 70, MUTED, false);
            g.text(font, text(menu.data.get(7) == 1 ? "valid_short" : "invalid_short"), 228, 94, menu.data.get(7) == 1 ? 0xff82b797 : MUTED, false);
        } else if (menu.view == 1) {
            g.text(font, text("inputs"), 12, 51, MUTED, false);
            g.text(font, text("outputs"), 186, 51, MUTED, false);
            g.text(font, text("upgrades"), 250, 51, MUTED, false);
            g.text(font, text("speed_short"), 250, 80, MUTED, false);
            g.text(font, text("patterns_short"), 278, 80, MUTED, false);
            g.text(font, text("pull_short"), 250, 108, MUTED, false);
            g.text(font, text("push_short"), 278, 108, MUTED, false);
        } else {
            g.text(font, text("side_hint"), 12, 118, MUTED, false);
        }
        g.text(font, playerInventoryTitle, inventoryLabelX, inventoryLabelY, MUTED, false);
        g.text(font, Component.translatable("gui.craftwright.status." + (menu.data.get(9) == 0 ? "paused" : menu.data.get(5))), 186, 144, INK, false);
        g.text(font, Component.translatable("gui.craftwright.power_stored", menu.energy()), 186, 164, MUTED, false);
        g.text(font, Component.translatable("gui.craftwright.power_rate", menu.workFe()), 186, 176, MUTED, false);
        g.text(font, Component.translatable("gui.craftwright.speed", menu.data.get(3)), 186, 190, MUTED, false);
        g.text(font, Component.translatable("gui.craftwright.capacity", menu.capacity(), menu.tier.maxPatterns), 186, 204, MUTED, false);
    }
    @Override protected void extractTooltip(GuiGraphicsExtractor g, int mouseX, int mouseY) {
        super.extractTooltip(g, mouseX, mouseY);
        if (menu.view == 0 && isHovering(186, 76, 16, 16, mouseX, mouseY)) {
            var lines = new java.util.ArrayList<Component>();
            lines.add(text(menu.data.get(7) == 1 ? "valid" : "invalid"));
            for (int i = 0; i < 9; i++) {
                ItemStack stack = menu.remainder(i);
                if (!stack.isEmpty()) lines.add(Component.translatable("gui.craftwright.returned", stack.getCount(), stack.getHoverName()));
            }
            g.setComponentTooltipForNextFrame(font, lines, mouseX, mouseY);
        }
    }
}
