package com.example.data.prefs

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * SharedPreferences-backed configuration for the customizable Bottom Navigation Bar (2 to 5 active tabs).
 * Default order: HOME -> FILES -> VAULT -> ANALYZE -> TOOLS (Network & More).
 */
class BottomBarPreferences(context: Context) {

  private val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

  private val _activeTabIds = MutableStateFlow(loadSavedTabs())
  val activeTabIds: StateFlow<List<String>> = _activeTabIds.asStateFlow()

  private fun loadSavedTabs(): List<String> {
    val raw = prefs.getString(KEY_TABS_CSV, null) ?: return DEFAULT_TABS
    val parsed = raw.split(',')
      .map { it.trim() }
      .filter { it in ALL_AVAILABLE_TAB_IDS }
      .distinct()
      .take(MAX_TABS)
    return if (parsed.size >= MIN_TABS) parsed else DEFAULT_TABS
  }

  private fun saveTabs(tabs: List<String>) {
    val normalized = tabs.filter { it in ALL_AVAILABLE_TAB_IDS }.distinct().take(MAX_TABS)
    if (normalized.size < MIN_TABS) return
    prefs.edit().putString(KEY_TABS_CSV, normalized.joinToString(",")).apply()
    _activeTabIds.value = normalized
  }

  fun toggleTab(tabId: String): Boolean {
    if (tabId !in ALL_AVAILABLE_TAB_IDS) return false
    val current = _activeTabIds.value.toMutableList()
    return if (tabId in current) {
      if (current.size <= MIN_TABS) {
        false
      } else {
        current.remove(tabId)
        saveTabs(current)
        true
      }
    } else {
      if (current.size >= MAX_TABS) {
        false
      } else {
        current.add(tabId)
        saveTabs(current)
        true
      }
    }
  }

  fun moveTab(tabId: String, direction: Int) {
    val current = _activeTabIds.value.toMutableList()
    val idx = current.indexOf(tabId)
    if (idx < 0) return
    val targetIdx = idx + direction
    if (targetIdx !in current.indices) return
    val tmp = current[targetIdx]
    current[targetIdx] = current[idx]
    current[idx] = tmp
    saveTabs(current)
  }

  fun resetDefaults() {
    saveTabs(DEFAULT_TABS)
  }

  companion object {
    private const val PREFS_NAME = "nexofiles_bottom_bar_prefs"
    private const val KEY_TABS_CSV = "active_bottom_tabs_csv"
    const val MIN_TABS = 2
    const val MAX_TABS = 5

    val DEFAULT_TABS = listOf("HOME", "FILES", "VAULT", "ANALYZE", "TOOLS")

    val ALL_AVAILABLE_TAB_IDS = listOf(
      "HOME",
      "FILES",
      "VAULT",
      "ANALYZE",
      "TOOLS",
      "DRIVE",
      "EXTERNAL_CLOUDS",
      "NETWORK",
      "RECYCLE_BIN",
      "SETTINGS",
    )
  }
}
