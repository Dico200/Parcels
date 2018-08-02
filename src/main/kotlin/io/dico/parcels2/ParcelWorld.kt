package io.dico.parcels2

import io.dico.parcels2.storage.Storage
import io.dico.parcels2.util.Vec2i
import io.dico.parcels2.util.floor
import org.bukkit.Location
import org.bukkit.World
import org.bukkit.block.Block
import org.bukkit.entity.Entity
import java.util.UUID

interface ParcelProvider {
    val worlds: Map<String, ParcelWorld>

    fun getWorldById(id: ParcelWorldId): ParcelWorld?

    fun getParcelById(id: ParcelId): Parcel?

    fun getWorld(name: String): ParcelWorld?

    fun getWorld(world: World): ParcelWorld? = getWorld(world.name)

    fun getWorld(block: Block): ParcelWorld? = getWorld(block.world)

    fun getWorld(loc: Location): ParcelWorld? = getWorld(loc.world)

    fun getWorld(entity: Entity): ParcelWorld? = getWorld(entity.location)

    fun getParcelAt(worldName: String, x: Int, z: Int): Parcel? = getWorld(worldName)?.locator?.getParcelAt(x, z)

    fun getParcelAt(world: World, x: Int, z: Int): Parcel? = getParcelAt(world.name, x, z)

    fun getParcelAt(world: World, vec: Vec2i): Parcel? = getParcelAt(world, vec.x, vec.z)

    fun getParcelAt(loc: Location): Parcel? = getParcelAt(loc.world, loc.x.floor(), loc.z.floor())

    fun getParcelAt(entity: Entity): Parcel? = getParcelAt(entity.location)

    fun getParcelAt(block: Block): Parcel? = getParcelAt(block.world, block.x, block.z)

    fun getWorldGenerator(worldName: String): ParcelGenerator?

    fun loadWorlds()
}

interface ParcelLocator {
    val world: World

    fun getParcelIdAt(x: Int, z: Int): ParcelId?

    fun getParcelAt(x: Int, z: Int): Parcel?

    fun getParcelAt(vec: Vec2i): Parcel? = getParcelAt(vec.x, vec.z)

    fun getParcelAt(loc: Location): Parcel? = getParcelAt(loc.x.floor(), loc.z.floor()).takeIf { loc.world == world }

    fun getParcelAt(entity: Entity): Parcel? = getParcelAt(entity.location).takeIf { entity.world == world }

    fun getParcelAt(block: Block): Parcel? = getParcelAt(block.x, block.z).takeIf { block.world == world }

}

typealias ParcelContainerFactory = (ParcelWorld) -> ParcelContainer

interface ParcelContainer {

    fun getParcelById(x: Int, z: Int): Parcel?

    fun getParcelById(id: Vec2i): Parcel? = getParcelById(id.x, id.z)

    fun nextEmptyParcel(): Parcel?

}

interface ParcelWorld : ParcelLocator, ParcelContainer, ParcelBlockManager {
    val id: ParcelWorldId
    val name: String
    val uid: UUID?
    val options: WorldOptions
    val generator: ParcelGenerator
    val storage: Storage
    val container: ParcelContainer
    val locator: ParcelLocator
    val blockManager: ParcelBlockManager
    val globalAddedData: GlobalAddedDataManager
}
