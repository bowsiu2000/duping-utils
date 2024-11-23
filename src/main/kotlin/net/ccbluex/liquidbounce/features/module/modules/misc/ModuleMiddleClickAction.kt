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

import net.ccbluex.liquidbounce.config.Choice
import net.ccbluex.liquidbounce.config.ChoiceConfigurable
import net.ccbluex.liquidbounce.event.events.NotificationEvent
import net.ccbluex.liquidbounce.event.events.WorldChangeEvent
import net.ccbluex.liquidbounce.event.handler
import net.ccbluex.liquidbounce.event.repeatable
import net.ccbluex.liquidbounce.features.misc.FriendManager
import net.ccbluex.liquidbounce.features.module.Category
import net.ccbluex.liquidbounce.features.module.Module
import net.ccbluex.liquidbounce.utils.aiming.facingEnemy
import net.ccbluex.liquidbounce.utils.aiming.raytraceEntity
import net.ccbluex.liquidbounce.utils.client.SilentHotbar
import net.ccbluex.liquidbounce.utils.client.notification
import net.ccbluex.liquidbounce.utils.entity.rotation
import net.ccbluex.liquidbounce.utils.inventory.useHotbarSlotOrOffhand
import net.ccbluex.liquidbounce.utils.item.findHotbarItemSlot
import net.minecraft.entity.player.PlayerEntity
import net.minecraft.item.Items

/**
 * MiddleClickAction module
 *
 * Allows you to perform actions with middle clicks.
 */
object ModuleMiddleClickAction : Module(
    "MiddleClickAction",
    Category.MISC,
    aliases = arrayOf("FriendClicker", "MiddleClickPearl")
) {

    private val mode = choices(this, "Mode", FriendClicker, arrayOf(FriendClicker, Pearl))

    override fun disable() {
        Pearl.disable()
    }

    object Pearl : Choice("Pearl") {

        private val slotResetDelay by int("SlotResetDelay", 1, 0..10, "ticks")

        private var wasPressed = false

        val repeatable = repeatable {
            if (mc.currentScreen != null) {
                wasPressed = false
                return@repeatable
            }

            val pickup = mc.options.pickItemKey.isPressed

            if (pickup) {
                // visually select the slot
                val slot = findHotbarItemSlot(Items.ENDER_PEARL)?.hotbarSlotForServer ?: return@repeatable
                SilentHotbar.selectSlotSilently(this, slot, slotResetDelay)
                wasPressed = true
            } else if (wasPressed) { // the key was released
                findHotbarItemSlot(Items.ENDER_PEARL)?.let {
                    useHotbarSlotOrOffhand(it, slotResetDelay)
                }

                wasPressed = false
            }
        }

        val handler = handler<WorldChangeEvent> {
            wasPressed = false
        }

        override fun disable() {
            wasPressed = false
        }

        fun cancelPick(): Boolean {
            return ModuleMiddleClickAction.enabled &&
                mode.activeChoice == this &&
                findHotbarItemSlot(Items.ENDER_PEARL) != null
        }

        override val parent: ChoiceConfigurable<*>
            get() = mode

    }

    object FriendClicker : Choice("FriendClicker") {

        private val pickUpRange by float("PickUpRange", 3.0f, 1f..100f)

        private var clicked = false

        val repeatable = repeatable {
            val rotation = player.rotation

            val entity = (raytraceEntity(pickUpRange.toDouble(), rotation) { it is PlayerEntity }
                ?: return@repeatable).entity as PlayerEntity

            val facesEnemy = facingEnemy(
                toEntity = entity, rotation = rotation, range = pickUpRange.toDouble(),
                wallsRange = 0.0
            )

            val pickup = mc.options.pickItemKey.isPressed

            if (facesEnemy && pickup && !clicked) {
                val name = entity.nameForScoreboard

                if (FriendManager.isFriend(name)) {
                    FriendManager.friends.remove(FriendManager.Friend(name, null))
                    notification(
                        "FriendClicker",
                        message("removedFriend", name),
                        NotificationEvent.Severity.INFO
                    )
                } else {
                    FriendManager.friends.add(FriendManager.Friend(name, null))

                    notification(
                        "FriendClicker",
                        message("addedFriend", name),
                        NotificationEvent.Severity.INFO
                    )
                }
            }

            clicked = pickup
        }

        override val parent: ChoiceConfigurable<*>
            get() = mode

    }

}