package com.saldolivre.app.data.model

import com.google.gson.annotations.SerializedName

enum class FriendshipStatus(val value: String) {
    @SerializedName("pending")
    PENDING("pending"),

    @SerializedName("accepted")
    ACCEPTED("accepted"),

    @SerializedName("rejected")
    REJECTED("rejected");

    companion object {
        fun from(value: String?): FriendshipStatus =
            entries.find { it.value.equals(value, ignoreCase = true) } ?: PENDING
    }
}

/**
 * Representa um vínculo de amizade na coleção `friendships` do Cloud Firestore.
 */
data class Friendship(
    val id: String = "",
    val requesterUid: String = "",
    val requesterEmail: String = "",
    val requesterName: String = "",
    val recipientUid: String = "",
    val recipientEmail: String = "",
    val recipientName: String = "",
    val status: FriendshipStatus = FriendshipStatus.PENDING,
    val createdAt: Long = System.currentTimeMillis()
) {
    fun isBetween(uidA: String, uidB: String): Boolean =
        (requesterUid == uidA && recipientUid == uidB) || (requesterUid == uidB && recipientUid == uidA)

    fun otherUser(myUid: String): Pair<String, String> =
        if (requesterUid == myUid) Pair(recipientUid, recipientName) else Pair(requesterUid, requesterName)
}
