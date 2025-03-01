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
package net.ccbluex.liquidbounce.features.module.modules.combat.velocity.mode

import net.ccbluex.liquidbounce.config.types.Choice
import net.ccbluex.liquidbounce.config.types.ChoiceConfigurable
import net.ccbluex.liquidbounce.config.types.ToggleableConfigurable
import net.ccbluex.liquidbounce.event.EventListener
import net.ccbluex.liquidbounce.event.events.AttackEntityEvent
import net.ccbluex.liquidbounce.event.handler
import net.ccbluex.liquidbounce.event.tickHandler
import net.ccbluex.liquidbounce.features.module.modules.combat.velocity.ModuleVelocity.modes
import net.minecraft.client.gui.screen.ingame.InventoryScreen

object VelocityIntave : VelocityMode("Intave") {

    private class ReduceOnAttack(parent: EventListener?) : ToggleableConfigurable(
        parent, "ReduceOnAttack",
        true
    ) {
        private val reduceFactor by float("Factor", 0.6f, 0.6f..1f)
        private val hurtTime by int("HurtTime", 9, 1..10)
        var lastAttackTime = 0L

        @Suppress("unused")
        private val attackHandler = handler<AttackEntityEvent> {
            if (player.hurtTime == hurtTime && System.currentTimeMillis() - lastAttackTime <= 8000) {
                player.velocity.x *= reduceFactor
                player.velocity.z *= reduceFactor
            }
            lastAttackTime = System.currentTimeMillis()
        }
    }

    init {
        tree(ReduceOnAttack(this))
    }

    private class JumpReset(parent: EventListener?) : ToggleableConfigurable(
        parent, "JumpReset",
        true
    ) {

        private val chance by float("Chance", 50f, 0f..100f, "%")

        @Suppress("unused")
        private val repeatable = tickHandler {
            val shouldJump = Math.random() * 100 < chance && player.hurtTime > 5
            val canJump = player.isOnGround && mc.currentScreen !is InventoryScreen

            if (shouldJump && canJump) {
                player.jump()
            }
        }
    }

    init {
        tree(JumpReset(this))
    }
}
