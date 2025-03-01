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
package net.ccbluex.liquidbounce.injection.mixins.minecraft.entity;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.llamalad7.mixinextras.injector.v2.WrapWithCondition;
import net.ccbluex.liquidbounce.event.EventManager;
import net.ccbluex.liquidbounce.event.EventState;
import net.ccbluex.liquidbounce.event.events.*;
import net.ccbluex.liquidbounce.features.module.modules.combat.ModuleSuperKnockback;
import net.ccbluex.liquidbounce.features.module.modules.combat.criticals.ModuleCriticals;
import net.ccbluex.liquidbounce.features.module.modules.combat.killaura.ModuleKillAura;
import net.ccbluex.liquidbounce.features.module.modules.exploit.ModuleAntiHunger;
import net.ccbluex.liquidbounce.features.module.modules.exploit.ModulePortalMenu;
import net.ccbluex.liquidbounce.features.module.modules.movement.ModuleEntityControl;
import net.ccbluex.liquidbounce.features.module.modules.movement.ModuleSprint;
import net.ccbluex.liquidbounce.features.module.modules.movement.noslow.ModuleNoSlow;
import net.ccbluex.liquidbounce.features.module.modules.movement.step.ModuleStep;
import net.ccbluex.liquidbounce.features.module.modules.render.ModuleClickGui;
import net.ccbluex.liquidbounce.features.module.modules.render.ModuleFreeCam;
import net.ccbluex.liquidbounce.features.module.modules.render.ModuleNoSwing;
import net.ccbluex.liquidbounce.features.module.modules.render.ModuleRotations;
import net.ccbluex.liquidbounce.integration.BrowserScreen;
import net.ccbluex.liquidbounce.integration.VrScreen;
import net.ccbluex.liquidbounce.integration.interop.protocol.rest.v1.game.PlayerData;
import net.ccbluex.liquidbounce.utils.aiming.Rotation;
import net.ccbluex.liquidbounce.utils.aiming.RotationManager;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.input.Input;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.MovementType;
import net.minecraft.network.packet.c2s.play.HandSwingC2SPacket;
import net.minecraft.util.Hand;
import net.minecraft.util.math.Vec3d;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientPlayerEntity.class)
public abstract class MixinClientPlayerEntity extends MixinPlayerEntity {

    @Shadow
    public Input input;

    @Shadow
    @Final
    public ClientPlayNetworkHandler networkHandler;

    @Shadow
    public abstract boolean isSubmergedInWater();

    @Shadow
    protected abstract boolean isWalking();

    @Unique
    private PlayerData lastKnownStatistics = null;

    @Unique
    private PlayerNetworkMovementTickEvent eventMotion;

