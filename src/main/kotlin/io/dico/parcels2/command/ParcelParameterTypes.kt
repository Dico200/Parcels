package io.dico.parcels2.command

import io.dico.dicore.command.CommandException
import io.dico.dicore.command.parameter.ArgumentBuffer
import io.dico.dicore.command.parameter.Parameter
import io.dico.dicore.command.parameter.type.ParameterType
import io.dico.parcels2.Parcel
import io.dico.parcels2.ParcelProvider
import io.dico.parcels2.ParcelWorld
import org.bukkit.command.CommandSender
import org.bukkit.entity.Player

fun invalidInput(parameter: Parameter<*, *>, message: String): Nothing {
    throw CommandException("invalid input for ${parameter.name}: $message")
}

fun ParcelProvider.getTargetWorld(input: String?, sender: CommandSender, parameter: Parameter<*, *>): ParcelWorld {
    val worldName = input
        ?.takeUnless { it.isEmpty() }
        ?: (sender as? Player)?.world?.name
        ?: invalidInput(parameter, "console cannot omit the world name")

    return getWorld(worldName)
        ?: invalidInput(parameter, "$worldName is not a parcel world")
}

class ParcelParameterType(val parcelProvider: ParcelProvider) : ParameterType<Parcel, Void>(Parcel::class.java) {
    val regex = Regex.fromLiteral("((.+)->)?([0-9]+):([0-9]+)")

    override fun parse(parameter: Parameter<Parcel, Void>, sender: CommandSender, buffer: ArgumentBuffer): Parcel {
        val matchResult = regex.matchEntire(buffer.next())
            ?: invalidInput(parameter, "must match (w->)?a:b (/${regex.pattern}/)")

        val world = parcelProvider.getTargetWorld(matchResult.groupValues[2], sender, parameter)

        val x = matchResult.groupValues[3].toIntOrNull()
            ?: invalidInput(parameter, "couldn't parse int")

        val z = matchResult.groupValues[4].toIntOrNull()
            ?: invalidInput(parameter, "couldn't parse int")

        return world.getParcelById(x, z)
            ?: invalidInput(parameter, "parcel id is out of range")
    }

}
