package com.example

import android.content.Context
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import com.example.data.model.SortBy
import com.example.data.model.ThemeMode
import com.example.data.prefs.UserPreferencesRepository
import com.example.ui.screens.SettingsScreen
import com.example.ui.theme.NexoFilesTheme
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ThemeModeTest {

  @get:Rule
  val composeTestRule = createComposeRule()

  @Test
  fun `user preferences persists light dark and system theme modes`() = runBlocking {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val prefs = UserPreferencesRepository(context)

    // Default should be SYSTEM
    assertEquals(ThemeMode.SYSTEM, prefs.themeMode.first())

    prefs.setThemeMode(ThemeMode.LIGHT)
    assertEquals(ThemeMode.LIGHT, prefs.themeMode.first())

    prefs.setThemeMode(ThemeMode.DARK)
    assertEquals(ThemeMode.DARK, prefs.themeMode.first())

    prefs.setThemeMode(ThemeMode.SYSTEM)
    assertEquals(ThemeMode.SYSTEM, prefs.themeMode.first())
  }

  @Test
  fun `nexo theme provides exact dark and light palettes`() {
    var darkBg: Color? = null
    var darkSurface: Color? = null
    var darkSurfaceVar: Color? = null
    var darkOutline: Color? = null
    var darkPrimary: Color? = null
    var darkOnPrimary: Color? = null
    var darkSecondary: Color? = null
    var darkOnBg: Color? = null
    var darkOnSurfaceVar: Color? = null

    var lightBg: Color? = null
    var lightSurface: Color? = null
    var lightSurfaceVar: Color? = null
    var lightOutline: Color? = null
    var lightPrimary: Color? = null
    var lightOnPrimary: Color? = null
    var lightSecondary: Color? = null
    var lightOnBg: Color? = null
    var lightOnSurfaceVar: Color? = null

    composeTestRule.setContent {
      NexoFilesTheme(themeMode = ThemeMode.DARK) {
        val cs = MaterialTheme.colorScheme
        darkBg = cs.background
        darkSurface = cs.surface
        darkSurfaceVar = cs.surfaceVariant
        darkOutline = cs.outline
        darkPrimary = cs.primary
        darkOnPrimary = cs.onPrimary
        darkSecondary = cs.secondary
        darkOnBg = cs.onBackground
        darkOnSurfaceVar = cs.onSurfaceVariant
      }
      NexoFilesTheme(themeMode = ThemeMode.LIGHT) {
        val cs = MaterialTheme.colorScheme
        lightBg = cs.background
        lightSurface = cs.surface
        lightSurfaceVar = cs.surfaceVariant
        lightOutline = cs.outline
        lightPrimary = cs.primary
        lightOnPrimary = cs.onPrimary
        lightSecondary = cs.secondary
        lightOnBg = cs.onBackground
        lightOnSurfaceVar = cs.onSurfaceVariant
      }
    }
    composeTestRule.waitForIdle()

    // Exact Dark Mode Palette (Purple Primary + Green Complement)
    assertEquals(Color(0xFF110A1D), darkBg)
    assertEquals(Color(0xFF1D1230), darkSurface)
    assertEquals(Color(0xFF26183E), darkSurfaceVar)
    assertEquals(Color(0xFF3B2561), darkOutline)
    assertEquals(Color(0xFF9C7AFF), darkPrimary)
    assertEquals(Color(0xFF0D0816), darkOnPrimary)
    assertEquals(Color(0xFF39FF14), darkSecondary)
    assertEquals(Color(0xFFF3EFFF), darkOnBg)
    assertEquals(Color(0xFFA899C2), darkOnSurfaceVar)

    // Exact Light Mode Palette (Soft Grayish Surfaces + Purple Primary + Green Complement)
    assertEquals(Color(0xFFE4E2EA), lightBg)
    assertEquals(Color(0xFFECEAF2), lightSurface)
    assertEquals(Color(0xFFDCD8E6), lightSurfaceVar)
    assertEquals(Color(0xFFC0B8D0), lightOutline)
    assertEquals(Color(0xFF6D44B8), lightPrimary)
    assertEquals(Color(0xFFF5F3FA), lightOnPrimary)
    assertEquals(Color(0xFF129E00), lightSecondary)
    assertEquals(Color(0xFF180D2B), lightOnBg)
    assertEquals(Color(0xFF534966), lightOnSurfaceVar)
  }

  @Test
  fun `settings screen theme selector switches between light dark and system`() {
    var selectedMode = ThemeMode.SYSTEM

    composeTestRule.setContent {
      NexoFilesTheme(themeMode = selectedMode) {
        SettingsScreen(
          themeMode = selectedMode,
          showHiddenFiles = true,
          sortBy = SortBy.NAME,
          vaultBiometricEnabled = true,
          hasAllFilesAccess = true,
          webServerPort = 8080,
          onSelectThemeMode = { selectedMode = it },
          onToggleHiddenFiles = {},
          onSelectSortBy = {},
          onToggleVaultBiometric = {},
          onGrantStorageAccess = {},
          onOpenViewOnPc = {},
          onBack = {},
        )
      }
    }

    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag("settings_theme_LIGHT").assertIsDisplayed().performClick()
    assertEquals(ThemeMode.LIGHT, selectedMode)

    composeTestRule.onNodeWithTag("settings_theme_DARK").assertIsDisplayed().performClick()
    assertEquals(ThemeMode.DARK, selectedMode)

    composeTestRule.onNodeWithTag("settings_theme_SYSTEM").assertIsDisplayed().performClick()
    assertEquals(ThemeMode.SYSTEM, selectedMode)
  }
}
