package meteordevelopment.meteorclient.mixin;

import meteordevelopment.meteorclient.systems.modules.Modules;
import meteordevelopment.meteorclient.systems.modules.movement.ElytraFakeFly;
import net.minecraft.item.FireworkRocketItem;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;

@Mixin(FireworkRocketItem.class)
public class FireworkRocketItemMixin {

    @ModifyExpressionValue(method = "use", at = @At(value = "INVOKE", target = "Lnet/minecraft/entity/player/PlayerEntity;isFallFlying()Z"))
    private boolean overrideIsFallFlying(boolean original) {
        ElytraFakeFly fakeFly = Modules.get().get(ElytraFakeFly.class);

        if (fakeFly.isFlying()) {
            return false;
        }

        return original;
    }
}