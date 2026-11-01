package com.tingyun.smartmistakebook.core.ui

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.outlined.Biotech
import androidx.compose.material.icons.outlined.Bolt
import androidx.compose.material.icons.outlined.AccountBalance
import androidx.compose.material.icons.outlined.Functions
import androidx.compose.material.icons.outlined.HistoryEdu
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material.icons.outlined.PhoneAndroid
import androidx.compose.material.icons.outlined.Public
import androidx.compose.material.icons.outlined.Science
import androidx.compose.material.icons.outlined.Translate
import com.tingyun.smartmistakebook.core.ui.ClaudeButton as Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import com.tingyun.smartmistakebook.core.ui.ClaudeSecondaryButton as OutlinedButton
import androidx.compose.material3.Text
import com.tingyun.smartmistakebook.core.ui.ClaudeTextButton as TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.tingyun.smartmistakebook.core.model.SubjectKind

@Composable
@OptIn(ExperimentalLayoutApi::class)
fun SectionHeader(
    title: String,
    modifier: Modifier = Modifier,
    action: (@Composable () -> Unit)? = null,
) {
    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = 40.dp),
    ) {
        if (action != null && (maxWidth < 300.dp || LocalDensity.current.fontScale > 1.3f)) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(title, color = Ink, style = MaterialTheme.typography.titleMedium)
                action()
            }
        } else {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(title, Modifier.weight(1f), color = Ink, style = MaterialTheme.typography.titleMedium)
                action?.invoke()
            }
        }
    }
}

@Composable
fun InlineActionButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    contentDescription: String = text,
) {
    TextButton(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier
            .defaultMinSize(minHeight = SmartDimens.MinimumTouchTarget, minWidth = SmartDimens.MinimumTouchTarget)
            .semantics { this.contentDescription = contentDescription },
        colors = ButtonDefaults.textButtonColors(contentColor = Ink, disabledContentColor = InkMuted),
        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp),
    ) {
        Text(text, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
fun ScreenStatePanel(
    icon: ImageVector,
    title: String,
    supportingText: String? = null,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = 96.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(ContentSurface)
            .padding(horizontal = 20.dp, vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(Modifier.size(52.dp).clip(RoundedCornerShape(16.dp)).background(JadeSoft),
            contentAlignment = Alignment.Center) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(26.dp),
            tint = JadeDark,
        )
        }
        Spacer(Modifier.height(10.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            color = Ink,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center,
        )
        if (supportingText != null) {
            Spacer(Modifier.height(4.dp))
            Text(
                text = supportingText,
                style = MaterialTheme.typography.bodyMedium,
                color = InkSecondary,
                textAlign = TextAlign.Center,
            )
        }
        if (actionLabel != null && onAction != null) {
            Spacer(Modifier.height(16.dp))
            OutlinedButton(
                onClick = onAction,
                modifier = Modifier.defaultMinSize(minHeight = 48.dp),
            ) {
                Text(actionLabel)
            }
        }
    }
}

@Composable
fun LocalModeLine(
    text: String,
    modifier: Modifier = Modifier,
    icon: ImageVector = Icons.Outlined.PhoneAndroid,
    contentDescription: String = "本地数据状态",
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = 32.dp)
            .semantics(mergeDescendants = true) {
                this.contentDescription = "$contentDescription：$text"
            },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(24.dp),
            tint = JadeActive,
        )
        Spacer(Modifier.width(10.dp))
        Text(
            text = text,
            color = JadeActive,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
        )
    }
}

@Composable
fun PrimaryActionButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    enabled: Boolean = true,
    contentDescription: String = text,
) {
    Button(
        onClick = onClick,
        modifier = modifier
            .defaultMinSize(minHeight = 48.dp)
            .semantics {
                this.contentDescription = contentDescription
                role = Role.Button
            },
        enabled = enabled,
        shape = RoundedCornerShape(SmartDimens.CallToActionRadius),
        colors = ButtonDefaults.buttonColors(
            containerColor = Jade,
            contentColor = OnJade,
            disabledContainerColor = JadeSoft,
            disabledContentColor = InkMuted,
        ),
        elevation = ButtonDefaults.buttonElevation(
            defaultElevation = 0.dp,
            pressedElevation = 0.dp,
            focusedElevation = 0.dp,
            hoveredElevation = 0.dp,
            disabledElevation = 0.dp,
        ),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 10.dp),
    ) {
        if (icon != null) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(SmartIconSizes.Standard),
            )
            Spacer(Modifier.width(8.dp))
        }
        Text(text = text, style = MaterialTheme.typography.labelLarge)
    }
}

