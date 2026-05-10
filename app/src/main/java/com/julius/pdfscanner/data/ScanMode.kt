package com.julius.pdfscanner.data

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.*
import androidx.compose.ui.graphics.vector.ImageVector

enum class ScanMode(
    val label: String,
    val description: String,
    val icon: ImageVector,
    val pageLimit: Int = 20
) {
    DOCUMENT("Document", "Papers, forms, receipts", Icons.Default.Description),
    BOOK("Book", "Open books — auto-splits two pages", Icons.AutoMirrored.Filled.MenuBook),
    ID_CARD("ID Card", "IDs and passports — scan front & back", Icons.Default.Badge, pageLimit = 2),
    BUSINESS_CARD("Business Card", "Extracts contact info to save", Icons.Default.ContactPage, pageLimit = 2),
    WHITEBOARD("Whiteboard", "Removes glare, boosts contrast", Icons.Default.Dashboard),
    BULK("Bulk Scan", "Fast scan up to 50 pages", Icons.Default.DynamicFeed, pageLimit = 50)
}
