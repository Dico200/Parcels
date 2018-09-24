package io.dico.parcels2.command

import io.dico.dicore.command.CommandBuilder
import io.dico.dicore.command.ICommandAddress
import io.dico.dicore.command.ICommandDispatcher
import io.dico.dicore.command.registration.reflect.ReflectiveRegistration
import io.dico.parcels2.Interactables
import io.dico.parcels2.ParcelsPlugin
import io.dico.parcels2.logger
import java.util.LinkedList
import java.util.Queue

@Suppress("UsePropertyAccessSyntax")
fun getParcelCommands(plugin: ParcelsPlugin): ICommandDispatcher =
    with(CommandBuilder()) {
        setChatController(ParcelsChatController())
        addParameterType(false, ParcelParameterType(plugin.parcelProvider))
        addParameterType(false, ProfileParameterType())
        addParameterType(true, ParcelTarget.PType(plugin.parcelProvider))

        group("parcel", "plot", "plots", "p") {
            addRequiredPermission("parcels.command")
            registerCommands(CommandsGeneral(plugin))
            registerCommands(CommandsPrivilegesLocal(plugin))

            group("option", "opt", "o") {
                setGroupDescription(
                    "changes interaction options for this parcel",
                    "Sets whether players who are not allowed to",
                    "build here can interact with certain things."
                )

                group("interact", "i") {
                    val command = ParcelOptionsInteractCommand(plugin.parcelProvider)
                    Interactables.classesById.forEach {
                        addSubCommand(it.name, command)
                    }
                }
            }

            group("global", "g") {
                registerCommands(CommandsPrivilegesGlobal(plugin))
            }

            group("admin", "a") {
                registerCommands(CommandsAdmin(plugin))
            }

            if (!logger.isDebugEnabled) return@group

            group("debug", "d") {
                registerCommands(CommandsDebug(plugin))
            }
        }

        generateHelpAndSyntaxCommands()
        getDispatcher()
    }

inline fun CommandBuilder.group(name: String, vararg aliases: String, config: CommandBuilder.() -> Unit) {
    group(name, *aliases)
    config()
    parent()
}

private fun CommandBuilder.generateHelpAndSyntaxCommands(): CommandBuilder {
    generateCommands(dispatcher as ICommandAddress, "help", "syntax")
    return this
}

private fun generateCommands(address: ICommandAddress, vararg names: String) {
    val addresses: Queue<ICommandAddress> = LinkedList()
    addresses.offer(address)

    while (addresses.isNotEmpty()) {
        val cur = addresses.poll()
        addresses.addAll(cur.children.values.distinct())
        if (cur.hasCommand()) {
            ReflectiveRegistration.generateCommands(cur, names)
        }
    }
}
