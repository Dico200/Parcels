package io.dico.parcels2.command

import io.dico.dicore.command.parameter.ArgumentBuffer
import io.dico.dicore.command.parameter.Parameter
import io.dico.dicore.command.parameter.type.ParameterConfig
import io.dico.dicore.command.parameter.type.ParameterType
import io.dico.parcels2.ParcelWorld
import io.dico.parcels2.Worlds
import org.bukkit.Bukkit
import org.bukkit.OfflinePlayer
import org.bukkit.command.CommandSender
import org.bukkit.entity.Player

class NamedParcelTarget(val world: ParcelWorld, val player: OfflinePlayer, val index: Int)

@Target(AnnotationTarget.VALUE_PARAMETER)
@Retention(AnnotationRetention.RUNTIME)
annotation class NamedParcelDefault(val value: NamedParcelDefaultValue)

enum class NamedParcelDefaultValue {
    FIRST_OWNED,
    NULL
}

class NamedParcelTargetConfig : ParameterConfig<NamedParcelDefault,
    NamedParcelDefaultValue>(NamedParcelDefault::class.java) {

    override fun toParameterInfo(annotation: NamedParcelDefault): NamedParcelDefaultValue {
        return annotation.value
    }
}

class ParcelHomeParameterType(val worlds: Worlds) : ParameterType<NamedParcelTarget,
    NamedParcelDefaultValue>(NamedParcelTarget::class.java, NamedParcelTargetConfig()) {

    val regex = Regex.fromLiteral("((.+)->)?(.+)|((.+):([0-9]+))")

    private fun requirePlayer(sender: CommandSender, parameter: Parameter<*, *>): Player {
        if (sender !is Player) invalidInput(parameter, "console cannot omit the player name")
        return sender
    }

    @Suppress("UsePropertyAccessSyntax")
    private fun getOfflinePlayer(input: String, parameter: Parameter<*, *>) = Bukkit.getOfflinePlayer(input)
        ?.takeIf { it.isOnline() || it.hasPlayedBefore() }
        ?: invalidInput(parameter, "do not know who $input is")

    override fun parse(parameter: Parameter<NamedParcelTarget, NamedParcelDefaultValue>,
                       sender: CommandSender, buffer: ArgumentBuffer): NamedParcelTarget {
        val matchResult = regex.matchEntire(buffer.next())
            ?: invalidInput(parameter, "must be a player, index, or player:index (/${regex.pattern}/)")

        val world = worlds.getTargetWorld(matchResult.groupValues[2], sender, parameter)

        matchResult.groupValues[3].takeUnless { it.isEmpty() }?.let {
            // first group was matched, it's a player or an int
            it.toIntOrNull()?.let {
                requirePlayer(sender, parameter)
                return NamedParcelTarget(world, sender as Player, it)
            }

            return NamedParcelTarget(world, getOfflinePlayer(it, parameter), 0)
        }

        val player = getOfflinePlayer(matchResult.groupValues[5], parameter)
        val index = matchResult.groupValues[6].toIntOrNull()
            ?: invalidInput(parameter, "couldn't parse int")

        return NamedParcelTarget(world, player, index)
    }

    override fun getDefaultValue(parameter: Parameter<NamedParcelTarget, NamedParcelDefaultValue>,
                                 sender: CommandSender, buffer: ArgumentBuffer): NamedParcelTarget? {
        if (parameter.paramInfo == NamedParcelDefaultValue.NULL) {
            return null
        }

        val world = worlds.getTargetWorld(null, sender, parameter)
        val player = requirePlayer(sender, parameter)
        return NamedParcelTarget(world, player, 0)
    }

}
