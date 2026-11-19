package com.example.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.example.data.converter.FileHelper
import com.example.domain.model.ConversionOutput
import com.example.domain.model.ConversionUiState
import com.example.domain.model.FileFormat
import com.example.domain.model.FormatCategory
import com.example.domain.model.PickedFile
import com.example.domain.model.StudioTool
import com.example.ui.components.ConversionResultCard
import com.example.ui.components.FormatBadge
import com.example.ui.components.SamplePickerBottomSheet
import com.example.ui.components.StudioOptionsPanel
import com.example.ui.components.formatBadgeColor
import com.example.ui.theme.CobaltPrimary
import com.example.ui.theme.EmeraldSuccess
import com.example.ui.theme.MintSecondary
import com.example.ui.viewmodel.ConvertFlowViewModel

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ConvertScreen(
    viewModel: ConvertFlowViewModel,
    onOpenPreview: (filePath: String, fileName: String, ext: String) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val pickedFiles by viewModel.pickedFiles.collectAsStateWithLifecycle()
    val availableTargets by viewModel.availableTargets.collectAsStateWithLifecycle()
    val targetFormat by viewModel.targetFormat.collectAsStateWithLifecycle()
    val options by viewModel.options.collectAsStateWithLifecycle()
    val conversionState by viewModel.conversionState.collectAsStateWithLifecycle()
    val activeTool by viewModel.activeTool.collectAsStateWithLifecycle()
    val isInspecting by viewModel.isInspecting.collectAsStateWithLifecycle()

    var showSampleSheet by remember { mutableStateOf(false) }
    var pendingSaveOutput by remember { mutableStateOf<ConversionOutput?>(null) }

    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickMultipleVisualMedia(20)
    ) { uris: List<Uri> ->
        if (uris.isNotEmpty()) viewModel.onUrisPicked(uris)
    }

    val documentPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenMultipleDocuments()
    ) { uris: List<Uri> ->
        if (uris.isNotEmpty()) viewModel.onUrisPicked(uris)
    }

    val saveCopyLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("*/*")
    ) { destUri: Uri? ->
        val out = pendingSaveOutput
        pendingSaveOutput = null
        if (destUri != null && out != null) {
            viewModel.saveCopyToUri(out.outputFile, destUri)
        }
    }

    fun launchDocumentPicker() {
        documentPickerLauncher.launch(
            arrayOf(
                "application/pdf",
                "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                "text/plain",
                "text/csv",
                "text/tab-separated-values",
                "text/markdown",
                "text/html",
                "application/json",
                "image/*"
            )
        )
    }

    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.TopCenter
    ) {
        LazyColumn(
            modifier = Modifier
                .widthIn(max = 760.dp)
                .fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 18.dp, vertical = 18.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Sleek Editorial Studio Header (no heavy boxy banner)
            item {
                StudioHeader(
                    activeTool = activeTool,
                    onClearTool = { viewModel.clearActiveTool() }
                )
            }

            // Bento Drop Zone / Selected File Strip
            item {
                if (isInspecting) {
                    Surface(
                        color = MaterialTheme.colorScheme.surface,
                        shape = RoundedCornerShape(24.dp),
                        tonalElevation = 2.dp,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(28.dp),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            CircularProgressIndicator(modifier = Modifier.size(22.dp), strokeWidth = 2.5.dp)
                            Spacer(Modifier.width(12.dp))
                            Text("Analyzing file structure...", style = MaterialTheme.typography.titleSmall)
                        }
                    }
                } else if (pickedFiles.isEmpty()) {
                    DashedDropCanvas(
                        activeTool = activeTool,
                        onBrowseFiles = { launchDocumentPicker() },
                        onPickPhotos = {
                            photoPickerLauncher.launch(
                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                            )
                        },
                        onTrySample = { showSampleSheet = true }
                    )
                } else {
                    Surface(
                        color = MaterialTheme.colorScheme.surface,
                        shape = RoundedCornerShape(22.dp),
                        tonalElevation = 2.dp,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = if (pickedFiles.size == 1) "Source File" else "${pickedFiles.size} Source Files",
                                    style = MaterialTheme.typography.titleMedium
                                )
                                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                    TextButton(
                                        onClick = { launchDocumentPicker() },
                                        modifier = Modifier.minimumInteractiveComponentSize()
                                    ) {
                                        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(Modifier.width(4.dp))
                                        Text("Replace")
                                    }
                                    TextButton(
                                        onClick = { viewModel.clearSelection() },
                                        modifier = Modifier.minimumInteractiveComponentSize().testTag("clear_selection_button")
                                    ) {
                                        Icon(Icons.Default.Close, contentDescription = "Clear", modifier = Modifier.size(16.dp))
                                        Spacer(Modifier.width(4.dp))
                                        Text("Clear")
                                    }
                                }
                            }

                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                pickedFiles.forEach { file ->
                                    PickedFileTile(file = file)
                                }
                            }
                        }
                    }
                }
            }

            // Target Format Bento Bridge
            if (pickedFiles.isNotEmpty() && availableTargets.isNotEmpty()) {
                item {
                    Surface(
                        color = MaterialTheme.colorScheme.surface,
                        shape = RoundedCornerShape(22.dp),
                        tonalElevation = 2.dp,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(18.dp),
                            verticalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            // Signature Flow Bridge
                            Surface(
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                shape = RoundedCornerShape(16.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        Text("Convert", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        FormatBadge(pickedFiles.first().format.ext)
                                    }
                                    Icon(
                                        Icons.AutoMirrored.Filled.ArrowForward,
                                        contentDescription = "to",
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        Text("Into", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        targetFormat?.let { FormatBadge(it.ext) }
                                    }
                                }
                            }

                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                availableTargets.forEach { fmt ->
                                    val selected = targetFormat == fmt
                                    val isSameFormat = pickedFiles.size == 1 && pickedFiles.first().format == fmt
                                    TargetFormatBentoTile(
                                        format = fmt,
                                        isSelected = selected,
                                        tagNote = if (isSameFormat) "Optimize" else null,
                                        onClick = { viewModel.selectTargetFormat(fmt) }
                                    )
                                }
                            }

                            OutlinedTextField(
                                value = options.customOutputName,
                                onValueChange = { txt -> viewModel.updateOptions { it.copy(customOutputName = txt) } },
                                label = { Text("Rename output (optional)") },
                                placeholder = { Text("Keep original filename") },
                                singleLine = true,
                                shape = RoundedCornerShape(14.dp),
                                colors = OutlinedTextFieldDefaults.colors(
                                    unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant
                                ),
                                modifier = Modifier.fillMaxWidth().testTag("custom_output_name_input")
                            )
                        }
                    }
                }

                item {
                    StudioOptionsPanel(
                        primaryFile = pickedFiles.firstOrNull(),
                        targetFormat = targetFormat,
                        options = options,
                        onUpdateOptions = viewModel::updateOptions
                    )
                }

                // Gradient Primary CTA / Running Progress
                item {
                    when (val state = conversionState) {
                        is ConversionUiState.Running -> {
                            Surface(
                                color = MaterialTheme.colorScheme.primaryContainer,
                                shape = RoundedCornerShape(20.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(
                                    modifier = Modifier.padding(18.dp),
                                    verticalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text(
                                            text = state.progress.stepLabel,
                                            style = MaterialTheme.typography.titleSmall,
                                            color = MaterialTheme.colorScheme.onPrimaryContainer
                                        )
                                        Text(
                                            text = "${(state.progress.fraction * 100).toInt()}%",
                                            style = MaterialTheme.typography.labelMedium,
                                            color = MaterialTheme.colorScheme.onPrimaryContainer
                                        )
                                    }
                                    LinearProgressIndicator(
                                        progress = { state.progress.fraction },
                                        modifier = Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp))
                                    )
                                }
                            }
                        }
                        else -> {
                            val verb = if (pickedFiles.size == 1 && pickedFiles.first().format == targetFormat) {
                                "Optimize ${targetFormat?.displayName ?: ""}"
                            } else {
                                "Convert to ${targetFormat?.displayName ?: ""}"
                            }
                            GradientActionPill(
                                label = verb,
                                enabled = targetFormat != null,
                                onClick = { viewModel.runConversion() },
                                modifier = Modifier.testTag("run_conversion_button")
                            )
                        }
                    }
                }
            }

            if (conversionState is ConversionUiState.Error) {
                item {
                    val err = (conversionState as ConversionUiState.Error).message
                    Surface(
                        color = MaterialTheme.colorScheme.errorContainer,
                        shape = RoundedCornerShape(18.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Icon(Icons.Default.ErrorOutline, contentDescription = "Error", tint = MaterialTheme.colorScheme.onErrorContainer)
                            Text(
                                text = err,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onErrorContainer,
                                modifier = Modifier.weight(1f)
                            )
                            IconButton(onClick = { viewModel.resetConversionState() }) {
                                Icon(Icons.Default.Close, contentDescription = "Dismiss")
                            }
                        }
                    }
                }
            }

            if (conversionState is ConversionUiState.Success) {
                item {
                    val output = (conversionState as ConversionUiState.Success).output
                    ConversionResultCard(
                        output = output,
                        onPreview = {
                            onOpenPreview(output.outputFile.absolutePath, output.outputFile.name, output.outputFormat.ext)
                        },
                        onShare = { FileHelper.shareFile(context, output.outputFile, output.outputFormat.mimeType) },
                        onSaveCopy = {
                            pendingSaveOutput = output
                            saveCopyLauncher.launch(output.outputFile.name)
                        },
                        onOpenWith = { FileHelper.openExternally(context, output.outputFile, output.outputFormat.mimeType) }
                    )
                }
            }

            // Quick capability strip when idle so screen feels balanced & informative
            if (pickedFiles.isEmpty()) {
                item {
                    QuickFormatShowcase()
                }
            }

            item { Spacer(Modifier.height(12.dp)) }
        }
    }

    if (showSampleSheet) {
        SamplePickerBottomSheet(
            onSelectSample = { preset ->
                showSampleSheet = false
                viewModel.loadSampleFile(preset)
            },
            onDismiss = { showSampleSheet = false }
        )
    }
}

