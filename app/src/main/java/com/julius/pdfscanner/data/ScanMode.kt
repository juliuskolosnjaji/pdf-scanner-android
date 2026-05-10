package com.julius.pdfscanner.data

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.ui.graphics.vector.ImageVector

enum class ScanMode(
    val label: String,
    val description: String,
    val icon: ImageVector
) {
    DOCUMENT(
        label = "Document",
        description = "Papers, forms, receipts",
        icon = Icons.Default.Description
    ),
    BOOK(
        label = "Book",
        description = "Open books — auto-splits two pages",
        icon = Icons.Default.MenuBook
    ),
    ID_CARD(
        label = "ID Card",
        description = "IDs and passports — scan front & back",
        icon = Icons.Default.Badge
    ),
    BUSINESS_CARD(
        label = "Business Card",
        description = "Extracts contact info to save",
        icon = Icons.Default.ContactPage
    ),
    WHITEBOARD(
        label = "Whiteboard",
        description = "Removes glare, boosts contrast",
        icon = Icons.Default.Dashboard
    )
}
