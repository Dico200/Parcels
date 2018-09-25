package io.dico.parcels2.command

import io.dico.dicore.command.CommandException
import io.dico.dicore.command.EMessageType
import io.dico.dicore.command.ExecutionContext
import io.dico.dicore.command.annotation.Cmd
import io.dico.parcels2.ParcelsPlugin
import io.dico.parcels2.Privilege
import io.dico.parcels2.blockvisitor.RegionTraverser
import io.dico.parcels2.doBlockOperation
import org.bukkit.Bukkit
import org.bukkit.Material
import org.bukkit.block.BlockFace
import org.bukkit.block.data.Directional
import org.bukkit.entity.Player
import java.util.Random

class CommandsDebug(plugin: ParcelsPlugin) : AbstractParcelCommands(plugin) {

    @Cmd("reloadoptions")
    fun reloadOptions() {
        plugin.loadOptions()
    }

    @Cmd("tpworld")
    fun tpWorld(sender: Player, worldName: String): String {
        if (worldName == "list") {
            return Bukkit.getWorlds().joinToString("\n- ", "- ", "")
        }
        val world = Bukkit.getWorld(worldName) ?: throw CommandException("World $worldName is not loaded")
        sender.teleport(world.spawnLocation)
        return "Teleported you to $worldName spawn"
    }

    @Cmd("make_mess")
    @RequireParcelPrivilege(Privilege.OWNER)
    fun ParcelScope.cmdMakeMess(context: ExecutionContext) {
        val server = plugin.server
        val blockDatas = arrayOf(
            server.createBlockData(Material.BLUE_WOOL),
            server.createBlockData(Material.LIME_WOOL),
            server.createBlockData(Material.GLASS),
            server.createBlockData(Material.STONE_SLAB),
            server.createBlockData(Material.STONE),
            server.createBlockData(Material.QUARTZ_BLOCK),
            server.createBlockData(Material.BROWN_CONCRETE)
        )
        val random = Random()

        world.blockManager.doBlockOperation(parcel.id, traverser = RegionTraverser.upward) { block ->
            block.blockData = blockDatas[random.nextInt(7)]
        }.onProgressUpdate(1000, 1000) { progress, elapsedTime ->
            context.sendMessage(
                EMessageType.INFORMATIVE, "Mess progress: %.02f%%, %.2fs elapsed"
                    .format(progress * 100, elapsedTime / 1000.0)
            )
        }
    }

    @Cmd("directionality", aliases = ["dir"])
    fun cmdDirectionality(sender: Player, context: ExecutionContext, material: Material): Any? {
        val senderLoc = sender.location
        val block = senderLoc.add(senderLoc.direction.setY(0).normalize().multiply(2).toLocation(sender.world)).block

        val blockData = Bukkit.createBlockData(material)
        if (blockData is Directional) {
            blockData.facing = BlockFace.SOUTH
        }

        block.blockData = blockData
        return if (blockData is Directional) "The block is facing south" else "The block is not directional, however it implements " +
            blockData.javaClass.interfaces!!.contentToString()
    }

}