@Composable
private fun StudioHeader(
    activeTool: StudioTool?,
    onClearTool: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = activeTool?.title ?: "Convert Flow",
                    style = MaterialTheme.typography.displaySmall.copy(fontWeight = FontWeight.Bold)
                )
            }
            Text(
                text = activeTool?.subtitle ?: "Precision on-device document & media studio",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        if (activeTool != null) {
            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant,
                shape = RoundedCornerShape(20.dp),
                modifier = Modifier
                    .clip(RoundedCornerShape(20.dp))
                    .clickable(onClick = onClearTool)
                    .minimumInteractiveComponentSize()
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(Icons.Default.Close, contentDescription = "Reset tool", modifier = Modifier.size(14.dp))
                    Text("Reset", style = MaterialTheme.typography.labelSmall)
                }
            }
        } else {
            Surface(
                color = EmeraldSuccess.copy(alpha = 0.14f),
                shape = RoundedCornerShape(20.dp)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(5.dp)
                ) {
                    Icon(
                        Icons.Default.CloudOff,
                        contentDescription = null,
                        tint = EmeraldSuccess,
                        modifier = Modifier.size(14.dp)
                    )
                    Text(
                        text = "LOCAL",
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                        color = EmeraldSuccess
                    )
                }
            }
        }
    }
}

