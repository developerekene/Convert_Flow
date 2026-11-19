package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.CallSplit
import androidx.compose.material.icons.filled.AspectRatio
import androidx.compose.material.icons.filled.Collections
import androidx.compose.material.icons.filled.Compress
import androidx.compose.material.icons.filled.FolderZip
import androidx.compose.material.icons.filled.MergeType
import androidx.compose.material.icons.filled.PhotoSizeSelectLarge
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.TableChart
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.domain.model.FormatCategory
import com.example.domain.model.StudioTool
import com.example.ui.theme.BadgeArchive
import com.example.ui.theme.BadgeImage
import com.example.ui.theme.BadgePdf
import com.example.ui.theme.BadgeSheet
import com.example.ui.theme.HeroDeep
import com.example.ui.theme.HeroDeepAccent

@Composable
fun ToolsScreen(
    onLaunchTool: (StudioTool) -> Unit,
    modifier: Modifier = Modifier
) {
    val featured = listOf(StudioTool.COMPRESS_IMAGE, StudioTool.COMPRESS_PDF)
    val remaining = StudioTool.entries.filterNot { it in featured }

    BoxWithConstraints(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.TopCenter
    ) {
        val columns = if (maxWidth >= 600.dp) 2 else 2
        val groupedRows = remaining.chunked(columns)

        LazyColumn(
            modifier = Modifier
                .widthIn(max = 840.dp)
                .fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 18.dp, vertical = 18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = "Studio Tools",
                        style = MaterialTheme.typography.displaySmall.copy(fontWeight = FontWeight.Bold)
                    )
                    Text(
                        text = "Focused pipelines for compression, resizing, PDFs, spreadsheets, and ZIP.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // Asymmetric Featured Bento Pair
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    featured.forEach { tool ->
                        FeaturedBentoToolCard(
                            tool = tool,
                            onClick = { onLaunchTool(tool) },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }

            item {
                Text(
                    text = "All Pipelines",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }

            items(groupedRows) { rowTools ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    rowTools.forEach { tool ->
                        BentoToolCard(
                            tool = tool,
                            onClick = { onLaunchTool(tool) },
                            modifier = Modifier.weight(1f)
                        )
                    }
                    if (rowTools.size < columns) {
                        Spacer(Modifier.weight(1f))
                    }
                }
            }

            item { Spacer(Modifier.height(12.dp)) }
        }
    }
}

@Composable
private fun FeaturedBentoToolCard(
    tool: StudioTool,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val accent = toolAccentColor(tool.category)
    Surface(
        shape = RoundedCornerShape(22.dp),
        color = HeroDeep,
        modifier = modifier
            .clip(RoundedCornerShape(22.dp))
            .clickable(onClick = onClick)
            .minimumInteractiveComponentSize()
            .testTag("tool_${tool.name.lowercase()}")
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.linearGradient(
                        listOf(HeroDeep, HeroDeepAccent, accent.copy(alpha = 0.42f))
                    )
                )
                .padding(16.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        color = accent.copy(alpha = 0.25f),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(
                            imageVector = toolIcon(tool),
                            contentDescription = tool.title,
                            tint = Color.White,
                            modifier = Modifier.padding(9.dp).size(20.dp)
                        )
                    }
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                        contentDescription = null,
                        tint = Color.White.copy(alpha = 0.7f),
                        modifier = Modifier.size(16.dp)
                    )
                }

                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = tool.title,
                        style = MaterialTheme.typography.titleMedium,
                        color = Color.White,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = tool.subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = Color(0xFFB7C4DE),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

@Composable
private fun BentoToolCard(
    tool: StudioTool,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val accent = toolAccentColor(tool.category)
    Surface(
        modifier = modifier
            .clip(RoundedCornerShape(20.dp))
            .clickable(onClick = onClick)
            .minimumInteractiveComponentSize()
            .testTag("tool_${tool.name.lowercase()}"),
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 1.dp
    ) {
        Column(
            modifier = Modifier.padding(15.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    color = accent.copy(alpha = 0.14f),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(
                        imageVector = toolIcon(tool),
                        contentDescription = tool.title,
                        tint = accent,
                        modifier = Modifier.padding(9.dp).size(20.dp)
                    )
                }

                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.75f),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(
                        text = tool.badge,
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp)
                    )
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(
                    text = tool.title,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = tool.subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

private fun toolAccentColor(category: FormatCategory): Color = when (category) {
    FormatCategory.DOCUMENT -> BadgePdf
    FormatCategory.SPREADSHEET -> BadgeSheet
    FormatCategory.IMAGE -> BadgeImage
    FormatCategory.ARCHIVE -> BadgeArchive
}

private fun toolIcon(tool: StudioTool): ImageVector = when (tool) {
    StudioTool.COMPRESS_IMAGE -> Icons.Default.Compress
    StudioTool.RESIZE_IMAGE -> Icons.Default.PhotoSizeSelectLarge
    StudioTool.IMAGES_TO_PDF -> Icons.Default.PictureAsPdf
    StudioTool.PDF_TO_IMAGES -> Icons.Default.Collections
    StudioTool.COMPRESS_PDF -> Icons.Default.Compress
    StudioTool.RESIZE_PDF -> Icons.Default.AspectRatio
    StudioTool.MERGE_PDF -> Icons.Default.MergeType
    StudioTool.SPLIT_PDF -> Icons.AutoMirrored.Filled.CallSplit
    StudioTool.SPREADSHEET_STUDIO -> Icons.Default.TableChart
    StudioTool.ZIP_ARCHIVE -> Icons.Default.FolderZip
}
