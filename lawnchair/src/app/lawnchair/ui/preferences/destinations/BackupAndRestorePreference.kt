package app.lawnchair.ui.preferences.destinations

import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import app.lawnchair.backup.ui.restoreBackupOpener
import app.lawnchair.neoimport.ImportUiActivity
import app.lawnchair.neoimport.NeoControlCenterActivity
import app.lawnchair.backup.ui.restoreNovaBackupOpener
import app.lawnchair.ui.preferences.LocalIsExpandedScreen
import app.lawnchair.ui.preferences.components.NavigationActionPreference
import app.lawnchair.ui.preferences.components.controls.ClickablePreference
import app.lawnchair.ui.preferences.components.layout.PreferenceGroup
import app.lawnchair.ui.preferences.components.layout.PreferenceLayout
import app.lawnchair.ui.preferences.navigation.CreateBackup
import com.android.launcher3.R

@Composable
fun BackupAndRestorePreference(
    modifier: Modifier = Modifier,
) {
    PreferenceLayout(
        label = stringResource(R.string.backup_and_restore_label),
        backArrowVisible = !LocalIsExpandedScreen.current,
        modifier = modifier,
    ) {
        PreferenceGroup {
            NavigationActionPreference(
                label = stringResource(R.string.create_backup),
                subtitle = stringResource(R.string.create_backup_description),
                destination = CreateBackup,
            )
            ClickablePreference(
                label = stringResource(R.string.restore_backup),
                subtitle = stringResource(R.string.restore_backup_description),
                onClick = restoreBackupOpener(),
            )
        }
        PreferenceGroup {
            ClickablePreference(
                label = stringResource(R.string.restore_nova_backup),
                subtitle = stringResource(R.string.restore_nova_backup_description),
                onClick = restoreNovaBackupOpener(),
            )
        }
        PreferenceGroup {
            val context = LocalContext.current
            ClickablePreference(
                label = stringResource(R.string.import_ui),
                subtitle = stringResource(R.string.import_ui_description),
                onClick = {
                    context.startActivity(Intent(context, ImportUiActivity::class.java))
                },
            )
            ClickablePreference(
                label = stringResource(R.string.neo_open_control_center),
                subtitle = stringResource(R.string.neo_open_control_center_desc),
                onClick = {
                    context.startActivity(Intent(context, NeoControlCenterActivity::class.java))
                },
            )
        }
    }
}
