package fyi.goodbye.fridgy.models

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

@Parcelize
data class DisplayFridge(
    val id: String = "",
    val name: String = "",
    val createdByUid: String = "",
    val creatorDisplayName: String = "Unknown",
    val members: List<String> = listOf(),
    val createdAt: Long = System.currentTimeMillis()
) : Parcelable
