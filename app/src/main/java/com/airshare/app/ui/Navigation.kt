package com.airshare.app.ui

import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController

@Composable
fun AirShareNavHost(
    viewModel: MainViewModel,
    onFolderPicked: () -> Unit
) {
    val navController = rememberNavController()
    val context = LocalContext.current

    NavHost(navController = navController, startDestination = "main") {
        composable("main") {
            MainScreen(
                viewModel = viewModel,
                onSettings = { navController.navigate("settings") },
                onShareQr = {
                    val state = viewModel.serverState.value
                    if (state is com.airshare.app.server.ServerState.Running) {
                        val intent = Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(Intent.EXTRA_TEXT, "http://${state.ip}:${state.port}")
                        }
                        context.startActivity(Intent.createChooser(intent, "Share server address"))
                    }
                },
                onFolderPicked = onFolderPicked
            )
        }

        composable("settings") {
            SettingsScreen(
                viewModel = viewModel,
                onBack = { navController.popBackStack() }
            )
        }
    }
}
