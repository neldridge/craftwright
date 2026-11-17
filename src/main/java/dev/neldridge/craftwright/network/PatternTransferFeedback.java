package dev.neldridge.craftwright.network;

import java.util.function.Consumer;

/** Keeps optional recipe-viewer classes out of the common networking classloader. Main-thread use only. */
public final class PatternTransferFeedback {
    private static int menu, request;
    private static Consumer<Boolean> completion;
    public static void await(int containerId, int transferId, Consumer<Boolean> callback) {
        menu = containerId;
        request = transferId;
        completion = callback;
    }
    public static void complete(PatternTransferResultPayload result) {
        if (completion == null || result.containerId() != menu || result.transferId() != request) return;
        var callback = completion;
        completion = null;
        callback.accept(result.success());
    }
    public static void clear() { completion = null; }
    private PatternTransferFeedback() {}
}
