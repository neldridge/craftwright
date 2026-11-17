package dev.neldridge.craftwright;

public enum CrafterTier {
    REGULAR(1, 3, 40, 40_000), ADVANCED(6, 12, 10, 400_000);

    public final int basePatterns, maxPatterns, baseTicks, energyCapacity;
    CrafterTier(int basePatterns, int maxPatterns, int baseTicks, int energyCapacity) {
        this.basePatterns = basePatterns;
        this.maxPatterns = maxPatterns;
        this.baseTicks = baseTicks;
        this.energyCapacity = energyCapacity;
    }
}
