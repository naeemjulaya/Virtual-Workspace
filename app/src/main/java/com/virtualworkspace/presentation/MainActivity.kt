package com.virtualworkspace.presentation

import android.os.Bundle
import android.os.Build
import android.content.pm.PackageManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.Composable
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import android.content.Intent
import android.provider.DocumentsContract
import com.virtualworkspace.presentation.browser.BrowserScreen
import com.virtualworkspace.presentation.search.SearchScreen
import com.virtualworkspace.presentation.settings.SettingsScreen
import com.virtualworkspace.presentation.theme.VirtualWorkspaceTheme
import com.virtualworkspace.presentation.trash.TrashScreen
import com.virtualworkspace.presentation.workspaces.WorkspaceListScreen
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(android.Manifest.permission.POST_NOTIFICATIONS), 100)
        }
        setContent {
            VirtualWorkspaceTheme {
                AppNavHost()
            }
        }
    }
}

@Composable
private fun AppNavHost() {
    val navController = rememberNavController()

    NavHost(navController = navController, startDestination = "workspaces") {
        composable("workspaces") {
            WorkspaceListScreen(
                onOpenWorkspace = { id -> navController.navigate("browser/$id") },
                onOpenSettings = { navController.navigate("settings") }
            )
        }
        composable(
            route = "browser/{workspaceId}?folderId={folderId}",
            arguments = listOf(
                navArgument("workspaceId") { type = NavType.LongType },
                navArgument("folderId") { type = NavType.LongType; defaultValue = -1L }
            )
        ) { backStackEntry ->
            val workspaceId = backStackEntry.arguments?.getLong("workspaceId") ?: return@composable
            BrowserScreen(
                onNavigateSearch = { navController.navigate("search/$workspaceId") },
                onNavigateBack = { navController.popBackStack() }
            )
        }
        composable(
            route = "search/{workspaceId}",
            arguments = listOf(navArgument("workspaceId") { type = NavType.LongType })
        ) {
            SearchScreen(
                onNavigateBack = { navController.popBackStack() },
                onOpenNode = { item ->
                    if (item.node.type == com.virtualworkspace.domain.model.NodeType.FILE_REFERENCE) {
                        val uri = DocumentsContract.buildDocumentUri(
                            "com.virtualworkspace.documents", "n${item.node.id}"
                        )
                        val intent = Intent(Intent.ACTION_VIEW).apply {
                            setDataAndType(uri, item.storageObject?.mimeType ?: "*/*")
                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        }
                        runCatching { navController.context.startActivity(intent) }
                    } else {
                        navController.navigate("browser/${item.node.workspaceId}?folderId=${item.node.id}")
                    }
                }
            )
        }
        composable("settings") {
            SettingsScreen(
                onNavigateBack = { navController.popBackStack() },
                onOpenTrash = { navController.navigate("trash") }
            )
        }
        composable("trash") {
            TrashScreen(onNavigateBack = { navController.popBackStack() })
        }
    }
}
