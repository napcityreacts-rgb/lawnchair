# NEO Import UI (.neoui.zip)

Fork of [Lawnchair](https://github.com/LawnchairLauncher/lawnchair) (16-dev, base commit
`85335d9`) with an **Import UI** feature: load a whole launcher UI from a `.neoui.zip`
pack instead of hand-theming the launcher.

> Status: source only, **not tested on a phone**. The theme + wallpaper path is wired
> to real Lawnchair APIs. Home-screen placement and the control-center / app-wheel
> screens are explicit TODOs — the importer reports them as pending, it does not fake
> them. No CI run was verified for this branch.

## How to use

1. Build + install this fork (same as Lawnchair: `./gradlew assembleDebug`, install,
   set as default launcher).
2. Settings → Backup & restore → **Import UI** → pick a `.neoui.zip` file.
3. The screen previews the pack (name, author, accent, counts), then **Apply this UI**.
4. The result screen lists what was applied and what is still to wire.

A sample pack ships at repo root: `neo-tactile-sample.neoui.zip`
(NEO-TACTILE v7 OMEGA: Noguchi Red / ink / beige-glass tokens, 12 control-center tiles).

## The .neoui.zip spec format (version 1)

A `.neoui.zip` is a plain ZIP containing:

| Entry | Required | Contents |
|---|---|---|
| `ui.json` | yes | The spec (below) |
| `wallpaper.png` | no | Wallpaper image, referenced by `theme.wallpaper` |
| other assets | no | Any extra files, referenced by name |

`ui.json` shape (all fields shown; only `format` + `version` are strictly required —
everything else falls back to NEO-TACTILE defaults):

```jsonc
{
  "format": "neoui",          // must be "neoui"
  "version": 1,              // must be 1
  "name": "NEO-TACTILE v7 OMEGA",
  "author": "Dom",
  "theme": {
    "darkMode": true,                    // stored in spec; launcher dark-mode toggle is separate (see TODOs)
    "accent": "#FF2A44",                 // hex #RRGGBB / #AARRGGBB
    "background": "#0C0A0D",
    "surface": "#19171B",
    "hotseatBackground": "#19171B",
    "drawerBackground": "#0C0A0D",
    "colorStyle": "tonal_spot",          // Lawnchair ColorStyle name
    "wallpaper": "wallpaper.png"         // file name inside the zip; "" = no wallpaper
  },
  "grid": { "columns": 4, "rows": 5 },   // recorded; grid application is a TODO
  "homeScreen": [                        // TODO: placement (see below)
    { "type": "app", "package": "com.google.android.dialer", "label": "Phone", "x": 0, "y": 0 }
  ],
  "dock": [                              // TODO: placement (see below)
    { "type": "app", "package": "com.google.android.dialer", "label": "Phone", "x": 0, "y": 0 }
  ],
  "controlCenter": {                     // TODO: overlay screen (see below)
    "tiles": [
      { "icon": "wifi", "label": "WiFi", "action": "wifi", "style": "plush" }
    ]
  },
  "appWheel": {                          // TODO: wheel screen (see below)
    "apps": [
      { "package": "com.google.android.dialer", "label": "Phone", "icon": "phone" }
    ]
  }
}
```

Field notes:

- `homeScreen[]` / `dock[]` item `type`: `"app"` (launch by `package`), `"shortcut"`,
  `"widget"` (bind by `package` + `class`), `"folder"`. `x`/`y`/`spanX`/`spanY` are grid
  coordinates (defaults 0/0/1/1).
- `controlCenter.tiles[]` `action` keys: `wifi`, `bluetooth`, `airplane`, `dnd`,
  `flashlight`, `rotate`, `camera`, `timer`, `messages`, `music`, `maps`, `settings`.
  `style`: `"plush"` (ink-noir tile) or `"glass"` (beige-glass tile).
- `appWheel.apps[]` are apps the wheel launches by `package`.

## Files added (all under `lawnchair/src/app/lawnchair/neoimport/`)

| File | What it does |
|---|---|
| `UiSpec.kt` | Data classes + `org.json` parsing for the spec above. No new dependencies. |
| `NeoUiFile.kt` | Reads a `.neoui.zip` from a content `Uri` into `UiSpec` + asset bytes. |
| `UiImporter.kt` | Applies a spec (see wiring below). Reports applied vs pending. |
| `ImportUiActivity.kt` | The picker → preview → apply screen (Compose, `LawnchairTheme`). |

