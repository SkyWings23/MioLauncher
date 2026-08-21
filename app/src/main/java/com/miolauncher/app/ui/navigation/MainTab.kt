package com.miolauncher.app.ui.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.ShoppingBag
import androidx.compose.ui.graphics.vector.ImageVector

enum class MainTab(
    val route: String,
    val label: String,
    val icon: ImageVector,
) {
    HOME("home", "主页", Icons.Filled.Home),
    DOWNLOAD("download", "下载", Icons.Filled.Download),
    RESOURCE("resource", "资源", Icons.Filled.ShoppingBag),
    PROFILE("profile", "我的", Icons.Filled.Person),
    MULTIPLAYER("multiplayer", "联机", Icons.Filled.Public),
}