    /**
     * Hook entity tick event
     */
    @Inject(method = "tick", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/network/AbstractClientPlayerEntity;tick()V",
            shift = At.Shift.BEFORE,
            ordinal = 0),
            cancellable = true)
    private void hookTickEvent(CallbackInfo ci) {
        var tickEvent = new PlayerTickEvent();
        EventManager.INSTANCE.callEvent(tickEvent);

        if (tickEvent.isCancelled()) {
            ci.cancel();
        }
    }

    @Inject(method = "tick", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/network/AbstractClientPlayerEntity;tick()V",
            shift = At.Shift.AFTER,
            ordinal = 0))
    private void hookPostTickEvent(CallbackInfo ci) {
        EventManager.INSTANCE.callEvent(new PlayerPostTickEvent());

        // Call player statistics change event when statistics change
        var statistics = PlayerData.Companion.fromPlayer((ClientPlayerEntity) (Object) this);
        if (lastKnownStatistics == null || lastKnownStatistics != statistics) {
            EventManager.INSTANCE.callEvent(ClientPlayerDataEvent.Companion.fromPlayerStatistics(statistics));
        }
        this.lastKnownStatistics = statistics;
    }

    /**
     * Hook entity movement tick event
     */
    @Inject(method = "tickMovement", at = @At("HEAD"))
    private void hookMovementTickEvent(CallbackInfo callbackInfo) {
        EventManager.INSTANCE.callEvent(new PlayerMovementTickEvent());
    }

    /**
     * Hook entity movement tick event at HEAD and call out PRE tick movement event
     */
    @Inject(method = "sendMovementPackets", at = @At("HEAD"))
    private void hookMovementPre(CallbackInfo callbackInfo) {
        ClientPlayerEntity player = (ClientPlayerEntity) (Object) this;
        eventMotion = new PlayerNetworkMovementTickEvent(EventState.PRE, player.getX(), player.getY(), player.getZ(), player.isOnGround());
        EventManager.INSTANCE.callEvent(eventMotion);
    }

    @ModifyExpressionValue(method = "sendMovementPackets", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/network/ClientPlayerEntity;getX()D"))
    private double modifyXPosition(double original) {
        return eventMotion.getX();
    }

    @ModifyExpressionValue(method = "sendMovementPackets", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/network/ClientPlayerEntity;getY()D"))
    private double modifyYPosition(double original) {
        return eventMotion.getY();
    }

    @ModifyExpressionValue(method = "sendMovementPackets", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/network/ClientPlayerEntity;getZ()D"))
    private double modifyZPosition(double original) {
        return eventMotion.getZ();
    }

    @ModifyExpressionValue(method = "sendMovementPackets", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/network/ClientPlayerEntity;isOnGround()Z"))
    private boolean modifyOnGround(boolean original) {
        return eventMotion.getGround();
    }

    /**
     * Hook entity movement tick event at RETURN and call out POST tick movement event
     */
    @Inject(method = "sendMovementPackets", at = @At("RETURN"))
    private void hookMovementPost(CallbackInfo callbackInfo) {
        ClientPlayerEntity player = (ClientPlayerEntity) (Object) this;
        EventManager.INSTANCE.callEvent(new PlayerNetworkMovementTickEvent(EventState.POST, player.getX(), player.getY(), player.getZ(), player.isOnGround()));
    }

    /**
     * Hook push out function tick at HEAD and call out push out event, which is able to stop the cancel the execution.
     */
    @Inject(method = "pushOutOfBlocks", at = @At("HEAD"), cancellable = true)
    private void hookPushOut(double x, double z, CallbackInfo ci) {
        final PlayerPushOutEvent pushOutEvent = new PlayerPushOutEvent();
        EventManager.INSTANCE.callEvent(pushOutEvent);
        if (pushOutEvent.isCancelled()) {
            ci.cancel();
        }
    }

    /**
     * Hook move function at HEAD and call out move event, which is able to stop the cancel the execution.
     */
    @Inject(method = "move", at = @At("HEAD"))
    private void hookMove(MovementType type, Vec3d movement, CallbackInfo callbackInfo) {
        EventManager.INSTANCE.callEvent(new PlayerMoveEvent(type, movement));
    }

    /**
     * Hook portal menu module to make opening menus in portals possible
     */
    @ModifyExpressionValue(method = "tickNausea", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/screen/Screen;shouldPause()Z"))
    private boolean hookNetherClosingScreen(boolean original) {
        if (ModulePortalMenu.INSTANCE.getRunning()) {
            return true;
        }

        return original;
    }

    /**
     * Hook custom sneaking multiplier
     */
    @ModifyExpressionValue(method = "tickMovement", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/network/ClientPlayerEntity;getAttributeValue(Lnet/minecraft/registry/entry/RegistryEntry;)D"))
    private double hookCustomSneakingMultiplier(double original) {
        var playerSneakMultiplier = new PlayerSneakMultiplier(original);
        EventManager.INSTANCE.callEvent(playerSneakMultiplier);
        return playerSneakMultiplier.getMultiplier();
    }

    /**
     * Hook custom multiplier
     */
    @Inject(method = "tickMovement", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/network/ClientPlayerEntity;isUsingItem()Z", ordinal = 0))
    private void hookCustomMultiplier(CallbackInfo callbackInfo) {
        final Input input = this.input;
        // reverse
        input.movementForward /= 0.2f;
        input.movementSideways /= 0.2f;

        // then
        final PlayerUseMultiplier playerUseMultiplier = new PlayerUseMultiplier(0.2f, 0.2f);
        EventManager.INSTANCE.callEvent(playerUseMultiplier);
        input.movementForward *= playerUseMultiplier.getForward();
        input.movementSideways *= playerUseMultiplier.getSideways();
    }

    /**
     * Hook sprint effect from NoSlow module
     */
    @ModifyExpressionValue(method = "canStartSprinting", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/network/ClientPlayerEntity;isUsingItem()Z"))
    private boolean hookSprintAffectStart(boolean original) {
        if (ModuleNoSlow.INSTANCE.getRunning()) {
            return false;
        }

        return original;
    }

    // Silent rotations (Rotation Manager)

    @ModifyExpressionValue(method = {"sendMovementPackets", "tick"}, at = @At(value = "INVOKE", target = "Lnet/minecraft/client/network/ClientPlayerEntity;getYaw()F"))
    private float hookSilentRotationYaw(float original) {
        Rotation rotation = RotationManager.INSTANCE.getCurrentRotation();
        if (rotation == null) {
            return original;
        }

        return rotation.getYaw();
    }

    @ModifyExpressionValue(method = {"sendMovementPackets", "tick"}, at = @At(value = "INVOKE", target = "Lnet/minecraft/client/network/ClientPlayerEntity;getPitch()F"))
    private float hookSilentRotationPitch(float original) {
        Rotation rotation = RotationManager.INSTANCE.getCurrentRotation();
        if (rotation == null) {
            return original;
        }

        return rotation.getPitch();
    }

    @ModifyReturnValue(method = "isAutoJumpEnabled", at = @At("RETURN"))
    private boolean injectLegitStep(boolean original) {
        if (ModuleStep.Legit.INSTANCE.getRunning()) {
            return true;
        }

        return original;
    }

    @Inject(method = "swingHand", at = @At("HEAD"), cancellable = true)
    private void swingHand(Hand hand, CallbackInfo ci) {
        if (ModuleNoSwing.INSTANCE.getRunning()) {
            if (!ModuleNoSwing.INSTANCE.shouldHideForServer()) {
                networkHandler.sendPacket(new HandSwingC2SPacket(hand));
            }
            if (!ModuleNoSwing.INSTANCE.shouldHideForClient()) {
                swingHand(hand, false);
            }

            ci.cancel();
        }
    }

    @ModifyReturnValue(method = "getMountJumpStrength", at = @At("RETURN"))
    private float hookMountJumpStrength(float original) {
        if (ModuleEntityControl.INSTANCE.getRunning() && ModuleEntityControl.INSTANCE.getEnforceJumpStrength()) {
            return 1f;
        }

        return original;
    }

    @ModifyExpressionValue(method = "tickMovement", at = @At(value = "FIELD", target = "Lnet/minecraft/entity/player/PlayerAbilities;allowFlying:Z"))
    private boolean hookFreeCamPreventCreativeFly(boolean original) {
        return !ModuleFreeCam.INSTANCE.getRunning() && original;
    }

    @ModifyVariable(method = "sendMovementPackets", at = @At("STORE"), ordinal = 1)
    private boolean hookFreeCamPreventRotations(boolean bl4) {
        return (!ModuleFreeCam.INSTANCE.shouldDisableRotations() ||  ModuleRotations.INSTANCE.shouldSendCustomRotation())  && bl4;
    }

    @ModifyConstant(method = "canSprint", constant = @Constant(floatValue = 6.0F), slice = @Slice(from = @At(value = "INVOKE", target = "Lnet/minecraft/entity/player/HungerManager;getFoodLevel()I", ordinal = 0)))
    private float hookSprintIgnoreHunger(float constant) {
        return ModuleSprint.INSTANCE.shouldIgnoreHunger() ? -1F : constant;
    }

    @ModifyExpressionValue(method = "tickMovement", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/option/KeyBinding;isPressed()Z"))
    private boolean hookAutoSprint(boolean original) {
        return !ModuleSuperKnockback.INSTANCE.shouldBlockSprinting() && !ModuleKillAura.INSTANCE.shouldBlockSprinting()
                && (ModuleSprint.INSTANCE.getRunning() || original);
    }

    @ModifyExpressionValue(method = "tickMovement", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/network/ClientPlayerEntity;isWalking()Z"))
    private boolean hookOmnidirectionalSprintB(boolean original) {
        return liquid_bounce$isOmniWalking();
    }

    @ModifyExpressionValue(method = "canStartSprinting", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/network/ClientPlayerEntity;isBlind()Z"))
    private boolean hookSprintIgnoreBlindness(boolean original) {
        return !ModuleSprint.INSTANCE.shouldIgnoreBlindness() && original;
    }

    @ModifyExpressionValue(method = "canStartSprinting", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/network/ClientPlayerEntity;isWalking()Z"))
    private boolean hookOmnidirectionalSprintC(boolean original) {
        return liquid_bounce$isOmniWalking();
    }

    @ModifyExpressionValue(method = "tickMovement", at = @At(value = "FIELD", target = "Lnet/minecraft/client/network/ClientPlayerEntity;horizontalCollision:Z"))
    private boolean hookSprintIgnoreCollision(boolean original) {
        return !ModuleSprint.INSTANCE.shouldIgnoreCollision() && original;
    }

    @Unique
    private boolean liquid_bounce$isOmniWalking() {
        boolean hasMovement = Math.abs(input.movementForward) > 1.0E-5F || Math.abs(input.movementSideways) > 1.0E-5F;
        boolean isWalking = (double) Math.abs(input.movementForward) >= 0.8 || (double) Math.abs(input.movementSideways) >= 0.8;
        boolean modifiedIsWalking = this.isSubmergedInWater() ? hasMovement : isWalking;
        return ModuleSprint.INSTANCE.shouldSprintOmnidirectionally() ? modifiedIsWalking : this.isWalking();
    }

    @ModifyExpressionValue(method = "sendSprintingPacket", at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/network/ClientPlayerEntity;isSprinting()Z")
    )
    private boolean hookNoHungerSprint(boolean original) {
        if (ModuleCriticals.WhenSprinting.INSTANCE.getRunning() && ModuleCriticals.WhenSprinting.INSTANCE.getStopSprinting() == ModuleCriticals.WhenSprinting.StopSprintingMode.ON_NETWORK) {
            return false;
        }

        return !(ModuleAntiHunger.INSTANCE.getRunning() && ModuleAntiHunger.INSTANCE.getNoSprint()) && original;
    }

    @WrapWithCondition(method = "closeScreen", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/MinecraftClient;setScreen(Lnet/minecraft/client/gui/screen/Screen;)V"))
    private boolean preventCloseScreen(MinecraftClient instance, Screen screen) {
        // Prevent closing screen if the current screen is a client screen
        return !(instance.currentScreen instanceof BrowserScreen || instance.currentScreen instanceof VrScreen ||
                instance.currentScreen instanceof ModuleClickGui.ClickScreen);
    }

}
