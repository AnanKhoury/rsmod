package gg.rsmod.plugins.content.skills.woodcutting

import gg.rsmod.game.fs.def.ItemDef
import gg.rsmod.game.model.entity.DynamicObject
import gg.rsmod.game.model.entity.GameObject
import gg.rsmod.game.model.entity.Player
import gg.rsmod.game.model.item.Item
import gg.rsmod.game.model.item.ItemAttribute
import gg.rsmod.game.model.queue.QueueTask
import gg.rsmod.plugins.api.EquipmentType
import gg.rsmod.plugins.api.Skills
import gg.rsmod.plugins.api.cfg.Items
import gg.rsmod.plugins.api.ext.*

/**
 * @author Tom <rspsmods@gmail.com>
 *
 * Thanks to Stuart2
 * for the better axe usage
 *
 * Thanks to Hoax
 * for making the Infernal Axe work
 */

// TODO: fix for picking up charged axe doesn't use charges from axe if charges = 0.
// TODO: add in player.message("") for if charges is under amount to keep from checking all the time

object Woodcutting {

    val infernalaxe = Item(Items.INFERNAL_AXE)
    var charges = 5000
    data class Tree(val type: TreeType, val obj: Int, val trunk: Int)

    suspend fun chopDownTree(it: QueueTask, obj: GameObject, tree: TreeType, trunkId: Int) {

        val p = it.player
        val infernalaxe = Item(Items.INFERNAL_AXE)

        if (!canChop(p, obj, tree)) {
            return
        }

        val logName = p.world.definitions.get(ItemDef::class.java, tree.log).name
        val axe = AxeType.values.filter { p.getSkills().getMaxLevel(Skills.WOODCUTTING) >= it.level && (p.equipment.contains(it.item) || p.inventory.contains(it.item)) }.sortedBy { it.level }.last()

        p.filterableMessage("You swing your axe at the tree.")
        while (true) {

            p.animate(axe.animation)
            it.wait(2)

            if (!canChop(p, obj, tree)) {
                p.animate(-1)
                break
            }

            val level = p.getSkills().getCurrentLevel(Skills.WOODCUTTING)
            if (level.interpolate(minChance = 60, maxChance = 190, minLvl = 1, maxLvl = 99, cap = 255)) {
                p.filterableMessage("You get some ${logName.pluralSuffix(2)}.")
                p.playSound(3600)
                p.inventory.add(tree.log)
                p.addXp(Skills.WOODCUTTING, tree.xp)

                val chanceOfBurningLogOnCut = (1..3).random()
                if (axe.item == Items.INFERNAL_AXE && p.getSkills().getMaxLevel(Skills.FIREMAKING) >= 85 && chanceOfBurningLogOnCut == 3 && charges > 0) {
                    p.inventory.remove(tree.log)
                    charges--
                    if (charges == 0) {
                        if (p.hasEquipped(EquipmentType.WEAPON, Items.INFERNAL_AXE)) {
                            p.equipment.remove(Items.INFERNAL_AXE)
                            p.inventory.add(Items.INFERNAL_AXE_UNCHARGED)
                        } else {
                            val removeAxe = p.inventory.remove(Items.INFERNAL_AXE, 1)
                            if (removeAxe.hasSucceeded()) {
                                p.inventory.add(Items.INFERNAL_AXE_UNCHARGED)
                            }
                        }
                        if (p.inventory.contains(Items.INFERNAL_AXE) || p.hasEquipped(EquipmentType.WEAPON, Items.INFERNAL_AXE)) {
                            charges = 5000
                        }
                    }
                    infernalaxe.putAttr(ItemAttribute.CHARGES, charges)
                    it.player.graphic(id = 86, height = 2)
                    p.addXp(Skills.FIREMAKING, tree.burnXp)
                }

                if (p.world.random(tree.depleteChance) == 0) {
                    p.animate(-1)

                    if (trunkId != -1) {
                        val world = p.world
                        world.queue {
                            val trunk = DynamicObject(obj, trunkId)
                            world.remove(obj)
                            world.spawn(trunk)
                            wait(tree.respawnTime.random())
                            world.remove(trunk)
                            world.spawn(DynamicObject(obj))
                        }
                    }
                    break
                }
            }
            it.wait(2)
        }
    }

    private fun canChop(p: Player, obj: GameObject, tree: TreeType): Boolean {
        if (!p.world.isSpawned(obj)) {
            return false
        }

        val axe = AxeType.values.firstOrNull { p.getSkills().getMaxLevel(Skills.WOODCUTTING) >= it.level && (p.equipment.contains(it.item) || p.inventory.contains(it.item)) }
        if (axe == null) {
            p.message("You need an axe to chop down this tree.")
            p.message("You do not have an axe which you have the woodcutting level to use.")
            return false
        }

        if (p.getSkills().getMaxLevel(Skills.WOODCUTTING) < tree.level) {
            p.message("You need a Woodcutting level of ${tree.level} to chop down this tree.")
            return false
        }

        if (p.inventory.isFull) {
            p.message("Your inventory is too full to hold any more logs.")
            return false
        }

        return true
    }

    fun createAxe(player: Player) {
        player.inventory.remove(Items.DRAGON_AXE)
        player.inventory.remove(Items.SMOULDERING_STONE)

        player.inventory.add(infernalaxe).items.forEach {
            infernalaxe.putAttr(ItemAttribute.CHARGES, charges)
        }

        player.animate(id = 4511, delay = 2)
        player.graphic(id = 1240, height = 2)

        player.addXp(Skills.FIREMAKING, 350.0)
        player.addXp(Skills.WOODCUTTING, 200.0)
    }

    fun checkCharges(p: Player) {
        infernalaxe.putAttr(ItemAttribute.CHARGES, charges)
        p.message("Your infernal axe currently has ${infernalaxe.getAttr(ItemAttribute.CHARGES)} charges left.")
    }
}