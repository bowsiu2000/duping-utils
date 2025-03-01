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
package net.ccbluex.liquidbounce.utils.combat

import it.unimi.dsi.fastutil.objects.ObjectDoublePair
import net.ccbluex.liquidbounce.config.ConfigSystem
import net.ccbluex.liquidbounce.config.types.Configurable
import net.ccbluex.liquidbounce.event.EventManager
import net.ccbluex.liquidbounce.event.events.AttackEntityEvent
import net.ccbluex.liquidbounce.features.module.modules.combat.criticals.ModuleCriticals
import net.ccbluex.liquidbounce.utils.client.*
import net.ccbluex.liquidbounce.utils.entity.squaredBoxedDistanceTo
import net.ccbluex.liquidbounce.utils.kotlin.component1
import net.ccbluex.liquidbounce.utils.kotlin.component2
import net.ccbluex.liquidbounce.utils.kotlin.toDouble
import net.minecraft.client.world.ClientWorld
import net.minecraft.entity.Entity
import net.minecraft.entity.LivingEntity
import net.minecraft.entity.attribute.EntityAttributes
import net.minecraft.entity.mob.Angerable
import net.minecraft.entity.mob.HostileEntity
import net.minecraft.entity.mob.Monster
import net.minecraft.entity.mob.WaterCreatureEntity
import net.minecraft.entity.passive.PassiveEntity
import net.minecraft.entity.player.PlayerEntity
import net.minecraft.network.packet.c2s.play.PlayerInteractEntityC2SPacket
import net.minecraft.sound.SoundEvents
import net.minecraft.util.Hand
import net.minecraft.util.math.Box
import net.minecraft.util.math.Vec3d
import net.minecraft.world.GameMode

/**
 * Global target configurable
 *
 * Modules can have their own enemy configurable if required. If not, they should use this as default.
 * Global enemy configurable can be used to configure which entities should be considered as a target.
 *
 * This can be adjusted by the .target command and the panel inside the ClickGUI.
 */
val combatTargetsConfigurable = TargetConfigurable("CombatTargets", false)
val visualTargetsConfigurable = TargetConfigurable("VisualTargets", true)

data class EntityTargetingInfo(val classification: EntityTargetClassification, val isFriend: Boolean) {
    companion object {
        val DEFAULT = EntityTargetingInfo(EntityTargetClassification.TARGET, false)
    }
}

enum class EntityTargetClassification {
    TARGET,
    INTERESTING,
    IGNORED
}

/**
 * Configurable to configure which entities and their state (like being dead) should be considered as a target
 */
class TargetConfigurable(
    name: String,
    isVisual: Boolean
) : Configurable(name) {
    // Players should be considered as a target
    var players by boolean("Players", true)

    // Hostile mobs (like skeletons and zombies) should be considered as a target
    var hostile by boolean("Hostile", true)

    // Angerable mobs (like wolfs) should be considered as a target
    val angerable by boolean("Angerable", true)

    // Water Creature mobs should be considered as a target
    val waterCreature by boolean("WaterCreature", true)

    // Passive mobs (like cows, pigs and so on) should be considered as a target
    var passive by boolean("Passive", false)

    // Invisible entities should be also considered as a target
    var invisible by boolean("Invisible", true)

    // Dead entities should NOT be considered as a target - but this is useful to bypass anti-cheats
    var dead by boolean("Dead", false)

    // Sleeping entities should NOT be considered as a target
    var sleeping by boolean("Sleeping", false)

    // Client friends should be also considered as target
    var friends by boolean("Friends", isVisual)

    init {
        ConfigSystem.root(this)
    }

    fun shouldAttack(entity: Entity): Boolean {
        val info = EntityTaggingManager.getTag(entity).targetingInfo

        return when {
            info.isFriend && !friends -> false
            info.classification == EntityTargetClassification.TARGET -> isInteresting(entity)
            else -> false
        }
    }

    fun shouldShow(entity: Entity): Boolean {
        val info = EntityTaggingManager.getTag(entity).targetingInfo

        return when {
            info.isFriend && !friends -> false
            info.classification != EntityTargetClassification.IGNORED -> isInteresting(entity)
            else -> false
        }
    }

    /**
     * Check if an entity is considered a target
     */
    private fun isInteresting(suspect: Entity): Boolean {
        // Check if the enemy is living and not dead (or ignore being dead)
        if (suspect !is LivingEntity || !(dead || suspect.isAlive)) {
            return false
        }

        // Check if enemy is invisible (or ignore being invisible)
        if (!invisible && suspect.isInvisible) {
            return false
        }

        // Check if enemy is a player and should be considered as a target
        return when (suspect) {
            is PlayerEntity -> when {
                suspect == mc.player -> false
                // Check if enemy is sleeping (or ignore being sleeping)
                suspect.isSleeping && !sleeping -> false
                else -> players
            }
            is WaterCreatureEntity -> waterCreature
            is PassiveEntity -> passive
            is HostileEntity, is Monster -> hostile
            is Angerable -> angerable
            else -> false
        }
    }

}

