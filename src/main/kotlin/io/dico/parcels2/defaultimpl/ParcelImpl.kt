package io.dico.parcels2.defaultimpl

import io.dico.dicore.Formatting
import io.dico.parcels2.*
import io.dico.parcels2.util.Vec2i
import io.dico.parcels2.util.alsoIfTrue
import org.bukkit.OfflinePlayer
import org.joda.time.DateTime
import kotlin.reflect.KProperty

class ParcelImpl(override val world: ParcelWorld,
                 override val x: Int,
                 override val z: Int) : Parcel, ParcelId {
    override val id: ParcelId = this
    override val pos get() = Vec2i(x, z)
    override var data: ParcelDataHolder = ParcelDataHolder(); private set
    override val infoString by ParcelInfoStringComputer
    override var hasBlockVisitors: Boolean = false; private set
    override val worldId: ParcelWorldId get() = world.id

    override fun copyDataIgnoringDatabase(data: ParcelData) {
        this.data = ((data as? Parcel)?.data ?: data) as ParcelDataHolder
    }

    override fun copyData(data: ParcelData) {
        copyDataIgnoringDatabase(data)
        world.storage.setParcelData(this, data)
    }

    override fun dispose() {
        copyDataIgnoringDatabase(ParcelDataHolder())
        world.storage.setParcelData(this, null)
    }

    override val addedMap: AddedDataMap get() = data.addedMap
    override fun getAddedStatus(key: StatusKey) = data.getAddedStatus(key)
    override fun isBanned(key: StatusKey) = data.isBanned(key)
    override fun isAllowed(key: StatusKey) = data.isAllowed(key)
    override fun canBuild(player: OfflinePlayer, checkAdmin: Boolean, checkGlobal: Boolean): Boolean {
        return (data.canBuild(player, checkAdmin, false))
            || checkGlobal && world.globalAddedData[owner ?: return false].isAllowed(player)
    }

    override var addedStatusOfStar: AddedStatus
        get() = data.addedStatusOfStar
        set(value) = run { setAddedStatus(PlayerProfile.Star, value) }

    val globalAddedMap: AddedDataMap? get() = owner?.let { world.globalAddedData[it].addedMap }

    override val since: DateTime? get() = data.since

    override var owner: PlayerProfile?
        get() = data.owner
        set(value) {
            if (data.owner != value) {
                world.storage.setParcelOwner(this, value)
                data.owner = value
            }
        }

    override fun setAddedStatus(key: StatusKey, status: AddedStatus): Boolean {
        return data.setAddedStatus(key, status).alsoIfTrue {
            world.storage.setParcelPlayerStatus(this, key, status)
        }
    }

    override var allowInteractInputs: Boolean
        get() = data.allowInteractInputs
        set(value) {
            if (data.allowInteractInputs == value) return
            world.storage.setParcelAllowsInteractInputs(this, value)
            data.allowInteractInputs = value
        }

    override var allowInteractInventory: Boolean
        get() = data.allowInteractInventory
        set(value) {
            if (data.allowInteractInventory == value) return
            world.storage.setParcelAllowsInteractInventory(this, value)
            data.allowInteractInventory = value
        }


}

private object ParcelInfoStringComputer {
    val infoStringColor1 = Formatting.GREEN
    val infoStringColor2 = Formatting.AQUA

    private inline fun StringBuilder.appendField(field: StringBuilder.() -> Unit, value: StringBuilder.() -> Unit) {
        append(infoStringColor1)
        field()
        append(": ")
        append(infoStringColor2)
        value()
        append(' ')
    }

    private inline fun StringBuilder.appendField(name: String, value: StringBuilder.() -> Unit) {
        append(infoStringColor1)
        append(name)
        append(": ")
        append(infoStringColor2)
        value()
        append(' ')
    }

    private fun StringBuilder.appendAddedList(local: AddedDataMap, global: AddedDataMap, status: AddedStatus, fieldName: String) {
        val globalSet = global.filterValues { it == status }.keys
        val localList = local.filterValues { it == status }.keys.filter { it !in globalSet }
        val stringList = globalSet.map(StatusKey::notNullName).map { "(G)$it" } + localList.map(StatusKey::notNullName)
        if (stringList.isEmpty()) return

        appendField({
            append(fieldName)
            append('(')
            append(infoStringColor2)
            append(stringList.size)
            append(infoStringColor1)
            append(')')
        }) {
            stringList.joinTo(this,
                separator = infoStringColor1.toString() + ", " + infoStringColor2,
                limit = 150)
        }
    }

    operator fun getValue(parcel: Parcel, property: KProperty<*>): String = buildString {
        appendField("ID") {
            append(parcel.x)
            append(',')
            append(parcel.z)
        }

        val owner = parcel.owner
        appendField("Owner") {
            if (owner == null) {
                append(infoStringColor1)
                append("none")
            } else {
                append(owner.notNullName)
            }
        }

        // plotme appends biome here

        append('\n')

        val global = owner?.let { parcel.world.globalAddedData[owner].addedMap } ?: emptyMap()
        val local = parcel.addedMap
        appendAddedList(local, global, AddedStatus.ALLOWED, "Allowed")
        append('\n')
        appendAddedList(local, global, AddedStatus.BANNED, "Banned")

        if (!parcel.allowInteractInputs || !parcel.allowInteractInventory) {
            appendField("Options") {
                append("(")
                appendField("inputs") { append(parcel.allowInteractInputs) }
                append(", ")
                appendField("inventory") { append(parcel.allowInteractInventory) }
                append(")")
            }
        }

    }
}