## Wiring points (exact files/functions)

| What | Where |
|---|---|
| Activity registration | `lawnchair/AndroidManifest.xml` → `app.lawnchair.neoimport.ImportUiActivity` (`exported=false`, `@style/Theme.Lawnchair`) |
| Settings entry | `lawnchair/src/app/lawnchair/ui/preferences/destinations/BackupAndRestorePreference.kt` → new `PreferenceGroup` + `ClickablePreference` launching `ImportUiActivity` |
| Strings | `lawnchair/res/values/strings.xml` → `import_ui*` strings |
| Accent color | `UiImporter.apply()` → `PreferenceManager2.getInstance(ctx).accentColor.setBlocking(ColorOption.CustomColor(...))` — pref declared in `lawnchair/src/app/lawnchair/preferences2/PreferenceManager2.kt:311` |
| Hotseat bg | `UiImporter.apply()` → `prefs2.hotseatBackgroundColor.setBlocking(...)` — declared `PreferenceManager2.kt:214` |
| Drawer bg | `UiImporter.apply()` → `prefs2.appDrawerBackgroundColor.setBlocking(...)` — declared `PreferenceManager2.kt:229` |
| Color style | `UiImporter.apply()` → `prefs2.colorStyle.setBlocking(ColorStyle.fromString(...))` — declared `PreferenceManager2.kt:198`; `ColorStyle` in `lawnchair/src/app/lawnchair/theme/color/ColorStyles.kt` |
| Wallpaper | `UiImporter.apply()` → `WallpaperManager.getInstance(ctx).setBitmap(...)` — same call `app.lawnchair.backup.LawnchairBackup.restore()` uses |
| Spec persistence | `UiImporter.apply()` → `SharedPreferences "neo_import"`, key `"last_spec"` (raw ui.json). Read back via `UiImporter.readImportedSpec()` |
| Pref reload behavior | Each pref's `onSet` in `PreferenceManager2` restarts/recreates/reloads the grid — no manual restart needed |

## What is fully wired vs TODO

**Wired:** spec parsing, zip reading, accent/hotseat/drawer colors, color style, wallpaper,
spec persistence, preview + apply UI, applied-vs-pending reporting.

**TODO (explicitly marked in `UiImporter.kt`, nothing faked):**

1. **Home-screen / dock placement** — `UiImporter.applyHomeScreenItems()`.
   Needs: write Favorites rows for each `HomeItem` into the launcher DB and reload the
   model. Established pattern: `app.lawnchair.backup.LawnchairBackup.restore()`, which
   restores the whole DB file then calls `RestoreDbTask.performRestore(context,
   dbController)` with a `ModelDbController` (`com.android.launcher3.model.ModelDbController`).
   Either build a partial-DB restore in that style, or insert via the model writer and
   call `LauncherAppState.getInstance(context).model.reloadIfActive()`. Every placed
   item must resolve to a real installed package/class.
2. **Control-center overlay + app-wheel screens** — `UiImporter.applyControlCenterAndWheel()`.
   Lawnchair has no such surfaces today, so these are NEW screens (bottom-sheet /
   overlay activity) that read the persisted spec via `UiImporter.readImportedSpec()`
   and render the tiles / wheel apps. Action keys must map to real intents (same set
   Nappy Widgets already implements: WiFi panel, Bluetooth settings, DND, flashlight,
   camera, timer, messages, music, maps, settings) — never a dead button.
3. **Grid + darkMode from spec** — `grid` and `theme.darkMode` are parsed and stored
   but not applied. Grid maps to `DeviceGridState` (`com.android.launcher3.model.DeviceGridState`,
   see `LawnchairBackup.restore()`); dark mode maps to Lawnchair's theme setting.
4. **On-phone verification** — nothing here has run on a device yet.

## Build

Same as upstream Lawnchair (see upstream `README.md` / `CONTRIBUTING.md`). This branch
adds no new dependencies — parsing is `org.json` (Android built-in), prefs use the
existing opto `setBlocking` API.
