package com.tingyun.smartmistakebook.core.ui

// Caho1/open-claude-ui cds-theme.css / ChatComposer.tsx, c1f923ae; MIT (Copyright 2026 Caho1).
import androidx.compose.foundation.background
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.material3.TextFieldColors
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.takeOrElse
import androidx.compose.ui.semantics.error
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp

/** Native editing/IME, source-derived compact field paint and an external persistent label. */
@Composable
fun ClaudeTextField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    readOnly: Boolean = false,
    textStyle: TextStyle = MaterialTheme.typography.bodyLarge,
    label: (@Composable () -> Unit)? = null,
    placeholder: (@Composable () -> Unit)? = null,
    leadingIcon: (@Composable () -> Unit)? = null,
    trailingIcon: (@Composable () -> Unit)? = null,
    prefix: (@Composable () -> Unit)? = null,
    suffix: (@Composable () -> Unit)? = null,
    supportingText: (@Composable () -> Unit)? = null,
    isError: Boolean = false,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
    singleLine: Boolean = false,
    maxLines: Int = if (singleLine) 1 else Int.MAX_VALUE,
    minLines: Int = 1,
    interactionSource: MutableInteractionSource? = null,
    shape: Shape = RoundedCornerShape(14.dp),
    colors: TextFieldColors = OutlinedTextFieldDefaults.colors(
        focusedTextColor = Ink, unfocusedTextColor = Ink,
        focusedContainerColor = ContentSurface, unfocusedContainerColor = ContentSurface,
        focusedBorderColor = Jade, unfocusedBorderColor = Outline,
        cursorColor = Jade, focusedLabelColor = InkSecondary, unfocusedLabelColor = InkSecondary,
    ),
) {
    val source = interactionSource ?: remember { MutableInteractionSource() }
    val focused by source.collectIsFocusedAsState()
    val foreground = when { !enabled -> colors.disabledTextColor; isError -> colors.errorTextColor
        focused -> colors.focusedTextColor; else -> colors.unfocusedTextColor }
    val motion = LocalClaudeMotionEnabled.current
    val background by animateColorAsState(
        when { !enabled -> colors.disabledContainerColor; isError -> colors.errorContainerColor
            focused -> colors.focusedContainerColor; else -> colors.unfocusedContainerColor },
        ClaudeMotion.spec(motion), label = "cds-field-surface")
    val outline by animateColorAsState(
        when { !enabled -> colors.disabledIndicatorColor; isError -> colors.errorIndicatorColor
            focused -> colors.focusedIndicatorColor; else -> colors.unfocusedIndicatorColor },
        ClaudeMotion.spec(motion), label = "cds-field-outline")
    BasicTextField(
        value = value, onValueChange = onValueChange,
        modifier = modifier.semantics { if (isError) error("输入内容有误") },
        enabled = enabled, readOnly = readOnly,
        textStyle = textStyle.copy(color = textStyle.color.takeOrElse { foreground }),
        keyboardOptions = keyboardOptions, keyboardActions = keyboardActions,
        singleLine = singleLine, maxLines = maxLines, minLines = minLines,
        visualTransformation = visualTransformation, interactionSource = source,
        cursorBrush = SolidColor(if (isError) colors.errorCursorColor else colors.cursorColor),
        decorationBox = { innerField ->
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                if (label != null) {
                    CompositionLocalProvider(LocalContentColor provides InkSecondary) {
                        ProvideTextStyle(MaterialTheme.typography.bodySmall, label)
                    }
                }
                Row(
                    Modifier.defaultMinSize(minWidth = 120.dp, minHeight = 48.dp)
                        .background(background, shape).border(if (focused) 2.dp else 1.dp, outline, shape)
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    CompositionLocalProvider(LocalContentColor provides InkSecondary) { leadingIcon?.invoke() }
                    prefix?.invoke()
                    Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                        if (value.isEmpty() && placeholder != null) {
                            CompositionLocalProvider(LocalContentColor provides InkSecondary) {
                                ProvideTextStyle(textStyle, placeholder)
                            }
                        }
                        innerField()
                    }
                    suffix?.invoke()
                    CompositionLocalProvider(LocalContentColor provides InkSecondary) { trailingIcon?.invoke() }
                }
                if (supportingText != null) {
                    CompositionLocalProvider(LocalContentColor provides if (isError) ErrorText else InkSecondary) {
                        ProvideTextStyle(MaterialTheme.typography.labelMedium, supportingText)
                    }
                }
            }
        },
    )
}