@Composable
private fun DashedDropCanvas(
    activeTool: StudioTool?,
    onBrowseFiles: () -> Unit,
    onPickPhotos: () -> Unit,
    onTrySample: () -> Unit
) {
    val dashColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.45f)
    val glowPrimary = MaterialTheme.colorScheme.primary.copy(alpha = 0.08f)
    val glowSecondary = MaterialTheme.colorScheme.secondary.copy(alpha = 0.08f)

    Surface(
        shape = RoundedCornerShape(26.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 1.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.linearGradient(listOf(glowPrimary, Color.Transparent, glowSecondary))
                )
                .padding(12.dp)
                .drawBehind {
                    drawRoundRect(
                        color = dashColor,
                        cornerRadius = CornerRadius(20.dp.toPx(), 20.dp.toPx()),
                        style = Stroke(
                            width = 1.5.dp.toPx(),
                            pathEffect = PathEffect.dashPathEffect(floatArrayOf(18f, 14f), 0f)
                        )
                    )
                }
                .padding(horizontal = 18.dp, vertical = 26.dp)
        ) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Surface(
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                    shape = CircleShape,
                    modifier = Modifier.size(60.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Default.FileUpload,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(28.dp)
                        )
                    }
                }

                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = if (activeTool != null) "Select files for ${activeTool.title}"
                        else "Drop a file into Convert Flow",
                        style = MaterialTheme.typography.titleLarge,
                        textAlign = TextAlign.Center
                    )
                    Text(
                        text = "PDF, DOCX, XLSX, CSV, JSON, MD, HTML, PNG, JPG, WEBP",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Surface(
                        color = MaterialTheme.colorScheme.primary,
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(14.dp))
                            .clickable(onClick = onBrowseFiles)
                            .minimumInteractiveComponentSize()
                            .testTag("browse_files_button")
                    ) {
                        Row(
                            modifier = Modifier.padding(vertical = 13.dp, horizontal = 12.dp),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.FolderOpen, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(7.dp))
                            Text("Browse Files", style = MaterialTheme.typography.labelLarge, color = Color.White, maxLines = 1)
                        }
                    }

                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(14.dp))
                            .clickable(onClick = onPickPhotos)
                            .minimumInteractiveComponentSize()
                            .testTag("pick_photos_button")
                    ) {
                        Row(
                            modifier = Modifier.padding(vertical = 13.dp, horizontal = 12.dp),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.PhotoLibrary, contentDescription = null, tint = MaterialTheme.colorScheme.onSurface, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(7.dp))
                            Text("Pick Photos", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurface, maxLines = 1)
                        }
                    }
                }

                Surface(
                    color = Color.Transparent,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .clickable(onClick = onTrySample)
                        .minimumInteractiveComponentSize()
                        .testTag("try_sample_button")
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            Icons.Default.AutoAwesome,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.secondary,
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = "Or generate a local sample file",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.secondary
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun QuickFormatShowcase() {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(
            text = "Supported Pipelines",
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Row(
            modifier = Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            listOf(
                Triple("PDF ↔ DOCX", "Extract & typeset", "pdf"),
                Triple("Photos → PDF", "Multi-page A4", "png"),
                Triple("XLSX ↔ CSV", "JSON & PDF tables", "xlsx"),
                Triple("Compress & Resize", "Target KB & DPI", "jpg"),
                Triple("ZIP Studio", "Pack & inspect", "zip")
            ).forEach { (title, sub, ext) ->
                Surface(
                    color = MaterialTheme.colorScheme.surface,
                    shape = RoundedCornerShape(16.dp),
                    tonalElevation = 1.dp,
                    modifier = Modifier.width(156.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        FormatBadge(ext)
                        Column {
                            Text(title, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(sub, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PickedFileTile(file: PickedFile) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (file.format.category == FormatCategory.IMAGE) {
                AsyncImage(
                    model = file.uri,
                    contentDescription = file.displayName,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.size(46.dp).clip(RoundedCornerShape(10.dp))
                )
            } else {
                FormatBadge(file.format.ext)
            }

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = file.displayName,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                val details = buildList {
                    add(FileHelper.formatSize(file.sizeBytes))
                    file.pageCount?.let { add("$it page${if (it == 1) "" else "s"}") }
                    if (file.widthPx != null && file.heightPx != null) add("${file.widthPx}×${file.heightPx}px")
                    file.sheetCount?.let { add("$it sheet${if (it == 1) "" else "s"}") }
                }.joinToString(" • ")
                Text(
                    text = details,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun TargetFormatBentoTile(
    format: FileFormat,
    isSelected: Boolean,
    tagNote: String?,
    onClick: () -> Unit
) {
    val accent = formatBadgeColor(format.ext)
    val bg = if (isSelected) accent else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f)
    val fg = if (isSelected) Color.White else MaterialTheme.colorScheme.onSurface

    Surface(
        shape = RoundedCornerShape(14.dp),
        color = bg,
        modifier = Modifier
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .minimumInteractiveComponentSize()
            .testTag("target_format_${format.ext}")
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(7.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(7.dp)
                    .clip(CircleShape)
                    .background(if (isSelected) Color.White else accent)
            )
            Column {
                Text(
                    text = format.displayName,
                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                    color = fg
                )
                if (tagNote != null) {
                    Text(
                        text = tagNote,
                        style = MaterialTheme.typography.labelSmall,
                        color = if (isSelected) Color.White.copy(alpha = 0.85f) else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun GradientActionPill(
    label: String,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val gradient = if (enabled) {
        Brush.horizontalGradient(listOf(CobaltPrimary, Color(0xFF4C6FFF), MintSecondary))
    } else {
        Brush.horizontalGradient(listOf(Color.Gray.copy(alpha = 0.4f), Color.Gray.copy(alpha = 0.4f)))
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(56.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(gradient)
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(Icons.Default.PlayArrow, contentDescription = null, tint = Color.White)
            Text(
                text = label,
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                color = Color.White
            )
        }
    }
}
