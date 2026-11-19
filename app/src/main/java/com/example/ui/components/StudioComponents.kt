package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Compress
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.PhotoSizeSelectLarge
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.converter.FileHelper
import com.example.data.converter.SampleFileGenerator
import com.example.domain.model.ConversionOptions
import com.example.domain.model.ConversionOutput
import com.example.domain.model.FileFormat
import com.example.domain.model.FormatCategory
import com.example.domain.model.ImageResizePreset
import com.example.domain.model.PdfDpiPreset
import com.example.domain.model.PdfPageSize
import com.example.domain.model.PickedFile
import com.example.domain.model.ResizeMode
import com.example.ui.theme.BadgeArchive
import com.example.ui.theme.BadgeImage
import com.example.ui.theme.BadgePdf
import com.example.ui.theme.BadgeSheet
import com.example.ui.theme.BadgeText
import com.example.ui.theme.BadgeWord
import com.example.ui.theme.EmeraldSuccess
import com.example.ui.theme.HeroDeep
import com.example.ui.theme.HeroDeepAccent
import kotlin.math.roundToInt

fun formatBadgeColor(ext: String): Color {
    return when (ext.lowercase()) {
        "pdf" -> BadgePdf
        "docx" -> BadgeWord
        "xlsx", "csv", "tsv", "json" -> BadgeSheet
        "png", "jpg", "jpeg", "webp", "bmp" -> BadgeImage
        "zip" -> BadgeArchive
        else -> BadgeText
    }
}

@Composable
fun FormatBadge(
    extension: String,
    modifier: Modifier = Modifier
) {
    val tint = formatBadgeColor(extension)
    Surface(
        modifier = modifier,
        color = tint.copy(alpha = 0.14f),
        shape = RoundedCornerShape(10.dp)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 9.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(6.dp)
                    .clip(CircleShape)
                    .background(tint)
            )
            Text(
                text = extension.uppercase(),
                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                color = tint
            )
        }
    }
}

@Composable
fun SavingsPill(
    savingsPercent: Int,
    modifier: Modifier = Modifier
) {
    val isSmaller = savingsPercent > 0
    val bg = if (isSmaller) EmeraldSuccess.copy(alpha = 0.15f)
    else MaterialTheme.colorScheme.surfaceVariant
    val fg = if (isSmaller) EmeraldSuccess
    else MaterialTheme.colorScheme.onSurfaceVariant

    Surface(
        modifier = modifier,
        color = bg,
        shape = RoundedCornerShape(20.dp)
    ) {
        Text(
            text = when {
                savingsPercent > 0 -> "-$savingsPercent% smaller"
                savingsPercent < 0 -> "+${-savingsPercent}% size"
                else -> "0% change"
            },
            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
            color = fg,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
        )
    }
}

