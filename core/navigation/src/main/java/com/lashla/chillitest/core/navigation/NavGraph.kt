package com.lashla.chillitest.core.navigation

import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.lashla.chillitest.feature.details.DetailsScreen
import com.lashla.chillitest.feature.search.SearchScreen

@Composable
fun NavGraph(modifier: Modifier = Modifier) {
    val navController = rememberNavController()

    NavHost(
        navController,
        startDestination = Routes.SEARCH,
        modifier = modifier
    ) {
        composable(Routes.SEARCH) {
            SearchScreen(onGifSelected = { gifId ->
                navController.navigate(Routes.details(gifId)) {
                    launchSingleTop = true
                }
            })
        }
        composable(Routes.DETAILS) {
            DetailsScreen(
                onBack = { navController.popBackStack() },
                onGifSelected = { gifId ->
                    navController.navigate(Routes.details(gifId)) {
                        popUpTo(Routes.SEARCH)
                    }
                },
            )
        }
    }
}
