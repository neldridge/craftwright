package dev.neldridge.craftwright.network;

import java.util.List;
import dev.neldridge.craftwright.CrafterMenu;
import dev.neldridge.craftwright.Craftwright;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import net.minecraft.server.level.ServerPlayer;

public record PatternTransferPayload(int containerId, int transferId, int pattern, Identifier recipeId, List<ItemStack> ingredients) implements CustomPacketPayload {
    public static final Type<PatternTransferPayload> TYPE = new Type<>(Identifier.fromNamespaceAndPath(Craftwright.MOD_ID, "pattern_transfer"));
    public static final StreamCodec<RegistryFriendlyByteBuf, PatternTransferPayload> STREAM_CODEC = StreamCodec.composite(
        ByteBufCodecs.VAR_INT, PatternTransferPayload::containerId,
        ByteBufCodecs.VAR_INT, PatternTransferPayload::transferId,
        ByteBufCodecs.VAR_INT, PatternTransferPayload::pattern,
        Identifier.STREAM_CODEC, PatternTransferPayload::recipeId,
        ItemStack.OPTIONAL_STREAM_CODEC.apply(ByteBufCodecs.list(9)), PatternTransferPayload::ingredients,
        PatternTransferPayload::new);
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    public boolean apply(Player player) {
        return player.containerMenu instanceof CrafterMenu menu && menu.containerId == containerId
            && menu.importPattern(player, pattern, recipeId, ingredients);
    }
    public static void register(RegisterPayloadHandlersEvent event) {
        var registrar = event.registrar("1");
        registrar.playToServer(TYPE, STREAM_CODEC, (payload, context) -> context.enqueueWork(() -> {
            boolean success = payload.apply(context.player());
            if (context.player() instanceof ServerPlayer player) PacketDistributor.sendToPlayer(player,
                new PatternTransferResultPayload(payload.containerId(), payload.transferId(), success));
        }));
        registrar.playToClient(PatternTransferResultPayload.TYPE, PatternTransferResultPayload.STREAM_CODEC,
            (payload, context) -> context.enqueueWork(() -> PatternTransferFeedback.complete(payload)));
    }
}
