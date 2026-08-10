package com.storagerush.app.data.model

import android.net.Uri

/**
 * Represents one discovered media folder ("bucket" in MediaStore terms),
 * e.g. Camera, Screenshots, WhatsApp Images, Downloads.
 *
 * @param bucketId MediaStore's BUCKET_ID — the real unique key. Two folders
 *   can share the same [bucketName] (e.g. "Camera" on internal storage vs an
 *   SD card), so lookups/filters should always use bucketId, never the name.
 * @param bucketName Display name shown to the user. May have a disambiguating
 *   suffix appended (e.g. "Camera (SD Card)") if another bucket shares the
 *   same raw name.
 * @param itemCount Number of items in this bucket, used for default sort order.
 * @param thumbnailUri URI of the most recently added item in the bucket, used
 *   as the folder's preview thumbnail. Null if the bucket is somehow empty.
 * @param representativePath RELATIVE_PATH of the most recent item — used for
 *   default-bucket resolution (e.g. detecting the real Camera folder) and for
 *   disambiguating duplicate names.
 */
data class BucketInfo(
    val bucketId: Long,
    val bucketName: String,
    val itemCount: Int,
    val thumbnailUri: Uri?,
    val representativePath: String
)