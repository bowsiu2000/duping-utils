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
package net.ccbluex.liquidbounce.features.module.modules.misc

import net.ccbluex.liquidbounce.config.types.ToggleableConfigurable
import net.ccbluex.liquidbounce.event.events.NotificationEvent
import net.ccbluex.liquidbounce.event.events.PacketEvent
import net.ccbluex.liquidbounce.event.events.WorldRenderEvent
import net.ccbluex.liquidbounce.event.handler
import net.ccbluex.liquidbounce.event.tickHandler
import net.ccbluex.liquidbounce.features.module.Category
import net.ccbluex.liquidbounce.features.module.ClientModule
import net.ccbluex.liquidbounce.render.engine.Color4b
import net.ccbluex.liquidbounce.utils.client.MessageMetadata
import net.ccbluex.liquidbounce.utils.client.chat
import net.ccbluex.liquidbounce.utils.client.notification
import net.ccbluex.liquidbounce.utils.math.Easing
import net.ccbluex.liquidbounce.utils.render.WireframePlayer
import net.minecraft.network.packet.s2c.common.DisconnectS2CPacket
import net.minecraft.network.packet.s2c.play.PlayerPositionLookS2CPacket
import net.minecraft.util.math.Vec3d
import org.apache.commons.lang3.StringUtils

/**
 * Module Flag Check.
 *
 * Alerts you about set backs.
 */
object ModuleFlagCheck : ClientModule("FlagCheck", Category.MISC, aliases = arrayOf("FlagDetect")) {

    private var chatMessage by boolean("ChatMessage", true)
    private var notification by boolean("Notification", false)
    private var invalidAttributes by boolean("InvalidAttributes", false)

    private object ResetFlags : ToggleableConfigurable(this, "ResetFlags", true) {

        private var afterSeconds by int("After", 30, 1..300, "s")

        @Suppress("unused")
        private val repeatable = tickHandler {
            flagCount = 0
            waitSeconds(afterSeconds)
        }

    }

    private object Render : ToggleableConfigurable(this, "Render", true) {

        private val notInFirstPerson by boolean("NotInFirstPerson", true)
        private val renderTime by int("Alive", 1000, 0..3000, "ms")
        private val fadeOut by curve("FadeOut", Easing.QUAD_OUT)
        private val outTime by int("OutTime", 500, 0..2000, "ms")
        private var color by color("Color", Color4b.RED.alpha(100).darker())
        private var outlineColor by color("OutlineColor", Color4b.RED.darker())

        val wireframePlayer = WireframePlayer(Vec3d.ZERO, 0f, 0f)
        var creationTime = 0L
        var finished = true

        override fun enable() {
            finished = true
        }

        @Suppress("unused")
        val renderHandler = handler<WorldRenderEvent> {
            if (finished || notInFirstPerson && mc.options.perspective.isFirstPerson) {
                return@handler
            }

            val time = System.currentTimeMillis()
            val withinRenderDuration = time - creationTime < renderTime

            if (withinRenderDuration) {
                wireframePlayer.render(it, color, outlineColor)
            } else {
                val factor = 1f - fadeOut.getFactor(creationTime + renderTime, time, outTime.toFloat())
                if (factor == 0f) {
                    finished = true
                    return@handler
                }

                wireframePlayer.render(it, color.fade(factor), outlineColor.fade(factor))
            }
        }

        fun reset() {
            creationTime = System.currentTimeMillis()
            finished = false
        }

    }

    init {
        tree(ResetFlags)
        tree(Render)
    }

    private var flagCount = 0

    @Suppress("unused")
    private val packetHandler = handler<PacketEvent> { event ->
        when (val packet = event.packet) {
            is PlayerPositionLookS2CPacket -> {
                if (player.age <= 25) {
                    return@handler
                }

                flagCount++
                alert(AlertReason.LAGBACK)
                Render.reset()
                val change = packet.change
                val position = change.position
                Render.wireframePlayer.setPosRot(position.x, position.y, position.z, change.yaw, change.pitch)
            }

            is DisconnectS2CPacket -> {
                flagCount = 0
            }
        }
    }

    @Suppress("unused")
    private val repeatable = tickHandler {
        if (!invalidAttributes) {
            return@tickHandler
        }

        val invalidHeath = player.health <= 0f && player.isAlive
        val invalidHunger = player.hungerManager.foodLevel <= 0

        if (!invalidHeath && !invalidHunger) {
            return@tickHandler
        }

        val invalidReasons = mutableListOf<String>()

        if (invalidHeath) {
            invalidReasons.add("Health")
        }

        if (invalidHunger) {
            invalidReasons.add("Hunger")
        }

        if (invalidReasons.isNotEmpty()) {
            flagCount++

            val reasonString = invalidReasons.joinToString()
            alert(AlertReason.INVALID, reasonString)
        }
    }

    private fun alert(reason: AlertReason, extra: String? = null) {
        val message = if (StringUtils.isEmpty(extra)) {
            message("alert", message(reason.key), flagCount)
        } else {
            message("alertWithExtra", message(reason.key), extra!!, flagCount)
        }

        if (notification) {
            notification(name, message, NotificationEvent.Severity.INFO)
        }

        if (chatMessage) {
            chat(message, metadata = MessageMetadata(id = "$name#${reason.key}"))
        }
    }

    @Suppress("SpellCheckingInspection")
    private enum class AlertReason(val key: String) {
        INVALID("invalid"),
        LAGBACK("lagback")
    }

}