@Composable
fun OutlineActionChip(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    selected: Boolean? = null,
    enabled: Boolean = true,
    contentDescription: String = text,
) {
    OutlinedButton(
        onClick = onClick,
        modifier = modifier
            .defaultMinSize(minHeight = SmartDimens.MinimumTouchTarget)
            .semantics {
                this.contentDescription = contentDescription
                role = Role.Button
                selected?.let { this.selected = it }
            },
        enabled = enabled,
        shape = RoundedCornerShape(SmartDimens.ChipRadius),
        border = null,
        colors = ButtonDefaults.buttonColors(
            containerColor = if (selected == true) JadeSoft else Ink.copy(alpha = .06f),
            contentColor = if (selected == true) JadeActive else InkSecondary,
            disabledContentColor = InkMuted,
        ),
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
    ) {
        if (icon != null) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(SmartIconSizes.Standard),
            )
            Spacer(Modifier.width(7.dp))
        }
        Text(text = text, style = MaterialTheme.typography.labelMedium)
    }
}

private fun SubjectKind.icon(): ImageVector = when (this) {
    SubjectKind.CHINESE -> Icons.Outlined.Translate
    SubjectKind.MATH -> Icons.Outlined.Functions
    SubjectKind.ENGLISH -> Icons.Outlined.Language
    SubjectKind.PHYSICS -> Icons.Outlined.Bolt
    SubjectKind.CHEMISTRY -> Icons.Outlined.Science
    SubjectKind.BIOLOGY -> Icons.Outlined.Biotech
    SubjectKind.POLITICS -> Icons.Outlined.AccountBalance
    SubjectKind.HISTORY -> Icons.Outlined.HistoryEdu
    SubjectKind.GEOGRAPHY -> Icons.Outlined.Public
    SubjectKind.GENERAL -> Icons.AutoMirrored.Outlined.MenuBook
}

@Composable
fun SubjectIcon(
    subject: SubjectKind,
    modifier: Modifier = Modifier,
    contentDescription: String = "${subject.studentLabel()}学科",
) {
    val (surface, accent) = when (subject) {
        SubjectKind.CHINESE, SubjectKind.HISTORY, SubjectKind.POLITICS ->
            SmartColors.ReadingSurface to SmartColors.ReadingAccent
        SubjectKind.MATH, SubjectKind.PHYSICS, SubjectKind.CHEMISTRY, SubjectKind.ENGLISH ->
            SmartColors.LearningSurface to SmartColors.LearningAccent
        else -> JadeSoft to JadeDark
    }
    SubjectIcon(
        icon = subject.icon(),
        contentDescription = contentDescription,
        modifier = modifier,
        containerColor = surface,
        tint = accent,
    )
}

@Composable
fun SubjectIcon(
    icon: ImageVector,
    contentDescription: String,
    modifier: Modifier = Modifier,
    containerColor: Color = SubtleSurface,
    tint: Color = InkSecondary,
) {
    Box(
        modifier = modifier
            .size(36.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(containerColor)
            .semantics { this.contentDescription = contentDescription },
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(SmartIconSizes.Standard),
            tint = tint,
        )
    }
}

@Composable
fun PaperDivider(
    modifier: Modifier = Modifier,
    color: Color = Divider,
) {
    HorizontalDivider(
        modifier = modifier,
        thickness = 1.dp,
        color = color,
    )
}

@Composable
fun RootPageColumn(
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(
        start = SmartDimens.ContentHorizontalPadding,
        top = 8.dp,
        end = SmartDimens.ContentHorizontalPadding,
        bottom = 12.dp,
    ),
    scrollState: ScrollState = rememberScrollState(),
    content: @Composable ColumnScope.() -> Unit,
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .imePadding()
            .background(Paper),
        contentAlignment = Alignment.TopCenter,
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = SmartDimens.MaximumContentWidth)
                .fillMaxSize()
                .verticalScroll(scrollState)
                .padding(contentPadding),
            verticalArrangement = Arrangement.Top,
            content = content,
        )
    }
}

@Composable
fun RootPageLazyColumn(
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(
        start = SmartDimens.ContentHorizontalPadding,
        top = 8.dp,
        end = SmartDimens.ContentHorizontalPadding,
        bottom = 12.dp,
    ),
    listState: LazyListState = rememberLazyListState(),
    content: LazyListScope.() -> Unit,
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .imePadding()
            .background(Paper),
        contentAlignment = Alignment.TopCenter,
    ) {
        LazyColumn(
            modifier = Modifier
                .widthIn(max = SmartDimens.MaximumContentWidth)
                .fillMaxSize(),
            state = listState,
            contentPadding = contentPadding,
            verticalArrangement = Arrangement.Top,
            content = content,
        )
    }
}
