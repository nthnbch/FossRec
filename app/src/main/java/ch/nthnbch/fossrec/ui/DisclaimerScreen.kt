package ch.nthnbch.fossrec.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Gavel
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import ch.nthnbch.fossrec.R
import ch.nthnbch.fossrec.ui.theme.Dimens

/** Legal notice shown on first launch. The app cannot be used until the box is ticked; otherwise it quits. */
@Composable
fun DisclaimerScreen(onAccept: () -> Unit, onDecline: () -> Unit) {
    var understood by rememberSaveable { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .systemBarsPadding()
            .padding(Dimens.s5)
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(Dimens.s4)
        ) {
            Spacer(Modifier.height(Dimens.s5))
            Icon(
                Icons.Default.Gavel,
                contentDescription = null,
                modifier = Modifier.size(Dimens.s8),
                tint = MaterialTheme.colorScheme.primary
            )
            Text(
                text = stringResource(R.string.disclaimer_title),
                style = MaterialTheme.typography.displaySmall,
                color = MaterialTheme.colorScheme.primary,
                textAlign = TextAlign.Center
            )
            Text(
                text = stringResource(R.string.disclaimer_heading),
                style = MaterialTheme.typography.titleLarge,
                textAlign = TextAlign.Center
            )
            Text(
                text = stringResource(R.string.disclaimer_body),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Spacer(Modifier.height(Dimens.s4))

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .toggleable(value = understood, role = Role.Checkbox, onValueChange = { understood = it })
                .padding(vertical = Dimens.s3),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Checkbox(checked = understood, onCheckedChange = null)
            Spacer(Modifier.width(Dimens.s4))
            Text(
                text = stringResource(R.string.disclaimer_checkbox),
                style = MaterialTheme.typography.bodyLarge
            )
        }

        Spacer(Modifier.height(Dimens.s4))

        Button(
            onClick = onAccept,
            enabled = understood,
            modifier = Modifier
                .fillMaxWidth()
                .height(Dimens.s7)
        ) {
            Text(stringResource(R.string.disclaimer_continue), style = MaterialTheme.typography.labelLarge)
        }
        TextButton(
            onClick = onDecline,
            modifier = Modifier
                .fillMaxWidth()
                .height(Dimens.s7)
        ) {
            Text(stringResource(R.string.disclaimer_quit), style = MaterialTheme.typography.labelLarge)
        }
    }
}
