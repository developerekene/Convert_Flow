package com.devekene.konvertibles.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SwapHoriz
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
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
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
import com.devekene.konvertibles.data.converter.FileHelper
import com.devekene.konvertibles.domain.model.ConversionCatalog
import com.devekene.konvertibles.domain.model.ConversionGroup
import com.devekene.konvertibles.domain.model.ConversionOutput
import com.devekene.konvertibles.domain.model.ConversionPipeline
import com.devekene.konvertibles.domain.model.ConversionUiState
import com.devekene.konvertibles.domain.model.FileFormat
import com.devekene.konvertibles.domain.model.FormatCategory
import com.devekene.konvertibles.domain.model.PickedFile
import com.devekene.konvertibles.domain.model.PipelineMode
import com.devekene.konvertibles.ui.components.ConversionResultCard
import com.devekene.konvertibles.ui.components.FormatBadge
import com.devekene.konvertibles.ui.components.StudioOptionsPanel
import com.devekene.konvertibles.ui.components.StudioPillChoice
import com.devekene.konvertibles.ui.theme.CrimsonContainer
import com.devekene.konvertibles.ui.theme.CrimsonDeep
import com.devekene.konvertibles.ui.theme.CrimsonPrimary
import com.devekene.konvertibles.ui.viewmodel.ConvertFlowViewModel

