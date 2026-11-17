package dev.neldridge.craftwright;

public enum SideMode {
    INPUT(true, false), OUTPUT(false, true), BOTH(true, true), DISABLED(false, false);
    public final boolean input, output;
    SideMode(boolean input, boolean output) { this.input = input; this.output = output; }
    public SideMode next() { return values()[(ordinal() + 1) % values().length]; }
}
