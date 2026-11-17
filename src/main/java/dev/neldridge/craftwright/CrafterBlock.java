package dev.neldridge.craftwright;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.*;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.server.level.ServerPlayer;

public class CrafterBlock extends BaseEntityBlock {
    public static final MapCodec<CrafterBlock> REGULAR_CODEC = simpleCodec(p -> new CrafterBlock(p, CrafterTier.REGULAR));
    public static final MapCodec<CrafterBlock> ADVANCED_CODEC = simpleCodec(p -> new CrafterBlock(p, CrafterTier.ADVANCED));
    public final CrafterTier tier;
    public CrafterBlock(Properties properties, CrafterTier tier) { super(properties); this.tier = tier; }
    @Override protected MapCodec<? extends BaseEntityBlock> codec() { return tier == CrafterTier.REGULAR ? REGULAR_CODEC : ADVANCED_CODEC; }
    @Override protected RenderShape getRenderShape(BlockState state) { return RenderShape.MODEL; }
    @Override public BlockEntity newBlockEntity(BlockPos pos, BlockState state) { return new CrafterBlockEntity(pos, state); }
    @Override public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        return level.isClientSide() ? null : createTickerHelper(type, Craftwright.CRAFTER_BE.get(), CrafterBlockEntity::tick);
    }
    @Override protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (player instanceof ServerPlayer serverPlayer && level.getBlockEntity(pos) instanceof CrafterBlockEntity be) {
            serverPlayer.openMenu(be, buf -> { buf.writeBlockPos(pos); buf.writeEnum(tier); });
        }
        return InteractionResult.SUCCESS;
    }
}
