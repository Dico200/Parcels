@file:Suppress("NOTHING_TO_INLINE")

package io.dico.parcels2.storage

import io.dico.parcels2.*
import kotlinx.coroutines.experimental.Deferred
import kotlinx.coroutines.experimental.Job
import kotlinx.coroutines.experimental.channels.ReceiveChannel
import kotlinx.coroutines.experimental.channels.SendChannel
import kotlinx.coroutines.experimental.launch
import java.util.UUID

typealias DataPair = Pair<ParcelId, ParcelData?>
typealias AddedDataPair<TAttach> = Pair<TAttach, MutableAddedDataMap>

interface Storage {
    val name: String
    val isConnected: Boolean

    fun init(): Job

    fun shutdown(): Job


    fun getPlayerUuidForName(name: String): Deferred<UUID?>

    fun readParcelData(parcel: ParcelId): Deferred<ParcelData?>

    fun transmitParcelData(parcels: Sequence<ParcelId>): ReceiveChannel<DataPair>

    fun transmitAllParcelData(): ReceiveChannel<DataPair>

    fun getOwnedParcels(user: PlayerProfile): Deferred<List<ParcelId>>

    fun getNumParcels(user: PlayerProfile): Deferred<Int>


    fun setParcelData(parcel: ParcelId, data: ParcelData?): Job

    fun setParcelOwner(parcel: ParcelId, owner: PlayerProfile?): Job

    fun setParcelPlayerStatus(parcel: ParcelId, player: PlayerProfile, status: AddedStatus): Job

    fun setParcelAllowsInteractInventory(parcel: ParcelId, value: Boolean): Job

    fun setParcelAllowsInteractInputs(parcel: ParcelId, value: Boolean): Job


    fun transmitAllGlobalAddedData(): ReceiveChannel<AddedDataPair<PlayerProfile>>

    fun readGlobalAddedData(owner: PlayerProfile): Deferred<MutableAddedDataMap?>

    fun setGlobalAddedStatus(owner: PlayerProfile, player: PlayerProfile, status: AddedStatus): Job


    fun getChannelToUpdateParcelData(): SendChannel<Pair<ParcelId, ParcelData>>
}

class BackedStorage internal constructor(val b: Backing) : Storage {
    override val name get() = b.name
    override val isConnected get() = b.isConnected

    override fun init() = launch(b.dispatcher) { b.init() }

    override fun shutdown() = launch(b.dispatcher) { b.shutdown() }


    override fun getPlayerUuidForName(name: String): Deferred<UUID?> = b.launchFuture { b.getPlayerUuidForName(name) }

    override fun readParcelData(parcel: ParcelId) = b.launchFuture { b.readParcelData(parcel) }

    override fun transmitParcelData(parcels: Sequence<ParcelId>) = b.openChannel<DataPair> { b.transmitParcelData(it, parcels) }

    override fun transmitAllParcelData() = b.openChannel<DataPair> { b.transmitAllParcelData(it) }

    override fun getOwnedParcels(user: PlayerProfile) = b.launchFuture { b.getOwnedParcels(user) }

    override fun getNumParcels(user: PlayerProfile) = b.launchFuture { b.getNumParcels(user) }

    override fun setParcelData(parcel: ParcelId, data: ParcelData?) = b.launchJob { b.setParcelData(parcel, data) }

    override fun setParcelOwner(parcel: ParcelId, owner: PlayerProfile?) = b.launchJob { b.setParcelOwner(parcel, owner) }

    override fun setParcelPlayerStatus(parcel: ParcelId, player: PlayerProfile, status: AddedStatus) = b.launchJob { b.setLocalPlayerStatus(parcel, player, status) }

    override fun setParcelAllowsInteractInventory(parcel: ParcelId, value: Boolean) = b.launchJob { b.setParcelAllowsInteractInventory(parcel, value) }

    override fun setParcelAllowsInteractInputs(parcel: ParcelId, value: Boolean) = b.launchJob { b.setParcelAllowsInteractInputs(parcel, value) }


    override fun transmitAllGlobalAddedData(): ReceiveChannel<AddedDataPair<PlayerProfile>> = b.openChannel { b.transmitAllGlobalAddedData(it) }

    override fun readGlobalAddedData(owner: PlayerProfile): Deferred<MutableAddedDataMap?> = b.launchFuture { b.readGlobalAddedData(owner) }

    override fun setGlobalAddedStatus(owner: PlayerProfile, player: PlayerProfile, status: AddedStatus) = b.launchJob { b.setGlobalPlayerStatus(owner, player, status) }

    override fun getChannelToUpdateParcelData(): SendChannel<Pair<ParcelId, ParcelData>> = b.openChannelForWriting { b.setParcelData(it.first, it.second) }
}
