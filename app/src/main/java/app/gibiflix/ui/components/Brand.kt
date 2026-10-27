package app.gibiflix.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.gibiflix.R
import app.gibiflix.core.device.Profile
import app.gibiflix.ui.theme.Anton
import app.gibiflix.ui.theme.LocalProfile
import app.gibiflix.ui.theme.accent

@Composable
fun GibiLogo(size: Dp, modifier: Modifier = Modifier) {
    val mono = LocalProfile.current == Profile.EINK_MONO
    Image(
        painter = painterResource(if (mono) R.drawable.gibiflix_logo_mono else R.drawable.gibiflix_logo),
        contentDescription = stringResource(R.string.app_name),
        modifier = modifier.size(size),
    )
}

@Composable
fun Wordmark(fontSize: TextUnit = 28.sp, modifier: Modifier = Modifier) {
    val profile = LocalProfile.current
    Text(
        text = stringResource(R.string.wordmark),
        fontFamily = Anton,
        fontSize = fontSize,
        letterSpacing = 2.sp,
        color = if (profile == Profile.EINK_MONO) MaterialTheme.colorScheme.onBackground else profile.accent,
        modifier = modifier,
    )
}

@Composable
fun LogoWithWordmark(modifier: Modifier = Modifier, logoSize: Dp = 36.dp, fontSize: TextUnit = 28.sp) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        GibiLogo(logoSize)
        Wordmark(fontSize)
    }
}
