/*
 * Copyright 2026, NeoLauncher (Lawnchair fork)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package app.lawnchair.neoimport

import android.app.WallpaperManager
import android.content.Context
import android.graphics.BitmapFactory
import android.util.Log
import app.lawnchair.preferences2.PreferenceManager2
import app.lawnchair.theme.color.ColorOption
import app.lawnchair.theme.color.ColorStyle
import com.patrykmichalik.opto.core.setBlocking
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Applies a parsed .neoui.zip spec to the launcher.
 *
 * FULLY WIRED (this pass):
 *  - Theme colors: accent, hotseat background, app-drawer background, color style
 *    via PreferenceManager2 (app.lawnchair.preferences2.PreferenceManager2).
 *    Each pref's onSet handler in PreferenceManager2 restarts/recreates/reloads the
 *    grid as needed — see PreferenceManager2.kt.
 *  - Wallpaper: WallpaperManager.setBitmap — same call LawnchairBackup uses in
 *    app.lawnchair.backup.LawnchairBackup.restore().
 *  - Spec persistence: the raw ui.json is stored in SharedPreferences ("neo_import",
 *    key "last_spec") so follow-up screens (control-center overlay, app wheel) can
 *    read the imported spec without re-picking the file.
 *
 * WIRED (increment 2):
 *  - Control-center overlay: [NeoControlCenterActivity] renders the spec's
 *    tiles (or the built-in NEO-TACTILE defaults), opened from Settings →
 *    Open Control Center and from the Control Center widget header. The
 *    swipe-down gesture hook is still TODO.
 *
 * NOT WIRED YET (explicit TODOs — nothing is faked):
 *  - Home-screen / dock item placement: see [applyHomeScreenItems].
 *  - App-wheel screen (drawer replacement): see [applyControlCenterAndWheel].
 *    The spec for it is persisted, so the screen only needs to be built and
 *    read it back via [readImportedSpec].
 */
object UiImporter {
    private const val TAG = "UiImporter"
    private const val PREFS_NAME = "neo_import"
    private const val KEY_LAST_SPEC = "last_spec"

    /** What [apply] actually did, so the UI can report applied vs pending honestly. */
    data class ImportResult(
        val applied: List<String>,
        val pending: List<String>,
    )

    /**
     * Applies [file]'s spec. Must be called from a background thread (it does disk
     * + WallpaperManager work). Returns what was applied vs what is still TODO.
     */
    suspend fun apply(context: Context, file: NeoUiFile): ImportResult {
        val applied = mutableListOf<String>()
        val pending = mutableListOf<String>()
        val spec = file.spec
        val appContext = context.applicationContext

        // ---- Theme colors (PreferenceManager2) ----
        val prefs2 = PreferenceManager2.getInstance(appContext)
        try {
            prefs2.accentColor.setBlocking(ColorOption.CustomColor(parseHexColor(spec.theme.accent)))
            applied += "Accent color ${spec.theme.accent}"
        } catch (e: Exception) {
            Log.e(TAG, "accent color failed", e)
            pending += "Accent color (invalid value ${spec.theme.accent})"
        }
        try {
            prefs2.hotseatBackgroundColor.setBlocking(
                ColorOption.CustomColor(parseHexColor(spec.theme.hotseatBackground)),
            )
            applied += "Hotseat background ${spec.theme.hotseatBackground}"
        } catch (e: Exception) {
            Log.e(TAG, "hotseat color failed", e)
            pending += "Hotseat background (invalid value ${spec.theme.hotseatBackground})"
        }
        try {
            prefs2.appDrawerBackgroundColor.setBlocking(
                ColorOption.CustomColor(parseHexColor(spec.theme.drawerBackground)),
            )
            applied += "App-drawer background ${spec.theme.drawerBackground}"
        } catch (e: Exception) {
            Log.e(TAG, "drawer color failed", e)
            pending += "App-drawer background (invalid value ${spec.theme.drawerBackground})"
        }
        try {
            prefs2.colorStyle.setBlocking(ColorStyle.fromString(spec.theme.colorStyle))
            applied += "Color style ${spec.theme.colorStyle}"
        } catch (e: Exception) {
            Log.e(TAG, "color style failed", e)
            pending += "Color style (unknown value ${spec.theme.colorStyle})"
        }

        // ---- Wallpaper ----
        val wallpaper = file.wallpaperBytes
        if (wallpaper != null) {
            try {
                withContext(Dispatchers.IO) {
                    val bitmap = BitmapFactory.decodeByteArray(wallpaper, 0, wallpaper.size)
                        ?: throw IllegalArgumentException("wallpaper bytes did not decode")
                    WallpaperManager.getInstance(appContext).setBitmap(bitmap)
                }
                applied += "Wallpaper (${spec.theme.wallpaperFile})"
            } catch (e: Exception) {
                Log.e(TAG, "wallpaper failed", e)
                pending += "Wallpaper (could not apply ${spec.theme.wallpaperFile})"
            }
        }

        // ---- Persist the spec for follow-up screens ----
        appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_LAST_SPEC, spec.rawJson)
            .apply()
        applied += "Saved UI spec \"${spec.name}\" for control center + app wheel"

