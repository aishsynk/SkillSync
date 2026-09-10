package com.example.skillsync.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.skillsync.theme.NumericInline
import com.example.skillsync.theme.NumericStyle
import com.example.skillsync.theme.Space
import com.example.skillsync.theme.editorialRule
import com.example.skillsync.theme.skill

/**
 * The V3 editorial primitives. One figure, one section header — every screen
 * composes from these so the app reads as one publication rather than twenty
 * forms.
 */

enum class FigureSize { Hero, Large, Medium }

/**
 * A number and what it means. The numeral is Fraunces Light and tabular; the
 * label is Inter caps underneath; an optional delta rides alongside with a
 * direction arrow. No naked figures — [label] is required.
 */
@Composable
fun Figure(
    value: String,
    label: String,
    modifier: Modifier = Modifier,
    delta: String? = null,
    deltaGood: Boolean? = null,
    size: FigureSize = FigureSize.Large,
    tone: Color? = null,
) {
    val sk = MaterialTheme.skill
    val valueStyle = when (size) {
        FigureSize.Hero -> NumericStyle.copy(fontSize = MaterialTheme.typography.displayLarge.fontSize)
        FigureSize.Large -> NumericStyle.copy(fontSize = MaterialTheme.typography.displaySmall.fontSize)
        FigureSize.Medium -> NumericStyle.copy(fontSize = MaterialTheme.typography.headlineMedium.fontSize)
    }
    Column(modifier) {
        Row(verticalAlignment = Alignment.Bottom) {
            Text(value, style = valueStyle, color = tone ?: sk.bodyText, maxLines = 1)
            if (delta != null) {
                Spacer(Modifier.width(Space.sm))
                Text(
                    (if (deltaGood == true) "▲ " else if (deltaGood == false) "▼ " else "") + delta,
                    style = NumericInline.copy(fontSize = MaterialTheme.typography.bodySmall.fontSize),
                    color = when (deltaGood) {
                        true -> sk.aqua; false -> sk.warn; else -> sk.subText
                    },
                    modifier = Modifier.padding(bottom = 4.dp),
                )
            }
        }
        Spacer(Modifier.height(Space.xs))
        Text(
            label.uppercase(),
            style = MaterialTheme.typography.labelSmall,
            color = sk.labelText,
        )
    }
}

/**
 * A section break: a Fraunces title, an optional conclusion sentence in Inter,
 * an optional trailing affordance, and a hairline rule underneath. This is the
 * rhythm of the whole app — conclusion first, evidence below.
 */
@Composable
fun SectionHeader(
    title: String,
    modifier: Modifier = Modifier,
    conclusion: String? = null,
    trailing: (@Composable () -> Unit)? = null,
) {
    val sk = MaterialTheme.skill
    Column(
        modifier
            .fillMaxWidth()
            .padding(top = Space.lg, bottom = Space.md)
            .editorialRule(),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                title,
                style = MaterialTheme.typography.headlineSmall,
                color = sk.bodyText,
                modifier = Modifier.weight(1f),
            )
            if (trailing != null) trailing()
        }
        if (conclusion != null) {
            Spacer(Modifier.height(Space.xs))
            Text(
                conclusion,
                style = MaterialTheme.typography.bodyMedium,
                color = sk.subText,
            )
        }
        Spacer(Modifier.height(Space.md))
    }
}

/** A label above a value, both small — for dense key/value strips inside a card. */
@Composable
fun MicroStat(label: String, value: String, modifier: Modifier = Modifier, tone: Color? = null) {
    val sk = MaterialTheme.skill
    Column(modifier) {
        Text(label.uppercase(), style = MaterialTheme.typography.labelSmall, color = sk.labelText)
        Spacer(Modifier.height(2.dp))
        Text(
            value,
            style = NumericInline.copy(fontSize = MaterialTheme.typography.titleMedium.fontSize),
            color = tone ?: sk.bodyText,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            fontWeight = FontWeight.SemiBold,
        )
    }
}
