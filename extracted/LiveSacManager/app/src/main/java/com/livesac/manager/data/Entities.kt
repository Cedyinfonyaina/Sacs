package com.livesac.manager.data

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.livesac.manager.domain.Bag
import com.livesac.manager.domain.Client
import com.livesac.manager.domain.ClientRow
import com.livesac.manager.domain.DeliveryDay
import com.livesac.manager.domain.LiveInfo
import com.livesac.manager.domain.LiveKind

/** Une session de vente. */
@Entity(tableName = "lives")
data class LiveEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    /** Date choisie, en millisecondes UTC à minuit. */
    val dateMillis: Long,
    /** Jour du Live : WED, SAT, SUN ou OTHER (voir LiveKind). Ajouté en version 2. */
    val kind: String = "OTHER"
)

/** Un client d'un Live. `nameKey` = nom en minuscules, pour détecter les doublons. */
@Entity(
    tableName = "clients",
    foreignKeys = [
        ForeignKey(
            entity = LiveEntity::class,
            parentColumns = ["id"],
            childColumns = ["liveId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("liveId"), Index(value = ["liveId", "nameKey"])]
)
data class ClientEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val liveId: Long,
    val name: String,
    val nameKey: String,
    /** Emballage terminé ? Ajouté en version 2. */
    val packed: Boolean = false,
    /** Jour de livraison : WED, SAT, SUN ou null. Ajouté en version 2. */
    val deliveryDay: String? = null
)

/** Un sac acheté par un client, avec son code JP et son prix en Ariary. */
@Entity(
    tableName = "bags",
    foreignKeys = [
        ForeignKey(
            entity = ClientEntity::class,
            parentColumns = ["id"],
            childColumns = ["clientId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("clientId")]
)
data class BagEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val clientId: Long,
    val jpCode: String,
    val price: Long
)

fun ClientEntity.toModel() = Client(id = id, name = name)

fun BagEntity.toModel() = Bag(id = id, clientId = clientId, jpCode = jpCode, price = price)

fun LiveEntity.toInfo() = LiveInfo(id = id, title = title, dateMillis = dateMillis, kind = LiveKind.fromCode(kind))

fun ClientEntity.toRow() =
    ClientRow(id = id, liveId = liveId, name = name, packed = packed, deliveryDay = DeliveryDay.fromCode(deliveryDay))
