package com.saldolivre.app.data.model

data class SharedUser(
    val uid: String = "",
    val email: String = "",
    val name: String = ""
)

data class Card(
    val id: String = "",
    val ownerUid: String = "",
    val ownerName: String = "",
    val cardName: String = "",
    val closingDay: Int = 1,
    val dueDay: Int = 10,
    val sharedWithUsers: List<SharedUser> = emptyList(),
    val sharedWithUids: List<String> = emptyList(),
    val frequentThirdParties: List<String> = emptyList(),
    val color: String = "violet",
    val limit: Double? = null
)