@Composable
fun ConvertScreen(
    viewModel: ConvertFlowViewModel,
    onOpenPreview: (filePath: String, fileName: String, ext: String) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val selectedPipeline by viewModel.selectedPipeline.collectAsStateWithLifecycle()
    val selectedGroup by viewModel.selectedGroup.collectAsStateWithLifecycle()
    val pipelineSearch by viewModel.pipelineSearch.collectAsStateWithLifecycle()
    val filteredPipelines by viewModel.filteredPipelines.collectAsStateWithLifecycle()

    val pickedFiles by viewModel.pickedFiles.collectAsStateWithLifecycle()
    val targetFormat by viewModel.targetFormat.collectAsStateWithLifecycle()
    val options by viewModel.options.collectAsStateWithLifecycle()
    val conversionState by viewModel.conversionState.collectAsStateWithLifecycle()
    val isInspecting by viewModel.isInspecting.collectAsStateWithLifecycle()

    var pendingAppend by remember { mutableStateOf(false) }
    var pendingSaveOutput by remember { mutableStateOf<ConversionOutput?>(null) }

    val singlePhotoLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        if (uri != null) viewModel.onUrisPicked(listOf(uri), append = false)
    }

    val multiPhotoLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickMultipleVisualMedia(20)
    ) { uris: List<Uri> ->
        val shouldAppend = pendingAppend
        pendingAppend = false
        if (uris.isNotEmpty()) viewModel.onUrisPicked(uris, append = shouldAppend)
    }

    val singleDocLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) viewModel.onUrisPicked(listOf(uri), append = false)
    }

    val multiDocLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenMultipleDocuments()
    ) { uris: List<Uri> ->
        val shouldAppend = pendingAppend
        pendingAppend = false
        if (uris.isNotEmpty()) viewModel.onUrisPicked(uris, append = shouldAppend)
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

    fun launchStrictDocumentPicker(append: Boolean = false) {
        pendingAppend = append && selectedPipeline.allowMultiple
        val mimeFilter = FileFormat.mimeFilterFor(selectedPipeline.sourceFormats)
        if (selectedPipeline.allowMultiple) {
            multiDocLauncher.launch(mimeFilter)
        } else {
            singleDocLauncher.launch(mimeFilter)
        }
    }

    fun launchStrictPhotoPicker(append: Boolean = false) {
        pendingAppend = append && selectedPipeline.allowMultiple
        val req = PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
        if (selectedPipeline.allowMultiple) {
            multiPhotoLauncher.launch(req)
        } else {
            singlePhotoLauncher.launch(req)
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        contentAlignment = Alignment.TopCenter
    ) {
        LazyColumn(
            modifier = Modifier
                .widthIn(max = 760.dp)
                .fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 18.dp, vertical = 18.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Branded Konvertibles Hero Header
            item {
                KonvertiblesHeader()
            }

            // Conversion Selector (64 pipelines + category pills + search)
            item {
                PipelineSelectorCard(
                    selectedGroup = selectedGroup,
                    searchQuery = pipelineSearch,
                    pipelines = filteredPipelines,
                    selectedPipeline = selectedPipeline,
                    onSelectGroup = viewModel::setPipelineGroup,
                    onSearchChange = viewModel::setPipelineSearch,
                    onSelectPipeline = { viewModel.selectPipeline(it, keepCompatibleFiles = true) }
                )
            }

            // Strict Source Upload Dropzone OR Selected Files Strip
            item {
                AnimatedContent(
                    targetState = Triple(isInspecting, pickedFiles.isEmpty(), selectedPipeline.id),
                    transitionSpec = {
                        (fadeIn(tween(220)) + expandVertically(spring(dampingRatio = 0.88f))) togetherWith
                            (fadeOut(tween(150)) + shrinkVertically(tween(150)))
                    },
                    label = "dropzone_transition"
                ) { (inspecting, emptySelection, _) ->
                    when {
                        inspecting -> {
                            Surface(
                                color = MaterialTheme.colorScheme.surface,
                                shape = RoundedCornerShape(24.dp),
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.padding(28.dp),
                                    horizontalArrangement = Arrangement.Center,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(22.dp),
                                        strokeWidth = 2.5.dp,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                    Spacer(Modifier.width(12.dp))
                                    Text("Inspecting ${selectedPipeline.allowedSourceLabel}...", style = MaterialTheme.typography.titleSmall)
                                }
                            }
                        }

                        emptySelection -> {
                            StrictDropCanvas(
                                pipeline = selectedPipeline,
                                onBrowseStrict = { launchStrictDocumentPicker(append = false) },
                                onPickStrictPhotos = { launchStrictPhotoPicker(append = false) },
                                onLoadMatchingSample = { viewModel.loadSampleForCurrentPipeline() }
                            )
                        }

                        else -> {
                            Surface(
                                color = MaterialTheme.colorScheme.surface,
                                shape = RoundedCornerShape(22.dp),
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .animateContentSize(spring(stiffness = Spring.StiffnessMediumLow))
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
                                            text = if (pickedFiles.size == 1) "Uploaded ${pickedFiles.first().format.displayName}"
                                            else "${pickedFiles.size} Uploaded Files",
                                            style = MaterialTheme.typography.titleMedium
                                        )
                                        Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                                            if (selectedPipeline.allowMultiple) {
                                                TextButton(
                                                    onClick = {
                                                        if (selectedPipeline.usePhotoPicker) launchStrictPhotoPicker(append = true)
                                                        else launchStrictDocumentPicker(append = true)
                                                    },
                                                    modifier = Modifier.minimumInteractiveComponentSize()
                                                ) {
                                                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                                                    Spacer(Modifier.width(4.dp))
                                                    Text("Add")
                                                }
                                            } else {
                                                TextButton(
                                                    onClick = {
                                                        if (selectedPipeline.usePhotoPicker) launchStrictPhotoPicker(append = false)
                                                        else launchStrictDocumentPicker(append = false)
                                                    },
                                                    modifier = Modifier.minimumInteractiveComponentSize()
                                                ) {
                                                    Icon(Icons.Default.SwapHoriz, contentDescription = null, modifier = Modifier.size(16.dp))
                                                    Spacer(Modifier.width(4.dp))
                                                    Text("Replace")
                                                }
                                            }
                                            TextButton(
                                                onClick = { viewModel.clearSelection() },
                                                modifier = Modifier
                                                    .minimumInteractiveComponentSize()
                                                    .testTag("clear_selection_button")
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
                }
            }

            // Output Mapping, Rename & Fine-Tune Controls
            item {
                AnimatedVisibility(
                    visible = pickedFiles.isNotEmpty(),
                    enter = fadeIn(tween(240)) + expandVertically(spring(dampingRatio = 0.85f)),
                    exit = fadeOut(tween(150)) + shrinkVertically(tween(150))
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                        Surface(
                            color = MaterialTheme.colorScheme.surface,
                            shape = RoundedCornerShape(22.dp),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier.padding(18.dp),
                                verticalArrangement = Arrangement.spacedBy(14.dp)
                            ) {
                                Surface(
                                    color = CrimsonContainer.copy(alpha = 0.55f),
                                    shape = RoundedCornerShape(16.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            pickedFiles.firstOrNull()?.let { FormatBadge(it.format.ext) }
                                            Text(
                                                text = selectedPipeline.title,
                                                style = MaterialTheme.typography.titleSmall,
                                                color = MaterialTheme.colorScheme.onBackground
                                            )
                                        }
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                                        ) {
                                            Icon(
                                                Icons.AutoMirrored.Filled.ArrowForward,
                                                contentDescription = "to",
                                                tint = MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.size(16.dp)
                                            )
                                            if (selectedPipeline.mode == PipelineMode.EXTRACT_ZIP) {
                                                Surface(
                                                    color = MaterialTheme.colorScheme.primary,
                                                    shape = RoundedCornerShape(10.dp)
                                                ) {
                                                    Text(
                                                        text = "FILES",
                                                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                                        color = Color.White,
                                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                                    )
                                                }
                                            } else {
                                                (targetFormat ?: selectedPipeline.targetFormat).let {
                                                    FormatBadge(it.ext)
                                                }
                                            }
                                        }
                                    }
                                }

                                OutlinedTextField(
                                    value = options.customOutputName,
                                    onValueChange = { txt -> viewModel.updateOptions { it.copy(customOutputName = txt) } },
                                    label = { Text("Output filename (optional)") },
                                    placeholder = { Text("Keep original filename") },
                                    singleLine = true,
                                    shape = RoundedCornerShape(14.dp),
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                                        unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant
                                    ),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .testTag("custom_output_name_input")
                                )
                            }
                        }

                        StudioOptionsPanel(
                            primaryFile = pickedFiles.firstOrNull(),
                            targetFormat = targetFormat ?: selectedPipeline.targetFormat,
                            options = options,
                            onUpdateOptions = viewModel::updateOptions
                        )

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
                                            color = MaterialTheme.colorScheme.primary,
                                            trackColor = Color.White,
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .height(8.dp)
                                                .clip(RoundedCornerShape(4.dp))
                                        )
                                    }
                                }
                            }
                            else -> {
                                val ctaLabel = when (selectedPipeline.mode) {
                                    PipelineMode.COMPRESS_IMAGE, PipelineMode.COMPRESS_PDF -> "Compress ${selectedPipeline.targetFormat.displayName}"
                                    PipelineMode.RESIZE_IMAGE, PipelineMode.RESIZE_PDF -> "Resize ${selectedPipeline.targetFormat.displayName}"
                                    PipelineMode.MERGE_PDF -> "Merge ${pickedFiles.size} PDFs"
                                    PipelineMode.SPLIT_PDF -> "Extract PDF Pages"
                                    PipelineMode.EXTRACT_ZIP -> "Extract Archive"
                                    else -> "Convert to ${(targetFormat ?: selectedPipeline.targetFormat).displayName}"
                                }
                                CrimsonActionPill(
                                    label = ctaLabel,
                                    enabled = pickedFiles.isNotEmpty(),
                                    onClick = { viewModel.runConversion() },
                                    modifier = Modifier.testTag("run_conversion_button")
                                )
                            }
                        }
                    }
                }
            }

            // Conversion Error
            item {
                AnimatedVisibility(
                    visible = conversionState is ConversionUiState.Error,
                    enter = fadeIn() + expandVertically(),
                    exit = fadeOut() + shrinkVertically()
                ) {
                    val err = (conversionState as? ConversionUiState.Error)?.message.orEmpty()
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
                            Icon(
                                Icons.Default.ErrorOutline,
                                contentDescription = "Error",
                                tint = MaterialTheme.colorScheme.onErrorContainer
                            )
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

            // Conversion Result Card
            item {
                AnimatedVisibility(
                    visible = conversionState is ConversionUiState.Success,
                    enter = fadeIn(tween(260)) + expandVertically(spring(dampingRatio = 0.82f)),
                    exit = fadeOut() + shrinkVertically()
                ) {
                    val output = (conversionState as? ConversionUiState.Success)?.output
                    if (output != null) {
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
            }

            item { Spacer(Modifier.height(14.dp)) }
        }
    }
}

@Composable
private fun KonvertiblesHeader() {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                text = "Konvertibles",
                style = MaterialTheme.typography.displaySmall.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onBackground
            )
            Text(
                text = "Pocket ready to go",
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                color = MaterialTheme.colorScheme.primary
            )
        }

        // Converters badge removed
    }
}