        // ---- Control center: built (increment 2) — report as applied ----
        if (spec.controlCenter.tiles.isNotEmpty()) {
            applied += "Control center (${spec.controlCenter.tiles.size} tiles) — built: open from Settings → Open Control Center or the Control Center widget"
        }

        // ---- TODOs (recorded, not faked) ----
        if (spec.homeScreen.isNotEmpty() || spec.dock.isNotEmpty()) {
            pending += applyHomeScreenItems(appContext, spec)
        }
        if (spec.appWheel.apps.isNotEmpty()) {
            pending += applyControlCenterAndWheel(appContext, spec)
        }

        return ImportResult(applied, pending)
    }

    /**
     * TODO — home-screen / dock item placement.
     *
     * What it needs: write Favorites rows for each [HomeItem] into the launcher DB
     * and reload the model. The established pattern is in
     * app.lawnchair.backup.LawnchairBackup.restore(), which restores the whole DB
     * file and then calls RestoreDbTask.performRestore(context, dbController) with a
     * ModelDbController (com.android.launcher3.model.ModelDbController). Placing
     * individual items means either (a) building a partial DB restore in that same
     * style, or (b) inserting via the model writer and calling
     * LauncherAppState.getInstance(context).model.reloadIfActive(). Do NOT fake this
     * by drawing icons that don't launch — every placed item must resolve to a real
     * package/class on the device.
     *
     * Returns the list of pending item descriptions for the result UI.
     */
    private fun applyHomeScreenItems(context: Context, spec: UiSpec): List<String> {
        @Suppress("UNUSED_PARAMETER")
        val unused = context
        val out = mutableListOf<String>()
        out += "Home-screen items (${spec.homeScreen.size}) — TODO: place via ModelDbController / RestoreDbTask (see LawnchairBackup.restore)"
        if (spec.dock.isNotEmpty()) {
            out += "Dock items (${spec.dock.size}) — TODO: same placement path as home-screen items"
        }
        return out
    }

    /**
     * TODO — app-wheel screen (drawer replacement).
     *
     * What it needs: Lawnchair has no radial app wheel today, so this is a NEW
     * screen, not a re-skin. Build it as a bottom-sheet / overlay activity that
     * reads the persisted spec via [readImportedSpec] and renders [WheelApp]s
     * (launch by package). The quick-launch slice already exists as the
     * NeoAppWheelWidget (Phone / Messages / Camera / Browser) — the full wheel
     * screen replaces / augments the app drawer. Action keys must map to real
     * intents — never a dead button.
     *
     * (The control-center overlay is BUILT — see NeoControlCenterActivity —
     * so it no longer reports here.)
     */
    private fun applyControlCenterAndWheel(context: Context, spec: UiSpec): List<String> {
        @Suppress("UNUSED_PARAMETER")
        val unused = context
        val out = mutableListOf<String>()
        if (spec.appWheel.apps.isNotEmpty()) {
            out += "App wheel (${spec.appWheel.apps.size} apps) — TODO: build wheel screen reading the persisted spec"
        }
        return out
    }

    /** Reads back the last imported spec (raw ui.json), or null when none imported. */
    fun readImportedSpec(context: Context): UiSpec? {
        val raw = context.applicationContext
            .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(KEY_LAST_SPEC, null) ?: return null
        return try {
            UiSpec.parse(raw)
        } catch (e: Exception) {
            Log.e(TAG, "persisted spec did not parse", e)
            null
        }
    }
}