@Composable
fun StudioPillChoice(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    accentColor: Color = MaterialTheme.colorScheme.primary
) {
    val bg = if (selected) accentColor else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f)
    val fg = if (selected) Color.White else MaterialTheme.colorScheme.onSurfaceVariant

    Surface(
        color = bg,
        shape = RoundedCornerShape(12.dp),
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .minimumInteractiveComponentSize()
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            color = fg,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun StudioOptionsPanel(
    primaryFile: PickedFile?,
    targetFormat: FileFormat?,
    options: ConversionOptions,
    onUpdateOptions: ((ConversionOptions) -> ConversionOptions) -> Unit,
    modifier: Modifier = Modifier
) {
    val isImageTarget = targetFormat?.category == FormatCategory.IMAGE
    val isPdfTarget = targetFormat == FileFormat.PDF
    val isPdfSource = primaryFile?.format == FileFormat.PDF
    val showImageControls = isImageTarget && primaryFile?.format?.category == FormatCategory.IMAGE
    val showPdfQualityControls = isPdfTarget || (isPdfSource && isImageTarget)

    if (!showImageControls && !showPdfQualityControls && !isPdfSource) return

    Surface(
        modifier = modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(22.dp),
        tonalElevation = 2.dp
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(
                        color = MaterialTheme.colorScheme.primaryContainer,
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Tune,
                            contentDescription = "Optimization controls",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(7.dp).size(18.dp)
                        )
                    }
                    Spacer(Modifier.width(10.dp))
                    Column {
                        Text(
                            text = "Fine-Tune Output",
                            style = MaterialTheme.typography.titleMedium
                        )
                        Text(
                            text = "Quality, size limits & dimensions",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            if (showImageControls) {
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    Icons.Default.Compress,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp),
                                    tint = MaterialTheme.colorScheme.primary
                                )
                                Spacer(Modifier.width(6.dp))
                                Text("Compression", style = MaterialTheme.typography.titleSmall)
                            }
                            Surface(
                                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.14f),
                                shape = RoundedCornerShape(20.dp)
                            ) {
                                Text(
                                    text = if (options.targetSizeKb != null) "≤ ${options.targetSizeKb} KB" else "${options.quality}%",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                                )
                            }
                        }

                        Slider(
                            value = options.quality.toFloat(),
                            onValueChange = { v ->
                                onUpdateOptions { it.copy(quality = v.roundToInt(), targetSizeKb = null) }
                            },
                            valueRange = 15f..100f,
                            colors = SliderDefaults.colors(
                                thumbColor = MaterialTheme.colorScheme.primary,
                                activeTrackColor = MaterialTheme.colorScheme.primary
                            ),
                            modifier = Modifier.testTag("quality_slider")
                        )

                        if (targetFormat == FileFormat.JPG || targetFormat == FileFormat.WEBP) {
                            Text(
                                text = "Quick target file size limit",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Row(
                                modifier = Modifier.horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                listOf(
                                    null to "Manual",
                                    100 to "100 KB",
                                    250 to "250 KB",
                                    500 to "500 KB",
                                    1024 to "1 MB"
                                ).forEach { (kb, label) ->
                                    StudioPillChoice(
                                        label = label,
                                        selected = options.targetSizeKb == kb,
                                        onClick = { onUpdateOptions { it.copy(targetSizeKb = kb) } },
                                        modifier = Modifier.testTag("target_kb_${kb ?: "manual"}")
                                    )
                                }
                            }
                        }
                    }
                }

                // Resize section
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(
                                Icons.Default.PhotoSizeSelectLarge,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp),
                                tint = MaterialTheme.colorScheme.secondary
                            )
                            Spacer(Modifier.width(6.dp))
                            Text("Resize", style = MaterialTheme.typography.titleSmall)
                            if (primaryFile?.widthPx != null && primaryFile.heightPx != null) {
                                Spacer(Modifier.weight(1f))
                                Text(
                                    text = "${primaryFile.widthPx}×${primaryFile.heightPx}px",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            ResizeMode.entries.forEach { mode ->
                                StudioPillChoice(
                                    label = mode.label,
                                    selected = options.resizeMode == mode,
                                    onClick = { onUpdateOptions { it.copy(resizeMode = mode) } },
                                    accentColor = MaterialTheme.colorScheme.secondary,
                                    modifier = Modifier.testTag("resize_mode_${mode.name.lowercase()}")
                                )
                            }
                        }

                        AnimatedVisibility(
                            visible = options.resizeMode == ResizeMode.PERCENTAGE,
                            enter = expandVertically() + fadeIn(),
                            exit = shrinkVertically() + fadeOut()
                        ) {
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.padding(top = 4.dp)
                            ) {
                                listOf(25, 50, 75, 100).forEach { pct ->
                                    StudioPillChoice(
                                        label = "$pct%",
                                        selected = options.scalePercent == pct,
                                        onClick = { onUpdateOptions { it.copy(scalePercent = pct) } }
                                    )
                                }
                            }
                        }

                        AnimatedVisibility(
                            visible = options.resizeMode == ResizeMode.PRESET,
                            enter = expandVertically() + fadeIn(),
                            exit = shrinkVertically() + fadeOut()
                        ) {
                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.padding(top = 4.dp)
                            ) {
                                ImageResizePreset.entries.forEach { preset ->
                                    StudioPillChoice(
                                        label = preset.label,
                                        selected = options.resizePreset == preset,
                                        onClick = { onUpdateOptions { it.copy(resizePreset = preset) } }
                                    )
                                }
                            }
                        }

                        AnimatedVisibility(
                            visible = options.resizeMode == ResizeMode.DIMENSIONS,
                            enter = expandVertically() + fadeIn(),
                            exit = shrinkVertically() + fadeOut()
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                OutlinedTextField(
                                    value = options.targetWidth?.toString() ?: "",
                                    onValueChange = { raw ->
                                        val w = raw.filter { it.isDigit() }.toIntOrNull()
                                        onUpdateOptions { it.copy(targetWidth = w) }
                                    },
                                    label = { Text("Width px") },
                                    singleLine = true,
                                    shape = RoundedCornerShape(14.dp),
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                    modifier = Modifier.weight(1f).testTag("resize_width_input")
                                )
                                IconButton(
                                    onClick = { onUpdateOptions { it.copy(keepAspectRatio = !it.keepAspectRatio) } },
                                    modifier = Modifier.minimumInteractiveComponentSize()
                                ) {
                                    Icon(
                                        imageVector = if (options.keepAspectRatio) Icons.Default.Lock else Icons.Default.LockOpen,
                                        contentDescription = if (options.keepAspectRatio) "Aspect ratio locked" else "Aspect ratio unlocked",
                                        tint = if (options.keepAspectRatio) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                OutlinedTextField(
                                    value = options.targetHeight?.toString() ?: "",
                                    onValueChange = { raw ->
                                        val h = raw.filter { it.isDigit() }.toIntOrNull()
                                        onUpdateOptions { it.copy(targetHeight = h) }
                                    },
                                    label = { Text("Height px") },
                                    singleLine = true,
                                    shape = RoundedCornerShape(14.dp),
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                    modifier = Modifier.weight(1f).testTag("resize_height_input")
                                )
                            }
                        }
                    }
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Strip EXIF Metadata", style = MaterialTheme.typography.titleSmall)
                        Text(
                            "Removes camera & GPS tags",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = options.stripExif,
                        onCheckedChange = { checked -> onUpdateOptions { it.copy(stripExif = checked) } },
                        modifier = Modifier.testTag("strip_exif_switch")
                    )
                }
            }

            if (showPdfQualityControls || isPdfSource) {
                if (isPdfTarget) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("PDF Page Size", style = MaterialTheme.typography.titleSmall)
                        Row(
                            modifier = Modifier.horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            PdfPageSize.entries.forEach { size ->
                                StudioPillChoice(
                                    label = size.label,
                                    selected = options.pdfPageSize == size,
                                    onClick = { onUpdateOptions { it.copy(pdfPageSize = size) } },
                                    modifier = Modifier.testTag("pdf_page_${size.name.lowercase()}")
                                )
                            }
                        }
                    }
                }

                if (isPdfSource && (isPdfTarget || isImageTarget)) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Render Resolution", style = MaterialTheme.typography.titleSmall)
                        Row(
                            modifier = Modifier.horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            PdfDpiPreset.entries.forEach { dpi ->
                                StudioPillChoice(
                                    label = dpi.label,
                                    selected = options.pdfDpi == dpi,
                                    onClick = { onUpdateOptions { it.copy(pdfDpi = dpi) } },
                                    modifier = Modifier.testTag("pdf_dpi_${dpi.dpi}")
                                )
                            }
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("PDF Quality", style = MaterialTheme.typography.titleSmall)
                        Text(
                            "${options.quality}%",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    Slider(
                        value = options.quality.toFloat(),
                        onValueChange = { v -> onUpdateOptions { it.copy(quality = v.roundToInt()) } },
                        valueRange = 20f..100f
                    )
                }

                if (isPdfSource && isPdfTarget) {
                    OutlinedTextField(
                        value = options.pageRange,
                        onValueChange = { txt -> onUpdateOptions { it.copy(pageRange = txt) } },
                        label = { Text("Page Range (optional, e.g. 1-3, 5)") },
                        placeholder = { Text("All pages") },
                        singleLine = true,
                        shape = RoundedCornerShape(14.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant
                        ),
                        modifier = Modifier.fillMaxWidth().testTag("pdf_page_range_input")
                    )
                }
            }
        }
    }
}

