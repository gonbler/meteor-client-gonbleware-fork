/*
 * This file is part of the Meteor Client distribution
 * (https://github.com/MeteorDevelopment/meteor-client). Copyright (c) Meteor Development.
 */

package meteordevelopment.meteorclient.mixin;

import meteordevelopment.meteorclient.systems.modules.Modules;
import meteordevelopment.meteorclient.systems.modules.movement.MovementFix;
import meteordevelopment.meteorclient.systems.modules.movement.NoSlow;
import meteordevelopment.meteorclient.systems.modules.player.SilentMine;
import net.minecraft.block.BlockState;
import net.minecraft.block.CobwebBlock;
import net.minecraft.entity.Entity;
import net.minecraft.network.packet.c2s.play.PlayerActionC2SPacket;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import static meteordevelopment.meteorclient.MeteorClient.mc;

@Mixin(CobwebBlock.class)
public abstract class CobwebBlockMixin {
    @Inject(method = "onEntityCollision", at = @At("HEAD"), cancellable = true)
    private void onEntityCollision(BlockState state, World world, BlockPos pos, Entity entity,
            CallbackInfo info) {
        if (entity == mc.player) {
            NoSlow noSlow = Modules.get().get(NoSlow.class);

            if (noSlow.cobweb()) {
                info.cancel();
            }

            if (noSlow.cobwebGrim()) {
                info.cancel();

                int s1 = mc.world.getPendingUpdateManager().incrementSequence().getSequence();
                //int s2 = mc.world.getPendingUpdateManager().incrementSequence().getSequence();
                mc.getNetworkHandler().sendPacket(new PlayerActionC2SPacket(
                        PlayerActionC2SPacket.Action.STOP_DESTROY_BLOCK, pos, Direction.UP, s1));

                MovementFix.inWebs = true;

                if (Modules.get().get(SilentMine.class).isActive() && Modules.get().get(SilentMine.class).antiRubberband.get()) {
                    return;
                }
                
                mc.getNetworkHandler().sendPacket(new PlayerActionC2SPacket(
                        PlayerActionC2SPacket.Action.ABORT_DESTROY_BLOCK, pos, Direction.UP));

            }
        }
    }
}
