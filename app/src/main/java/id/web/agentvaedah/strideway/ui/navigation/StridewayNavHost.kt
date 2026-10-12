package id.web.agentvaedah.strideway.ui.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.navArgument
import id.web.agentvaedah.strideway.ui.activity.ActivityDetailScreen
import id.web.agentvaedah.strideway.ui.coach.CoachKeysScreen
import id.web.agentvaedah.strideway.ui.coach.CoachScreen
import id.web.agentvaedah.strideway.ui.home.HomeScreen
import id.web.agentvaedah.strideway.ui.maps.MapsScreen
import id.web.agentvaedah.strideway.ui.record.RecordScreen
import id.web.agentvaedah.strideway.ui.theme.Palette
import id.web.agentvaedah.strideway.ui.you.DevicesScreen
import id.web.agentvaedah.strideway.ui.you.PrivacyZoneScreen
import id.web.agentvaedah.strideway.ui.you.YouScreen

private val tabRoutes = setOf(Routes.HOME, Routes.MAPS, Routes.RECORD, Routes.COACH, Routes.YOU)

@Composable
fun StridewayNavHost(navController: NavHostController, recording: Boolean) {
    val backStack by navController.currentBackStackEntryAsState()
    val route = backStack?.destination?.route

    fun selectTab(target: String) {
        navController.navigate(target) {
            popUpTo(navController.graph.findStartDestination().id) { saveState = true }
            launchSingleTop = true
            restoreState = true
        }
    }

    Scaffold(
        containerColor = Palette.Bg,
        bottomBar = {
            if (route in tabRoutes) BottomBar(route, recording, ::selectTab)
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            NavHost(navController, startDestination = Routes.HOME) {
                composable(Routes.HOME) {
                    HomeScreen(
                        onOpenActivity = { navController.navigate(Routes.activity(it)) },
                        onStartRecording = { selectTab(Routes.RECORD) },
                        onImport = { selectTab(Routes.MAPS) },
                    )
                }
                composable(Routes.MAPS) {
                    MapsScreen(
                        onNavigateRoute = { selectTab(Routes.RECORD) },
                        onOpenActivity = { navController.navigate(Routes.activity(it)) },
                        onOpenKeys = { navController.navigate(Routes.COACH_KEYS) },
                    )
                }
                composable(Routes.RECORD) {
                    RecordScreen(onOpenActivity = { navController.navigate(Routes.activity(it)) })
                }
                composable(Routes.COACH) {
                    CoachScreen(onOpenKeys = { navController.navigate(Routes.COACH_KEYS) })
                }
                composable(Routes.YOU) {
                    YouScreen(
                        onOpenDevices = { navController.navigate(Routes.DEVICES) },
                        onOpenPrivacy = { navController.navigate(Routes.PRIVACY) },
                        onOpenKeys = { navController.navigate(Routes.COACH_KEYS) },
                    )
                }
                composable(
                    Routes.ACTIVITY,
                    arguments = listOf(navArgument("id") { type = NavType.LongType }),
                ) {
                    ActivityDetailScreen(onBack = { navController.popBackStack() })
                }
                composable(Routes.COACH_KEYS) { CoachKeysScreen(onBack = { navController.popBackStack() }) }
                composable(Routes.DEVICES) { DevicesScreen(onBack = { navController.popBackStack() }) }
                composable(Routes.PRIVACY) { PrivacyZoneScreen(onBack = { navController.popBackStack() }) }
            }
        }
    }
}
