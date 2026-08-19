package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.ui.navigation.Screen
import com.example.ui.screens.EditorScreen
import com.example.ui.screens.FileManagerScreen
import com.example.ui.screens.HomeScreen
import com.example.ui.screens.SettingsScreen
import com.example.ui.screens.TemplatesScreen
import com.example.ui.theme.MyApplicationTheme

import androidx.compose.runtime.collectAsState
import androidx.compose.ui.platform.LocalContext
import com.example.ui.theme.AppThemeMode
import com.example.ui.theme.ThemeManager

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val context = LocalContext.current
            val themeManager = remember { ThemeManager(context) }
            val appTheme by themeManager.appThemeFlow.collectAsState(initial = AppThemeMode.SYSTEM)
            
            val isDarkTheme = when (appTheme) {
                AppThemeMode.LIGHT -> false
                AppThemeMode.DARK -> true
                AppThemeMode.SYSTEM -> isSystemInDarkTheme()
            }
            
            MyApplicationTheme(darkTheme = isDarkTheme) {
                MainApp()
            }
        }
    }
}

@Composable
fun MainApp() {
    val navController = rememberNavController()
    val screens = listOf(
        Screen.Home,
        Screen.Editor,
        Screen.Templates,
        Screen.Settings
    )

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        bottomBar = {
            NavigationBar {
                val navBackStackEntry by navController.currentBackStackEntryAsState()
                val currentDestination = navBackStackEntry?.destination

                screens.forEach { screen ->
                    NavigationBarItem(
                        icon = { Icon(screen.icon, contentDescription = screen.title) },
                        label = { Text(screen.title) },
                        selected = currentDestination?.hierarchy?.any { it.route == screen.route } == true,
                        onClick = {
                            navController.navigate(screen.route) {
                                popUpTo(navController.graph.findStartDestination().id) {
                                    saveState = true
                                }
                                launchSingleTop = true
                                restoreState = true
                            }
                        }
                    )
                }
            }
        }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = Screen.Home.route,
            modifier = Modifier.padding(innerPadding)
        ) {
            navController.addOnDestinationChangedListener { _, destination, _ ->
                com.example.ui.utils.AppLogger.i("Navigation", "Navigated to ${destination.route}")
            }
            
            composable(Screen.Home.route) {
                HomeScreen(
                    onNavigateToSettings = {
                        navController.navigate(Screen.Settings.route) {
                            launchSingleTop = true
                            restoreState = true
                        }
                    },
                    onNavigateToEditor = { fileName ->
                        navController.navigate(Screen.Editor.createRoute(fileName)) {
                            launchSingleTop = true
                            restoreState = true
                        }
                    },
                    onNavigateToFileManager = {
                        navController.navigate(Screen.FileManager.route) {
                            launchSingleTop = true
                            restoreState = true
                        }
                    },
                    onNavigateToTemplates = {
                        navController.navigate(Screen.Templates.route) {
                            launchSingleTop = true
                            restoreState = true
                        }
                    }
                )
            }
            composable(
                route = Screen.Editor.route,
                arguments = listOf(navArgument("fileName") { nullable = true })
            ) { backStackEntry ->
                val fileName = backStackEntry.arguments?.getString("fileName")
                EditorScreen(
                    fileName = fileName,
                    onNavigateBack = {
                        navController.popBackStack()
                    }
                )
            }
            composable(Screen.FileManager.route) {
                FileManagerScreen(
                    onFileSelected = { selectedFile ->
                        navController.navigate(Screen.Editor.createRoute(selectedFile))
                    }
                )
            }
            composable(Screen.Templates.route) {
                val context = LocalContext.current
                TemplatesScreen(
                    onUseTemplate = { fileName, code ->
                        val fm = com.example.ui.utils.FileManager(context)
                        fm.saveFile(fileName, code)
                        navController.navigate(Screen.Editor.createRoute(fileName)) {
                            popUpTo(Screen.Home.route) { saveState = true }
                            launchSingleTop = true
                            restoreState = true
                        }
                    }
                )
            }
            composable(Screen.Settings.route) { 
                SettingsScreen(
                    onNavigateToLogs = {
                        navController.navigate(Screen.Logs.route) {
                            launchSingleTop = true
                            restoreState = true
                        }
                    }
                ) 
            }
            composable(Screen.Logs.route) {
                com.example.ui.screens.LogsScreen(
                    onNavigateBack = { navController.popBackStack() }
                )
            }
        }
    }
}