// Extensions
@JvmOverloads
fun Entity.shouldBeShown(enemyConf: TargetConfigurable = visualTargetsConfigurable) =
    enemyConf.shouldShow(this)
fun Entity.shouldBeAttacked(enemyConf: TargetConfigurable = combatTargetsConfigurable) =
    enemyConf.shouldAttack(this)

/**
 * Find the best enemy in the current world in a specific range.
 */
fun ClientWorld.findEnemy(
    range: ClosedFloatingPointRange<Float>,
    enemyConf: TargetConfigurable = combatTargetsConfigurable
) = findEnemies(range, enemyConf).minByOrNull { (_, distance) -> distance }?.key()

fun ClientWorld.findEnemies(
    range: ClosedFloatingPointRange<Float>,
    enemyConf: TargetConfigurable = combatTargetsConfigurable
): List<ObjectDoublePair<Entity>> {
    val squaredRange = (range.start * range.start..range.endInclusive * range.endInclusive).toDouble()

    return getEntitiesInCuboid(player.eyePos, squaredRange.endInclusive)
        .filter { it.shouldBeAttacked(enemyConf) }
        .map { ObjectDoublePair.of(it, it.squaredBoxedDistanceTo(player)) }
        .filter { (_, distance) -> distance in squaredRange }
}

fun ClientWorld.getEntitiesInCuboid(
    midPos: Vec3d,
    range: Double,
    predicate: (Entity) -> Boolean = { true }
): MutableList<Entity> {
    return getOtherEntities(null, Box(midPos.subtract(range, range, range),
        midPos.add(range, range, range)), predicate)
}

inline fun ClientWorld.getEntitiesBoxInRange(
    midPos: Vec3d,
    range: Double,
    crossinline predicate: (Entity) -> Boolean = { true }
): MutableList<Entity> {
    val rangeSquared = range * range

    return getEntitiesInCuboid(midPos, range) { predicate(it) && it.squaredBoxedDistanceTo(midPos) <= rangeSquared }
}

fun Entity.attack(swing: Boolean, keepSprint: Boolean = false) {
    EventManager.callEvent(AttackEntityEvent(this))

    with (player) {
        // Swing before attacking (on 1.8)
        if (swing && isOlderThanOrEqual1_8) {
            swingHand(Hand.MAIN_HAND)
        }

        network.sendPacket(PlayerInteractEntityC2SPacket.attack(this@attack, isSneaking))

        if (keepSprint) {
            var genericAttackDamage =
                if (this.isUsingRiptide) {
                    this.riptideAttackDamage
                } else {
                    getAttributeValue(EntityAttributes.ATTACK_DAMAGE).toFloat()
                }
            val damageSource = this.damageSources.playerAttack(this)
            var enchantAttackDamage = this.getDamageAgainst(this@attack, genericAttackDamage,
                damageSource) - genericAttackDamage

            val attackCooldown = this.getAttackCooldownProgress(0.5f)
            genericAttackDamage *= 0.2f + attackCooldown * attackCooldown * 0.8f
            enchantAttackDamage *= attackCooldown

            if (genericAttackDamage > 0.0f || enchantAttackDamage > 0.0f) {
                if (enchantAttackDamage > 0.0f) {
                    this.addEnchantedHitParticles(this@attack)
                }

                if (ModuleCriticals.wouldDoCriticalHit(true)) {
                    world.playSound(
                        null, x, y, z, SoundEvents.ENTITY_PLAYER_ATTACK_CRIT,
                        soundCategory, 1.0f, 1.0f
                    )
                    addCritParticles(this@attack)
                }
            }
        } else {
            if (interaction.currentGameMode != GameMode.SPECTATOR) {
                attack(this@attack)
            }
        }

        // Reset cooldown
        resetLastAttackedTicks()

        // Swing after attacking (on 1.9+)
        if (swing && !isOlderThanOrEqual1_8) {
            swingHand(Hand.MAIN_HAND)
        }
    }
}
