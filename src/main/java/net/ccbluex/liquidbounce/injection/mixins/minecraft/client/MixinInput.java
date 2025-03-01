/*
 * This file is part of LiquidBounce (https://github.com/CCBlueX/LiquidBounce)
 *
 * Copyright (c) 2015 - 2024 CCBlueX
 *
 * LiquidBounce is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * LiquidBounce is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with LiquidBounce. If not, see <https://www.gnu.org/licenses/>.
 */

package net.ccbluex.liquidbounce.injection.mixins.minecraft.client;

import net.ccbluex.liquidbounce.features.module.modules.combat.criticals.ModuleCriticals;
import net.ccbluex.liquidbounce.features.module.modules.movement.ModuleSprint;
import net.minecraft.client.input.Input;
import net.minecraft.util.PlayerInput;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Input.class)
public abstract class MixinInput {

    @Shadow
    public float movementForward;

    @Shadow
    public float movementSideways;

    @Shadow
    public PlayerInput playerInput;

    @Inject(method = "hasForwardMovement", cancellable = true, at = @At("RETURN"))
    private void hookOmnidirectionalSprintA(final CallbackInfoReturnable<Boolean> callbackInfoReturnable) {
        if (ModuleCriticals.WhenSprinting.INSTANCE.getRunning() && ModuleCriticals.WhenSprinting.INSTANCE.getStopSprinting() == ModuleCriticals.WhenSprinting.StopSprintingMode.LEGIT) {
            callbackInfoReturnable.setReturnValue(false);
            return;
        }

        final boolean hasMovement = Math.abs(movementForward) > 1.0E-5F || Math.abs(movementSideways) > 1.0E-5F;

        callbackInfoReturnable.setReturnValue(!ModuleSprint.INSTANCE.shouldPreventSprint() && (ModuleSprint.INSTANCE.shouldSprintOmnidirectionally() ? hasMovement : callbackInfoReturnable.getReturnValue()));
    }

}
