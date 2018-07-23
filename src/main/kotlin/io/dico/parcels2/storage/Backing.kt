package io.dico.parcels2.storage

import io.dico.parcels2.Parcel
import io.dico.parcels2.ParcelData
import io.dico.parcels2.ParcelOwner
import kotlinx.coroutines.experimental.channels.ProducerScope
import java.util.*

interface Backing {

    val name: String

    suspend fun init()

    suspend fun shutdown()


    /**
     * This producer function is capable of constantly reading parcels from a potentially infinite sequence,
     * and provide parcel data for it as read from the database.
     */
    suspend fun ProducerScope<Pair<Parcel, ParcelData?>>.produceParcelData(parcels: Sequence<Parcel>)

    suspend fun readParcelData(parcelFor: Parcel): ParcelData?

    suspend fun getOwnedParcels(user: ParcelOwner): List<SerializableParcel>


    suspend fun setParcelData(parcelFor: Parcel, data: ParcelData?)

    suspend fun setParcelOwner(parcelFor: Parcel, owner: ParcelOwner?)

    suspend fun setParcelPlayerState(parcelFor: Parcel, player: UUID, state: Boolean?)

    suspend fun setParcelAllowsInteractInventory(parcel: Parcel, value: Boolean)

    suspend fun setParcelAllowsInteractInputs(parcel: Parcel, value: Boolean)

}