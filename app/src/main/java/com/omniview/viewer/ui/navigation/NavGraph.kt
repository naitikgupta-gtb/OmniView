package com.omniview.viewer.ui.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.omniview.viewer.data.model.FileCategory
import com.omniview.viewer.data.model.FileItem
import com.omniview.viewer.ui.browser.FileBrowserScreen
import com.omniview.viewer.ui.settings.SettingsScreen
import com.omniview.viewer.ui.viewer.code.CodeViewerScreen
import com.omniview.viewer.ui.viewer.image.ImageViewerScreen
import com.omniview.viewer.ui.viewer.office.OfficeViewerScreen
import com.omniview.viewer.ui.viewer.office.OfficeWebViewer
import com.omniview.viewer.ui.viewer.pdf.PdfViewerScreen
import com.omniview.viewer.ui.viewer.text.TextViewerScreen
import com.omniview.viewer.ui.viewer.video.MediaPlayerScreen
import java.net.URLDecoder
import java.net.URLEncoder

object Routes {
    const val BROWSER = "browser"
    const val SETTINGS = "settings"
    const val VIEWER = "viewer/{category}/{path}/{name}/{ext}"

    fun viewer(item: FileItem): String {
        val encodedPath = URLEncoder.encode(item.path, "UTF-8")
        val encodedName = URLEncoder.encode(item.name, "UTF-8")
        return "viewer/${item.category.name}/$encodedPath/$encodedName/${item.extension}"
    }
}

@Composable
fun OmniNavGraph(navController: NavHostController = rememberNavController()) {
    NavHost(navController = navController, startDestination = Routes.BROWSER) {

        composable(Routes.BROWSER) {
            FileBrowserScreen(onOpenFile = { item ->
                navController.navigate(Routes.viewer(item))
            })
        }

        composable(Routes.SETTINGS) {
            SettingsScreen(onBack = { navController.popBackStack() })
        }

        composable(Routes.VIEWER) { backStackEntry ->
            val categoryName = backStackEntry.arguments?.getString("category") ?: FileCategory.UNKNOWN.name
            val path = URLDecoder.decode(backStackEntry.arguments?.getString("path") ?: "", "UTF-8")
            val name = URLDecoder.decode(backStackEntry.arguments?.getString("name") ?: "", "UTF-8")
            val ext = backStackEntry.arguments?.getString("ext") ?: ""
            val category = FileCategory.valueOf(categoryName)
            val onBack: () -> Unit = { navController.popBackStack() }

            when (category) {
                FileCategory.IMAGE -> ImageViewerScreen(path, name, onBack)
                FileCategory.PDF -> PdfViewerScreen(path, name, onBack)
                FileCategory.VIDEO -> MediaPlayerScreen(path, name, isAudio = false, onBack = onBack)
                FileCategory.AUDIO -> MediaPlayerScreen(path, name, isAudio = true, onBack = onBack)
                FileCategory.CODE -> CodeViewerScreen(path, name, ext, onBack)
                FileCategory.TEXT -> TextViewerScreen(path, name, isCsv = ext == "csv", onBack = onBack)
                FileCategory.DOCUMENT, FileCategory.SPREADSHEET, FileCategory.PRESENTATION ->
                    if (ext.lowercase() in setOf("docx", "xlsx", "pptx")) {
                        OfficeWebViewer(path, name, ext, onBack)
                    } else {
                        OfficeViewerScreen(path, name, ext, onBack) // legacy .doc / .xls
                    }
                else -> TextViewerScreen(path, name, isCsv = false, onBack = onBack) // fallback: plain-text attempt
            }
        }
    }
}
