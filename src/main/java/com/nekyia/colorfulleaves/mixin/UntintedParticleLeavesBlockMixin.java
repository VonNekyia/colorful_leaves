package com.nekyia.colorfulleaves.mixin;

import com.nekyia.colorfulleaves.LeafColors;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ColorParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.ParticleUtils;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.UntintedParticleLeavesBlock;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Leaves with their colour painted in drop leaves of that colour - cherry petals, poplar
 * leaves. Where the server gave them another, they drop the tinted leaves oak drops, in it.
 */
@Mixin(UntintedParticleLeavesBlock.class)
abstract class UntintedParticleLeavesBlockMixin {

    @Inject(method = "spawnFallingLeavesParticle", at = @At("HEAD"), cancellable = true)
    private void colorfulleaves$fallInColour(Level level, BlockPos pos, RandomSource random, CallbackInfo ci) {
        int tint = LeafColors.tint(pos);
        if (tint != 0) {
            ParticleUtils.spawnParticleBelow(level, pos, random, ColorParticleOption.create(ParticleTypes.TINTED_LEAVES, tint));
            ci.cancel();
        }
    }
}
