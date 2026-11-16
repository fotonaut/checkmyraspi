package de.mchwrk.checkmyraspi.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import de.mchwrk.checkmyraspi.CheckMyRaspiApp
import de.mchwrk.checkmyraspi.design.MchwrkTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val c = (application as CheckMyRaspiApp).controller
        if (savedInstanceState == null) c.cleanupOrphanPhotos()

        setContent {
            MchwrkTheme {
                val nav = rememberNavController()
                NavHost(navController = nav, startDestination = "overview") {
                    composable("overview") {
                        OverviewScreen(
                            c,
                            onOpen = { nav.navigate("device/$it") },
                            onAdd = { nav.navigate("edit") },
                        )
                    }
                    composable("device/{id}", arguments = listOf(navArgument("id") { type = NavType.StringType })) { e ->
                        val id = e.arguments?.getString("id").orEmpty()
                        DeviceScreen(
                            c, id,
                            onEdit = { nav.navigate("edit?id=$id") },
                            onBack = { nav.popBackStack() },
                        )
                    }
                    composable(
                        "edit?id={id}",
                        arguments = listOf(navArgument("id") { type = NavType.StringType; nullable = true; defaultValue = null }),
                    ) { e ->
                        val id = e.arguments?.getString("id")
                        EditScreen(
                            c, id,
                            // Neues Gerät → zurück in die Übersicht, wo es jetzt auftaucht.
                            onDone = { nav.popBackStack() },
                            onBack = { nav.popBackStack() },
                        )
                    }
                }
            }
        }
    }
}