@Composable
fun ConversionResultCard(
    output: ConversionOutput,
    onPreview: () -> Unit,
    onShare: () -> Unit,
    onSaveCopy: () -> Unit,
    onOpenWith: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.fillMaxWidth().testTag("conversion_result_card"),
        shape = RoundedCornerShape(24.dp),
        color = HeroDeep
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.linearGradient(
                        colors = listOf(HeroDeep, HeroDeepAccent)
                    )
                )
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f)
                    ) {
                        Surface(
                            color = EmeraldSuccess.copy(alpha = 0.2f),
                            shape = CircleShape
                        ) {
                            Icon(
                                imageVector = Icons.Default.CheckCircle,
                                contentDescription = "Conversion succeeded",
                                tint = Color(0xFF34E8B5),
                                modifier = Modifier.padding(6.dp).size(20.dp)
                            )
                        }
                        Spacer(Modifier.width(12.dp))
                        Column {
                            Text(
                                text = output.outputFile.name,
                                style = MaterialTheme.typography.titleMedium,
                                color = Color.White,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = "Ready in ${output.durationMs} ms",
                                style = MaterialTheme.typography.bodySmall,
                                color = Color(0xFF9EACC7)
                            )
                        }
                    }
                    FormatBadge(output.outputFormat.ext)
                }

                // Split Before / After metrics
                Surface(
                    color = Color.White.copy(alpha = 0.07f),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text("INPUT", style = MaterialTheme.typography.labelSmall, color = Color(0xFF8B99B5))
                            Text(
                                text = FileHelper.formatSize(output.originalSizeBytes),
                                style = MaterialTheme.typography.titleMedium,
                                color = Color(0xFFD5DEF0)
                            )
                        }
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            SavingsPill(output.savingsPercent)
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Text("OUTPUT", style = MaterialTheme.typography.labelSmall, color = Color(0xFF8B99B5))
                            Text(
                                text = FileHelper.formatSize(output.outputSizeBytes),
                                style = MaterialTheme.typography.titleMedium,
                                color = Color.White
                            )
                        }
                    }
                }

                Text(
                    text = output.summary,
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(0xFFB7C3DB)
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    ResultActionTile(
                        icon = Icons.Default.Visibility,
                        label = "Preview",
                        highlighted = true,
                        onClick = onPreview,
                        modifier = Modifier.weight(1f).testTag("result_preview_button")
                    )
                    ResultActionTile(
                        icon = Icons.Default.Share,
                        label = "Share",
                        highlighted = false,
                        onClick = onShare,
                        modifier = Modifier.weight(1f).testTag("result_share_button")
                    )
                    ResultActionTile(
                        icon = Icons.Default.Download,
                        label = "Save",
                        highlighted = false,
                        onClick = onSaveCopy,
                        modifier = Modifier.weight(1f).testTag("result_save_button")
                    )
                    ResultActionTile(
                        icon = Icons.AutoMirrored.Filled.OpenInNew,
                        label = "Open",
                        highlighted = false,
                        onClick = onOpenWith,
                        modifier = Modifier.weight(1f).testTag("result_open_button")
                    )
                }
            }
        }
    }
}

