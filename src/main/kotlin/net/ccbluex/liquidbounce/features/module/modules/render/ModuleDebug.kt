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
package net.ccbluex.liquidbounce.features.module.modules.render

import net.ccbluex.liquidbounce.config.types.ToggleableConfigurable
import net.ccbluex.liquidbounce.event.EventListener
import net.ccbluex.liquidbounce.event.events.MovementInputEvent
import net.ccbluex.liquidbounce.event.events.OverlayRenderEvent
import net.ccbluex.liquidbounce.event.events.WorldRenderEvent
import net.ccbluex.liquidbounce.event.handler
import net.ccbluex.liquidbounce.event.tickHandler
import net.ccbluex.liquidbounce.features.module.Category
import net.ccbluex.liquidbounce.features.module.ClientModule
import net.ccbluex.liquidbounce.features.module.modules.movement.speed.ModuleSpeed
import net.ccbluex.liquidbounce.features.module.modules.player.ModuleBlink
import net.ccbluex.liquidbounce.features.module.modules.world.scaffold.ModuleScaffold
import net.ccbluex.liquidbounce.render.*
import net.ccbluex.liquidbounce.render.engine.Color4b
import net.ccbluex.liquidbounce.utils.entity.PlayerSimulationCache
import net.ccbluex.liquidbounce.utils.entity.eyes
import net.ccbluex.liquidbounce.utils.math.geometry.AlignedFace
import net.ccbluex.liquidbounce.utils.math.geometry.Line
import net.ccbluex.liquidbounce.utils.math.geometry.LineSegment
import net.ccbluex.liquidbounce.utils.math.geometry.PlaneSection
import net.ccbluex.liquidbounce.utils.math.toVec3
import net.minecraft.text.OrderedText
import net.minecraft.text.Text
import net.minecraft.util.Formatting
import net.minecraft.util.math.Box
import net.minecraft.util.math.Vec3d
import java.awt.Color

/**
 * Rotations module
 *
 * Allows you to see server-sided rotations.
 */

object ModuleDebug : ClientModule("Debug", Category.RENDER) {

    private val parameters by boolean("Parameters", true)
    private val geometry by boolean("Geometry", true)

    object RenderSimulatedPlayer : ToggleableConfigurable(this, "SimulatedPlayer", false) {

        private val ticksToPredict by int("TicksToPredict", 20, 5..100)

        @Suppress("unused")
        val tickRep = handler<MovementInputEvent> { _ ->
            // We aren't actually where we are because of blink.
            // So this module shall not cause any disturbance in that case.
            if (ModuleBlink.running) {
                return@handler
            }

            PlayerSimulationCache.getSimulationForLocalPlayer().simulateUntil(this.ticksToPredict)
        }

        val renderHandler = handler<WorldRenderEvent> { event ->
            val cachedPositions = PlayerSimulationCache
                .getSimulationForLocalPlayer()
                .getSnapshotsBetween(0 until this.ticksToPredict)

            renderEnvironmentForWorld(event.matrixStack) {
                withColor(Color4b.BLUE) {
                    drawLineStrip(positions = cachedPositions.map { relativeToCamera(it.pos).toVec3() })
                }
            }
        }

    }

    init {
        tree(RenderSimulatedPlayer)
    }

    private val debuggedGeometry = hashMapOf<DebuggedGeometryOwner, DebuggedGeometry>()

    val renderHandler = handler<WorldRenderEvent> { event ->
        val matrixStack = event.matrixStack

        if (!geometry) {
            return@handler
        }

        renderEnvironmentForWorld(matrixStack) {
            debuggedGeometry.values.forEach {
                it.render(this)
            }
        }
    }

    val repeatable = tickHandler {
        if (!ModuleSpeed.running) {
            return@tickHandler
        }

        val pos0 = Vec3d(77.0, 75.0, -52.0)
        val face = AlignedFace(pos0, pos0.add(1.0, 1.0, 0.0))

        ModuleDebug.debugGeometry(
            ModuleScaffold,
            "targetFace",
            ModuleDebug.DebuggedBox(Box(face.from, face.to), Color4b(255, 0, 0, 64))
        )

        val line = LineSegment(player.eyes, player.rotationVector, 0.0..10.0)

        ModuleDebug.debugGeometry(
            ModuleScaffold,
            "daLine",
            ModuleDebug.DebuggedLineSegment(line.endPoints.first, line.endPoints.second, Color4b(0, 0, 255, 255))
        )

        val pointTo = face.nearestPointTo(line)

        ModuleDebug.debugGeometry(
            ModuleScaffold,
            "targetPoint",
            ModuleDebug.DebuggedPoint(pointTo, Color4b(0, 0, 255, 255), size = 0.05)
        )
    }

