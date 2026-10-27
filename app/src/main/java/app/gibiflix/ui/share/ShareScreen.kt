package app.gibiflix.ui.share

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.gibiflix.R

@Composable
fun ShareScreen() {
    Column(Modifier.fillMaxSize().statusBarsPadding().padding(16.dp)) {
        Text(stringResource(R.string.share_title), style = MaterialTheme.typography.headlineMedium)
        Text(
            stringResource(R.string.share_coming),
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.padding(top = 12.dp),
        )
    }
}
