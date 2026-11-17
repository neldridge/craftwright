package dev.neldridge.craftwright;

import net.neoforged.neoforge.common.ModConfigSpec;

public final class CraftwrightConfig {
    public static final ModConfigSpec SPEC;
    public static final ModConfigSpec.IntValue REGULAR_FE, ADVANCED_FE, TRANSFER_INTERVAL, TRANSFER_FE;
    static {
        var b = new ModConfigSpec.Builder();
        REGULAR_FE = b.comment("Base FE per working tick for a regular crafter.").defineInRange("regularFePerTick", 4, 1, 100000);
        ADVANCED_FE = b.comment("Base FE per working tick for an advanced crafter.").defineInRange("advancedFePerTick", 16, 1, 100000);
        TRANSFER_INTERVAL = b.comment("Ticks between active transfer operations. Budgets are shared across all faces.").defineInRange("transferInterval", 20, 1, 1200);
        TRANSFER_FE = b.comment("FE per item actively pulled or pushed, multiplied by transfer upgrade tier.").defineInRange("transferFePerItem", 2, 1, 10000);
        SPEC = b.build();
    }
    private CraftwrightConfig() {}
}