    @Suppress("unused")
    val screenRenderHandler = handler<OverlayRenderEvent> { event ->
        val context = event.context

        if (mc.options.playerListKey.isPressed || !parameters) {
            return@handler
        }

        val width = mc.window.scaledWidth

        /**
         * Separate the debugged owner from its parameter
         * Structure should be like this:
         * Owner ->
         *   Parameter Name: Parameter Value
         *   Parameter Name: Parameter Value
         *   Parameter Name: Parameter Value
         */
        val textList = mutableListOf<OrderedText>()

        val debuggedOwners = debugParameters.keys.groupBy { it.owner }

        debuggedOwners.onEach { (owner, parameter) ->
            val ownerName = when (owner) {
                is ClientModule -> owner.name
                is EventListener -> "${owner.parent()?.javaClass?.simpleName}::${owner.javaClass.simpleName}"
                else -> owner.javaClass.simpleName
            }

            textList += Text.literal(ownerName).styled {
                it.withColor(Formatting.GOLD).withBold(true)
            }.asOrderedText()

            parameter.forEach { debuggedParameter ->
                val parameterName = debuggedParameter.name
                val parameterValue = debugParameters[debuggedParameter]
                textList += Text.literal("$parameterName: $parameterValue").styled {
                    it.withColor(Formatting.GRAY)
                }.asOrderedText()
            }
        }

        // Draw debug box of the screen with a width of 200
        val biggestWidth = textList.maxOfOrNull { mc.textRenderer.getWidth(it) + 10 }?.coerceAtLeast(80)
            ?: 80
        val directionWidth = biggestWidth / 2
        context.fill(
            width / 2 - directionWidth, 20, width / 2 + directionWidth,
            50 + (mc.textRenderer.fontHeight * textList.size), Color4b(0, 0, 0, 128).toARGB()
        )

        context.drawCenteredTextWithShadow(mc.textRenderer, Text.literal("Debugging").styled {
            it.withColor(Formatting.LIGHT_PURPLE).withBold(true)
        }.asOrderedText(), width / 2, 22, Color4b.WHITE.toARGB())

        // Draw white line below Debugging text
        context.fill(width / 2 - directionWidth, 32, width / 2 + directionWidth, 33, Color4b.WHITE.toARGB())

        // Draw text line one by one
        textList.forEachIndexed { index, text ->
            context.drawCenteredTextWithShadow(
                mc.textRenderer, text, width / 2, 40 +
                        (mc.textRenderer.fontHeight * index), Color4b.WHITE.toARGB()
            )
        }
    }

    inline fun debugGeometry(owner: Any, name: String, lazyGeometry: () -> DebuggedGeometry) {
        if (!running) {
            return
        }

        debugGeometry(owner, name, lazyGeometry.invoke())
    }

    fun debugGeometry(owner: Any, name: String, geometry: DebuggedGeometry) {
        // Do not take any new debugging while the module is off
        if (!running) {
            return
        }

        debuggedGeometry[DebuggedGeometryOwner(owner, name)] = geometry
    }

    private data class DebuggedGeometryOwner(val owner: Any, val name: String)

    private data class DebuggedParameter(val owner: Any, val name: String)

    private val debugParameters = hashMapOf<DebuggedParameter, Any>()

    inline fun debugParameter(owner: Any, name: String, lazyValue: () -> Any) {
        if (!running) {
            return
        }

        debugParameter(owner, name, lazyValue.invoke())
    }

    fun debugParameter(owner: Any, name: String, value: Any) {
        if (!running) {
            return
        }

        debugParameters[DebuggedParameter(owner, name)] = value
    }

    fun getArrayEntryColor(idx: Int, length: Int): Color4b {
        val hue = idx.toFloat() / length.toFloat()
        return Color4b(Color.getHSBColor(hue, 1f, 1f)).alpha(32)
    }

    sealed class DebuggedGeometry(val color: Color4b) {
        abstract fun render(env: WorldRenderEnvironment)
    }

    class DebuggedLine(line: Line, color: Color4b) : DebuggedGeometry(color) {
        val from: Vec3d
        val to: Vec3d

        init {
            val normalizedDirection = line.direction.normalize()

            this.from = line.position.subtract(normalizedDirection.multiply(100.0))
            this.to = line.position.add(normalizedDirection.multiply(100.0))
        }

        override fun render(env: WorldRenderEnvironment) {
            env.withColor(color) {
                this.drawLineStrip(relativeToCamera(from).toVec3(), relativeToCamera(to).toVec3())
            }
        }
    }

    class DebuggedQuad(val p1: Vec3d, val p2: Vec3d, color: Color4b) : DebuggedGeometry(color) {
        override fun render(env: WorldRenderEnvironment) {
            env.withColor(color) {
                this.drawQuad(relativeToCamera(p1).toVec3(), relativeToCamera(p2).toVec3())
            }
        }
    }

    class DebuggedLineSegment(val from: Vec3d, val to: Vec3d, color: Color4b) : DebuggedGeometry(color) {
        override fun render(env: WorldRenderEnvironment) {
            env.withColor(color) {
                this.drawLineStrip(relativeToCamera(from).toVec3(), relativeToCamera(to).toVec3())
            }
        }
    }

    open class DebuggedBox(val box: Box, color: Color4b) : DebuggedGeometry(color) {
        override fun render(env: WorldRenderEnvironment) {
            env.withColor(color) {
                this.drawSolidBox(box.offset(env.camera.pos.negate()))
            }
        }
    }

    class DebuggedPoint(point: Vec3d, color: Color4b, size: Double = 0.2) : DebuggedBox(
        Box.of(point, size, size, size),
        color
    )

    class DebugCollection(val geometry: Collection<DebuggedGeometry>) : DebuggedGeometry(Color4b.WHITE) {
        override fun render(env: WorldRenderEnvironment) {
            this.geometry.forEach { it.render(env) }
        }
    }

    override fun disable() {
        // Might clean up some memory if we disable the module
        debuggedGeometry.clear()
        debugParameters.clear()
        super.disable()
    }

}
