package dev.neldridge.craftwright.network;

import dev.neldridge.craftwright.Craftwright;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

public record PatternTransferResultPayload(int containerId, int transferId, boolean success) implements CustomPacketPayload {
    public static final Type<PatternTransferResultPayload> TYPE = new Type<>(Identifier.fromNamespaceAndPath(Craftwright.MOD_ID, "pattern_transfer_result"));
    public static final StreamCodec<RegistryFriendlyByteBuf, PatternTransferResultPayload> STREAM_CODEC = StreamCodec.composite(
        ByteBufCodecs.VAR_INT, PatternTransferResultPayload::containerId,
        ByteBufCodecs.VAR_INT, PatternTransferResultPayload::transferId,
        ByteBufCodecs.BOOL, PatternTransferResultPayload::success, PatternTransferResultPayload::new);
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
