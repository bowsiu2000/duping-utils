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

import net.ccbluex.liquidbounce.features.module.Category
import net.ccbluex.liquidbounce.features.module.ClientModule

/**
 * AntiBlind module
 *
 * Protects you from potentially annoying screen effects that block your view.
 */
object ModuleAntiBlind : ClientModule("AntiBlind", Category.RENDER) {
    val antiBlind by boolean("DisableBlindingEffect", true)
    val antiDarkness by boolean("DisableDarknessEffect", true)
    val antiNausea by boolean("DisableNauseaEffect", true)
    val pumpkinBlur by boolean("DisablePumpkinBlur", true)
    val liquidsFog by boolean("DisableLiquidsFog", true)
    val powderSnowFog by boolean("DisablePowderSnowFog", true)
    val floatingItems by boolean("DisableFloatingItems", true)
    val portalOverlay by boolean("DisablePortalOverlay", true)
    val wallOverlay by boolean("DisableWallOverlay", true)
    val fireOpacity by float("FireOpacity", 1.0F, 0.0F..1.0F)
    val bossBars by boolean("DisableBossBars", true)
    val explosionParticles by boolean("DisableExplosionParticles", false)
}