@Composable
private fun ResultActionTile(
    icon: ImageVector,
    label: String,
    highlighted: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val bg = if (highlighted) Color(0xFF3554FF) else Color.White.copy(alpha = 0.10f)
    Surface(
        color = bg,
        shape = RoundedCornerShape(14.dp),
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .minimumInteractiveComponentSize()
    ) {
        Column(
            modifier = Modifier.padding(vertical = 10.dp, horizontal = 4.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = Color.White,
                modifier = Modifier.size(18.dp)
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = Color.White
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SamplePickerBottomSheet(
    onSelectSample: (SampleFileGenerator.SamplePreset) -> Unit,
    onDismiss: () -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                text = "Generate Sample File",
                style = MaterialTheme.typography.headlineSmall
            )
            Text(
                text = "Creates a real local file so you can try any conversion immediately.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(4.dp))

            SampleFileGenerator.SamplePreset.entries.forEachIndexed { idx, preset ->
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.48f),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .clickable { onSelectSample(preset) }
                        .minimumInteractiveComponentSize()
                        .testTag("sample_${preset.format.ext}")
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        FormatBadge(preset.format.ext)
                        Column(modifier = Modifier.weight(1f)) {
                            Text(preset.title, style = MaterialTheme.typography.titleSmall)
                            Text(
                                preset.subtitle,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
                if (idx == SampleFileGenerator.SamplePreset.entries.lastIndex) {
                    Spacer(Modifier.height(4.dp))
                }
            }
        }
    }
}
