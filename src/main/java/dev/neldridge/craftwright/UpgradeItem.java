package dev.neldridge.craftwright;

import net.minecraft.world.item.Item;

public class UpgradeItem extends Item {
    public enum Kind { SPEED, PATTERN, PULL, PUSH }
    public final Kind kind;
    public final int tier;
    public UpgradeItem(Properties properties, Kind kind, int tier) {
        super(properties);
        this.kind = kind;
        this.tier = tier;
    }
}
