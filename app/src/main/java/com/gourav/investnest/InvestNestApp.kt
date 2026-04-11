package com.gourav.investnest

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AccountBalanceWallet
import androidx.compose.material.icons.outlined.Explore
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.gourav.investnest.feature.category.CategoryScreenRoute
import com.gourav.investnest.feature.detail.FundDetailRoute
import com.gourav.investnest.feature.explore.ExploreScreenRoute
import com.gourav.investnest.feature.search.SearchScreenRoute
import com.gourav.investnest.feature.watchlist.WatchlistScreenRoute
import com.gourav.investnest.feature.watchlistdetail.WatchlistDetailRoute
import com.gourav.investnest.ui.theme.InvestNestTheme

private sealed class BottomDestination(
    val route: String,
    val label: String,
) {
    data object Explore : BottomDestination(
        route = "explore",
        label = "Explore",
    )

    data object Watchlists : BottomDestination(
        route = "watchlists",
        label = "Watchlist",
    )
}

private const val SearchRoute = "search"
private const val ViewAllRoute = "view_all/{categoryKey}"
private const val DetailRoute = "fund/{schemeCode}"
private const val WatchlistDetailRoute = "watchlist/{watchlistId}"

@Composable
fun InvestNestApp() {
    val navController = rememberNavController()
    val currentBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = currentBackStackEntry?.destination
    val bottomDestinations = listOf(
        BottomDestination.Explore,
        BottomDestination.Watchlists,
    )
    val showBottomBar = bottomDestinations.any { destination ->
        currentDestination?.hierarchy?.any { it.route == destination.route } == true
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            if (showBottomBar) {
                NavigationBar(
                    containerColor = MaterialTheme.colorScheme.surface,
                    tonalElevation = 0.dp,
                ) {
                    bottomDestinations.forEach { destination ->
                        val selected = currentDestination?.hierarchy?.any { it.route == destination.route } == true
                        NavigationBarItem(
                            selected = selected,
                            onClick = {
                                navController.navigate(destination.route) {
                                    popUpTo(navController.graph.findStartDestination().id) {
                                        saveState = true
                                    }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            icon = {
                                Icon(
                                    imageVector = when (destination) {
                                        BottomDestination.Explore -> Icons.Outlined.Explore
                                        BottomDestination.Watchlists -> Icons.Outlined.AccountBalanceWallet
                                    },
                                    contentDescription = destination.label,
                                )
                            },
                            label = { androidx.compose.material3.Text(destination.label) },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = MaterialTheme.colorScheme.primary,
                                selectedTextColor = MaterialTheme.colorScheme.primary,
                                indicatorColor = MaterialTheme.colorScheme.primaryContainer,
                                unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            ),
                        )
                    }
                }
            }
        },
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = BottomDestination.Explore.route,
            modifier = Modifier.padding(innerPadding),
        ) {
            composable(BottomDestination.Explore.route) {
                ExploreScreenRoute(
                    onSearchClick = { navController.navigate(SearchRoute) },
                    onViewAllClick = { category ->
                        navController.navigate("view_all/${category.key}")
                    },
                    onFundClick = { schemeCode ->
                        navController.navigate("fund/$schemeCode")
                    },
                )
            }
            composable(BottomDestination.Watchlists.route) {
                WatchlistScreenRoute(
                    onWatchlistClick = { watchlistId ->
                        navController.navigate("watchlist/$watchlistId")
                    },
                    onExploreClick = {
                        navController.navigate(BottomDestination.Explore.route) {
                            popUpTo(navController.graph.findStartDestination().id) {
                                saveState = true
                            }
                            launchSingleTop = true
                            restoreState = true
                        }
                    },
                )
            }
            composable(SearchRoute) {
                SearchScreenRoute(
                    onBackClick = { navController.popBackStack() },
                    onFundClick = { schemeCode ->
                        navController.navigate("fund/$schemeCode")
                    },
                )
            }
            composable(ViewAllRoute) {
                CategoryScreenRoute(
                    onBackClick = { navController.popBackStack() },
                    onFundClick = { schemeCode ->
                        navController.navigate("fund/$schemeCode")
                    },
                )
            }
            composable(DetailRoute) {
                FundDetailRoute(
                    onBackClick = { navController.popBackStack() },
                )
            }
            composable(WatchlistDetailRoute) {
                WatchlistDetailRoute(
                    onBackClick = { navController.popBackStack() },
                    onFundClick = { schemeCode ->
                        navController.navigate("fund/$schemeCode")
                    },
                    onExploreClick = {
                        navController.navigate(BottomDestination.Explore.route) {
                            popUpTo(navController.graph.findStartDestination().id) {
                                saveState = true
                            }
                            launchSingleTop = true
                            restoreState = true
                        }
                    },
                )
            }
        }
    }
}

@Preview
@Composable
private fun InvestNestAppPreview() {
    InvestNestTheme {
        InvestNestApp()
    }
}
