package com.argon.chronopocket.mixin;

import com.argon.chronopocket.time.TimeRewindManager;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Level.class)
public abstract class LevelBlockMixin {

    @Shadow
    public abstract boolean isClientSide();

    @Shadow
    public abstract BlockState getBlockState(BlockPos pos);

    /**
     * Intercepts all block modifications in Minecraft:
     * - Blocks broken by players (survival, creative)
     * - Blocks placed by players, dispensers, or commands
     * - Explosions (creeper, TNT, end crystal)
     * - Environmental changes (water/lava, falling blocks, fire, etc.)
     */
    @Inject(
        method = "setBlock(Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;II)Z",
        at = @At("HEAD")
    )
    private void chronopocket$onSetBlock(BlockPos pos, BlockState newState, int flags, int maxUpdateDepth, CallbackInfoReturnable<Boolean> cir) {
        if (this.isClientSide() || TimeRewindManager.isRestoring() || TimeRewindManager.isRewinding()) {
            return;
        }

        Level self = (Level) (Object) this;
        BlockState oldState = this.getBlockState(pos);

        if (!oldState.equals(newState)) {
            TimeRewindManager.recordBlockChange(self.dimension(), pos, oldState);
        }
    }
}
