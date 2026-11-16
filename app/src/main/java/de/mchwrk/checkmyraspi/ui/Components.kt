package de.mchwrk.checkmyraspi.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import de.mchwrk.checkmyraspi.data.Reachability
import de.mchwrk.checkmyraspi.data.Severity
import de.mchwrk.checkmyraspi.design.Mchwrk

@Composable
fun reachColor(r: Reachability): Color = when (r) {
    Reachability.ONLINE -> Mchwrk.colors.good
    Reachability.OFFLINE -> MaterialTheme.colorScheme.error
    Reachability.ERROR -> Mchwrk.colors.warn
    Reachability.UNKNOWN -> Mchwrk.colors.muted
}

@Composable
fun severityColor(s: Severity): Color = when (s) {
    Severity.CRITICAL -> MaterialTheme.colorScheme.error
    Severity.WARN -> Mchwrk.colors.warn
    Severity.INFO -> MaterialTheme.colorScheme.primary
}

@Composable
fun StatusDot(r: Reachability, modifier: Modifier = Modifier) {
    Box(modifier.size(10.dp).clip(CircleShape).background(reachColor(r)))
}

@Composable
fun Badge(text: String, color: Color) {
    Surface(
        shape = RoundedCornerShape(6.dp),
        color = color.copy(alpha = 0.12f),
        contentColor = color,
    ) {
        Text(text, style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
    }
}

/** Abschnitt mit dünnem Rand statt Schatten (mchwrk-Stil). */
@Composable
fun SectionCard(title: String?, modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.medium)
            .background(MaterialTheme.colorScheme.surface)
            .border(1.dp, Mchwrk.colors.line, MaterialTheme.shapes.medium)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (title != null) {
            Text(title.uppercase(), style = MaterialTheme.typography.labelSmall, color = Mchwrk.colors.muted)
        }
        content()
    }
}

@Composable
fun KeyValue(key: String, value: String?, mono: Boolean = false) {
    if (value.isNullOrBlank()) return
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
        Text(key, style = MaterialTheme.typography.bodyMedium, color = Mchwrk.colors.muted, modifier = Modifier.width(120.dp))
        Text(
            value,
            style = MaterialTheme.typography.bodyMedium,
            fontFamily = if (mono) FontFamily.Monospace else null,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
fun UsageBar(label: String, percent: Int, detail: String) {
    val color = when {
        percent >= 90 -> MaterialTheme.colorScheme.error
        percent >= 75 -> Mchwrk.colors.warn
        else -> MaterialTheme.colorScheme.primary
    }
    Column(Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth()) {
            Text(label, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
            Text("$percent %", style = MaterialTheme.typography.bodyMedium, color = color)
        }
        Spacer(Modifier.height(4.dp))
        Box(Modifier.fillMaxWidth().height(6.dp).clip(CircleShape).background(Mchwrk.colors.line)) {
            Box(Modifier.fillMaxWidth(percent.coerceIn(0, 100) / 100f).height(6.dp).clip(CircleShape).background(color))
        }
        Spacer(Modifier.height(2.dp))
        Text(detail, style = MaterialTheme.typography.bodySmall, color = Mchwrk.colors.muted)
    }
}

@Composable
fun SecretField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    supporting: String? = null,
) {
    var visible by rememberSaveable { mutableStateOf(false) }
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        singleLine = true,
        visualTransformation = if (visible) VisualTransformation.None else PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
        trailingIcon = {
            IconButton(onClick = { visible = !visible }) {
                Icon(
                    if (visible) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility,
                    contentDescription = if (visible) "Verbergen" else "Anzeigen",
                )
            }
        },
        supportingText = supporting?.let { { Text(it) } },
        modifier = modifier.fillMaxWidth(),
    )
}
