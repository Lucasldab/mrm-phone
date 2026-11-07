package io.lldab.mrmphone.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import io.lldab.mrmphone.DeepLink
import io.lldab.mrmphone.data.AppContainer
import io.lldab.mrmphone.ui.library.LibraryScreen
import io.lldab.mrmphone.ui.library.LibraryViewModel
import io.lldab.mrmphone.ui.reader.ReaderScreen
import io.lldab.mrmphone.ui.reader.ReaderViewModel
import io.lldab.mrmphone.ui.search.SearchScreen
import io.lldab.mrmphone.ui.search.SearchViewModel
import io.lldab.mrmphone.ui.series.SeriesScreen
import io.lldab.mrmphone.ui.series.SeriesViewModel
import io.lldab.mrmphone.ui.settings.SettingsScreen
import io.lldab.mrmphone.ui.settings.SettingsViewModel

object Routes {
    const val LIBRARY = "library"
    const val SEARCH = "search"
    const val SETTINGS = "settings"
    fun series(id: Long) = "series/$id"
    fun reader(seriesId: Long, chapterId: Long) = "reader/$seriesId/$chapterId"
}

@Composable
fun AppNav(container: AppContainer, deepLink: DeepLink?, onDeepLinkHandled: () -> Unit) {
    val nav = rememberNavController()

    LaunchedEffect(deepLink) {
        when (deepLink) {
            null -> return@LaunchedEffect
            DeepLink.Library -> nav.navigate(Routes.LIBRARY) { popUpTo(Routes.LIBRARY) { inclusive = true } }
            is DeepLink.Series -> nav.navigate(Routes.series(deepLink.id)) {
                popUpTo(Routes.LIBRARY)
                launchSingleTop = true
            }
        }
        onDeepLinkHandled()
    }

    NavHost(
        navController = nav,
        startDestination = Routes.LIBRARY,
        modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background),
    ) {
        composable(Routes.LIBRARY) {
            val vm: LibraryViewModel = viewModel { LibraryViewModel(container) }
            LibraryScreen(
                vm = vm,
                onOpen = { nav.navigate(Routes.series(it)) },
                onSearch = { nav.navigate(Routes.SEARCH) },
                onSettings = { nav.navigate(Routes.SETTINGS) },
            )
        }
        composable(
            "series/{id}",
            arguments = listOf(navArgument("id") { type = NavType.LongType }),
        ) { entry ->
            val id = entry.arguments?.getLong("id") ?: return@composable
            val vm: SeriesViewModel = viewModel(key = "series-$id") { SeriesViewModel(container, id) }
            SeriesScreen(
                vm = vm,
                onBack = { nav.popBackStack() },
                onRead = { chapterId -> nav.navigate(Routes.reader(id, chapterId)) },
            )
        }
        composable(
            "reader/{seriesId}/{chapterId}",
            arguments = listOf(
                navArgument("seriesId") { type = NavType.LongType },
                navArgument("chapterId") { type = NavType.LongType },
            ),
        ) { entry ->
            val seriesId = entry.arguments?.getLong("seriesId") ?: return@composable
            val chapterId = entry.arguments?.getLong("chapterId") ?: return@composable
            val vm: ReaderViewModel = viewModel(key = "reader-$chapterId") {
                ReaderViewModel(container, seriesId, chapterId)
            }
            ReaderScreen(
                vm = vm,
                onBack = { nav.popBackStack() },
                onChapter = { next ->
                    nav.navigate(Routes.reader(seriesId, next)) {
                        popUpTo("reader/{seriesId}/{chapterId}") { inclusive = true }
                    }
                },
            )
        }
        composable(Routes.SEARCH) {
            val vm: SearchViewModel = viewModel { SearchViewModel(container) }
            SearchScreen(
                vm = vm,
                onBack = { nav.popBackStack() },
                onOpen = { id ->
                    nav.navigate(Routes.series(id)) { popUpTo(Routes.LIBRARY) }
                },
            )
        }
        composable(Routes.SETTINGS) {
            val vm: SettingsViewModel = viewModel { SettingsViewModel(container) }
            SettingsScreen(vm = vm, onBack = { nav.popBackStack() })
        }
    }
}
