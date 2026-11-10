package io.github.kimyeongbean.iter.app

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp

/** A round button with a left chevron; "뒤로" is its accessibility label. */
@Composable
internal fun BackButton(
    onClick: () -> Unit,
    diameter: Dp = IterButtonSize.MEDIUM.height,
) {
    val description = stringResource(R.string.back)
    val iconColor = MaterialTheme.colorScheme.onSecondaryContainer
    val buttonSemantics =
        Modifier.semantics {
            contentDescription = description
            role = Role.Button
        }
    Surface(
        onClick = onClick,
        shape = CircleShape,
        color = MaterialTheme.colorScheme.secondaryContainer,
        modifier = Modifier.size(diameter).then(buttonSemantics),
    ) {
        Canvas(Modifier.fillMaxSize().padding(diameter * 0.3f)) {
            val strokeWidth = size.minDimension * 0.12f
            val tip = Offset(size.width * 0.3f, size.height / 2)
            drawLine(iconColor, Offset(size.width * 0.65f, 0f), tip, strokeWidth, StrokeCap.Round)
            drawLine(iconColor, tip, Offset(size.width * 0.65f, size.height), strokeWidth, StrokeCap.Round)
        }
    }
}