@Composable
private fun PipelineSelectorCard(
    selectedGroup: ConversionGroup,
    searchQuery: String,
    pipelines: List<ConversionPipeline>,
    selectedPipeline: ConversionPipeline,
    onSelectGroup: (ConversionGroup) -> Unit,
    onSearchChange: (String) -> Unit,
    onSelectPipeline: (ConversionPipeline) -> Unit
) {
    Surface(
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(22.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Choose Conversion",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Text(
                    text = "${pipelines.size} available",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            TextField(
                value = searchQuery,
                onValueChange = onSearchChange,
                placeholder = { Text("Search e.g. PDF to Word, XML, WEBP...") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { onSearchChange("") }) {
                            Icon(Icons.Default.Clear, contentDescription = "Clear search")
                        }
                    }
                },
                singleLine = true,
                shape = RoundedCornerShape(16.dp),
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = MaterialTheme.colorScheme.background,
                    unfocusedContainerColor = MaterialTheme.colorScheme.background,
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .testTag("pipeline_search_input")
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                ConversionGroup.entries.forEach { group ->
                    StudioPillChoice(
                        label = group.label,
                        selected = selectedGroup == group,
                        onClick = { onSelectGroup(group) },
                        modifier = Modifier.testTag("group_${group.name.lowercase()}")
                    )
                }
            }

            if (pipelines.isEmpty()) {
                Text(
                    text = "No conversion matches \"$searchQuery\".",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)
                )
            } else {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(pipelines, key = { it.id }) { pipeline ->
                        PipelineTileCard(
                            pipeline = pipeline,
                            isSelected = pipeline.id == selectedPipeline.id,
                            onClick = { onSelectPipeline(pipeline) },
                            modifier = Modifier.animateItem()
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PipelineTileCard(
    pipeline: ConversionPipeline,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val bgColor by animateColorAsState(
        targetValue = if (isSelected) CrimsonContainer.copy(alpha = 0.65f) else MaterialTheme.colorScheme.background,
        animationSpec = tween(200),
        label = "pipeline_bg"
    )
    val borderColor by animateColorAsState(
        targetValue = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
        animationSpec = tween(200),
        label = "pipeline_border"
    )

    Surface(
        color = bgColor,
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(if (isSelected) 1.5.dp else 1.dp, borderColor),
        modifier = modifier
            .width(188.dp)
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .minimumInteractiveComponentSize()
            .testTag("pipeline_${pipeline.id}")
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                    shape = RoundedCornerShape(8.dp)
                ) {
                    val badgeText = if (pipeline.sourceFormats.size > 3) {
                        "MULTI → ${pipeline.targetFormat.ext.uppercase()}"
                    } else {
                        "${pipeline.sourceFormats.first().ext.uppercase()} → ${pipeline.targetFormat.ext.uppercase()}"
                    }
                    Text(
                        text = badgeText,
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                        color = if (isSelected) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }

                if (isSelected) {
                    Surface(
                        color = MaterialTheme.colorScheme.primary,
                        shape = CircleShape,
                        modifier = Modifier.size(20.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                Icons.Default.Check,
                                contentDescription = "Selected",
                                tint = Color.White,
                                modifier = Modifier.size(13.dp)
                            )
                        }
                    }
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = pipeline.title,
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onBackground,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = pipeline.subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun StrictDropCanvas(
    pipeline: ConversionPipeline,
    onBrowseStrict: () -> Unit,
    onPickStrictPhotos: () -> Unit,
    onLoadMatchingSample: () -> Unit
) {
    val dashColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.50f)
    val glowPrimary = MaterialTheme.colorScheme.primary.copy(alpha = 0.05f)
    val sampleExt = pipeline.sourceFormats.first().ext.uppercase()

    Surface(
        shape = RoundedCornerShape(26.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        modifier = Modifier.fillMaxWidth()
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(Brush.linearGradient(listOf(glowPrimary, Color.Transparent)))
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
                .padding(horizontal = 18.dp, vertical = 22.dp)
        ) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Surface(
                    color = MaterialTheme.colorScheme.primaryContainer,
                    shape = CircleShape,
                    modifier = Modifier.size(56.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Default.FileUpload,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(26.dp)
                        )
                    }
                }

                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = if (pipeline.sourceFormats.size > 6) "Upload Files for ${pipeline.title}"
                        else "Upload ${pipeline.allowedSourceLabel}",
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onBackground,
                        textAlign = TextAlign.Center
                    )
                    Text(
                        text = if (pipeline.allowMultiple) "Accepted: ${pipeline.allowedSourceLabel} (multi-file supported)"
                        else "Accepted format: ${pipeline.allowedSourceLabel} only",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                }

                if (pipeline.usePhotoPicker) {
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
                                .clickable(onClick = onPickStrictPhotos)
                                .minimumInteractiveComponentSize()
                                .testTag("pick_photos_button")
                        ) {
                            Row(
                                modifier = Modifier.padding(vertical = 13.dp, horizontal = 12.dp),
                                horizontalArrangement = Arrangement.Center,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Default.PhotoLibrary, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(7.dp))
                                Text("Pick Photos", style = MaterialTheme.typography.labelLarge, color = Color.White, maxLines = 1)
                            }
                        }

                        Surface(
                            color = MaterialTheme.colorScheme.background,
                            shape = RoundedCornerShape(14.dp),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(14.dp))
                                .clickable(onClick = onBrowseStrict)
                                .minimumInteractiveComponentSize()
                                .testTag("browse_files_button")
                        ) {
                            Row(
                                modifier = Modifier.padding(vertical = 13.dp, horizontal = 12.dp),
                                horizontalArrangement = Arrangement.Center,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Default.FolderOpen, contentDescription = null, tint = MaterialTheme.colorScheme.onBackground, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(7.dp))
                                Text("Files", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onBackground, maxLines = 1)
                            }
                        }
                    }
                } else {
                    Surface(
                        color = MaterialTheme.colorScheme.primary,
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .clickable(onClick = onBrowseStrict)
                            .minimumInteractiveComponentSize()
                            .testTag("browse_files_button")
                    ) {
                        Row(
                            modifier = Modifier.padding(vertical = 14.dp, horizontal = 16.dp),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.FolderOpen, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            val btnLabel = if (pipeline.sourceFormats.size > 6) "Select Files"
                            else "Select ${pipeline.allowedSourceLabel} File${if (pipeline.allowMultiple) "s" else ""}"
                            Text(btnLabel, style = MaterialTheme.typography.labelLarge, color = Color.White, maxLines = 1)
                        }
                    }
                }

                Surface(
                    color = Color.Transparent,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .clickable(onClick = onLoadMatchingSample)
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
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = "Or test with a matching .$sampleExt sample",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PickedFileTile(file: PickedFile) {
    Surface(
        color = MaterialTheme.colorScheme.background,
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
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
                    modifier = Modifier
                        .size(46.dp)
                        .clip(RoundedCornerShape(10.dp))
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
private fun CrimsonActionPill(
    label: String,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val gradient = if (enabled) {
        Brush.horizontalGradient(listOf(CrimsonPrimary, CrimsonDeep))
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
