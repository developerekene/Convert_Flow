package com.devekene.konvertibles.ui.navigation

import kotlinx.serialization.Serializable

sealed interface AppDestination {
    @Serializable
    data object Convert : AppDestination

    @Serializable
    data object Tools : AppDestination

    @Serializable
    data object History : AppDestination

    @Serializable
    data object Settings : AppDestination

    @Serializable
    data class Preview(
        val filePath: String,
        val fileName: String,
        val extension: String
    ) : AppDestination
}
