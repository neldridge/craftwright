package dev.neldridge.craftwright;

/** Pure balancing rules, shared by the machine and its UI. */
public final class MachineBalance {
    public static final int MAX_SPEED = 8;
    public static int craftTicks(CrafterTier tier, int speed) {
        // Equal reductions in the interval, rounded up; the final upgrade reaches 1 tick.
        int n = Math.clamp(speed, 0, MAX_SPEED);
        return Math.max(1, (int) Math.ceil(tier.baseTicks - (tier.baseTicks - 1) * n / (double) MAX_SPEED));
    }
    public static int patternCapacity(CrafterTier tier, int upgrades) {
        return Math.min(tier.maxPatterns, tier.basePatterns + Math.max(0, upgrades));
    }
    public static int transferAmount(int tier) {
        return switch (tier) { case 1 -> 1; case 2 -> 8; case 3 -> 32; case 4 -> 64; default -> 0; };
    }
    public static int workFe(int base, int speed, int patterns, int pull, int push) {
        return Math.toIntExact(Math.min(Integer.MAX_VALUE, (long) base * (1 + speed) * (1 + speed) + 2L * patterns + pull + push));
    }
    private MachineBalance() {}
}
