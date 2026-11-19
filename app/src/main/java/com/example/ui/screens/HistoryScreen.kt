package com.example.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.R
import com.example.data.converter.FileHelper
import com.example.data.local.ConversionRecord
import com.example.domain.model.FileFormat
import com.example.domain.model.FormatCategory
import com.example.ui.components.FormatBadge
import com.example.ui.components.SavingsPill
import com.example.ui.components.StudioPillChoice
import com.example.ui.components.formatBadgeColor
import com.example.ui.theme.EmeraldSuccess
import com.example.ui.theme.HeroDeep
import com.example.ui.viewmodel.ConvertFlowViewModel
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun HistoryScreen(
    viewModel: ConvertFlowViewModel,
    onOpenPreview: (filePath: String, fileName: String, ext: String) -> Unit,
    onGoToConvert: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val allRecords by viewModel.allRecords.collectAsStateWithLifecycle()
    val filteredRecords by viewModel.filteredRecords.collectAsStateWithLifecycle()
    val activeFilter by viewModel.historyFilter.collectAsStateWithLifecycle()
    val searchQuery by viewModel.historySearch.collectAsStateWithLifecycle()

    var pendingSaveRecord by remember { mutableStateOf<ConversionRecord?>(null) }
    val saveLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("*/*")
    ) { destUri: Uri? ->
        val rec = pendingSaveRecord
        pendingSaveRecord = null
        if (destUri != null && rec != null) {
            viewModel.saveCopyToUri(File(rec.outputPath), destUri)
        }
    }

    val totalBytesSaved = remember(allRecords) {
        allRecords.sumOf { (it.originalSizeBytes - it.outputSizeBytes).coerceAtLeast(0L) }
    }

    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.TopCenter
    ) {
        LazyColumn(
            modifier = Modifier
                .widthIn(max = 780.dp)
                .fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 18.dp, vertical = 18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        text = "Library",
                        style = MaterialTheme.typography.displaySmall.copy(fontWeight = FontWeight.Bold)
                    )

                    if (allRecords.isNotEmpty()) {
                        // 2-up Bento Metric Tiles
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Surface(
                                color = MaterialTheme.colorScheme.surface,
                                shape = RoundedCornerShape(20.dp),
                                tonalElevation = 1.dp,
                                modifier = Modifier.weight(1f)
                            ) {
                                Column(
                                    modifier = Modifier.padding(16.dp),
                                    verticalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Text(
                                        text = "CONVERTED",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Text(
                                        text = "${allRecords.size} file${if (allRecords.size == 1) "" else "s"}",
                                        style = MaterialTheme.typography.headlineSmall
                                    )
                                }
                            }
                            Surface(
                                color = HeroDeep,
                                shape = RoundedCornerShape(20.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Column(
                                    modifier = Modifier.padding(16.dp),
                                    verticalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Text(
                                        text = "SPACE SAVED",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = Color(0xFF93A3C2)
                                    )
                                    Text(
                                        text = if (totalBytesSaved > 0L) FileHelper.formatSize(totalBytesSaved) else "0 B",
                                        style = MaterialTheme.typography.headlineSmall,
                                        color = Color(0xFF34E8B5)
                                    )
                                }
                            }
                        }

                        // Borderless pill search bar
                        TextField(
                            value = searchQuery,
                            onValueChange = viewModel::setHistorySearch,
                            placeholder = { Text("Search converted files...") },
                            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                            trailingIcon = {
                                if (searchQuery.isNotEmpty()) {
                                    IconButton(onClick = { viewModel.setHistorySearch("") }) {
                                        Icon(Icons.Default.Clear, contentDescription = "Clear search")
                                    }
                                }
                            },
                            singleLine = true,
                            shape = RoundedCornerShape(28.dp),
                            colors = TextFieldDefaults.colors(
                                focusedContainerColor = MaterialTheme.colorScheme.surface,
                                unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                                focusedIndicatorColor = Color.Transparent,
                                unfocusedIndicatorColor = Color.Transparent
                            ),
                            modifier = Modifier.fillMaxWidth().testTag("history_search_input")
                        )

                        Row(
                            modifier = Modifier.horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            StudioPillChoice(
                                label = "All",
                                selected = activeFilter == null,
                                onClick = { viewModel.setHistoryFilter(null) }
                            )
                            FormatCategory.entries.forEach { cat ->
                                StudioPillChoice(
                                    label = cat.label,
                                    selected = activeFilter == cat,
                                    onClick = { viewModel.setHistoryFilter(cat) }
                                )
                            }
                        }
                    }
                }
            }

            if (allRecords.isEmpty()) {
                item {
                    Surface(
                        color = MaterialTheme.colorScheme.surface,
                        shape = RoundedCornerShape(24.dp),
                        tonalElevation = 1.dp,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Image(
                                painter = painterResource(id = R.drawable.img_empty_history),
                                contentDescription = "Empty local conversion history",
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(180.dp)
                                    .clip(RoundedCornerShape(18.dp))
                            )
                            Text(
                                text = "Your Studio Library is Empty",
                                style = MaterialTheme.typography.titleLarge
                            )
                            Text(
                                text = "Convert a PDF, Word file, spreadsheet, or image — every local output appears here ready to preview, share, or export.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center
                            )
                            Button(
                                onClick = onGoToConvert,
                                shape = RoundedCornerShape(14.dp),
                                modifier = Modifier.minimumInteractiveComponentSize().testTag("empty_history_cta")
                            ) {
                                Text("Convert a File")
                            }
                        }
                    }
                }
            } else if (filteredRecords.isEmpty()) {
                item {
                    Surface(
                        color = MaterialTheme.colorScheme.surface,
                        shape = RoundedCornerShape(18.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "No matching files for your current filter.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(24.dp),
                            textAlign = TextAlign.Center
                        )
                    }
                }
            } else {
                items(filteredRecords, key = { it.id }) { record ->
                    HistoryTimelineCard(
                        record = record,
                        onPreview = {
                            onOpenPreview(record.outputPath, record.outputName, record.outputExtension)
                        },
                        onShare = {
                            val mime = FileFormat.fromExtension(record.outputExtension)?.mimeType ?: "*/*"
                            FileHelper.shareFile(context, File(record.outputPath), mime)
                        },
                        onSaveCopy = {
                            pendingSaveRecord = record
                            saveLauncher.launch(record.outputName)
                        },
                        onDelete = { viewModel.deleteRecord(record) }
                    )
                }
            }

            item { Spacer(Modifier.height(12.dp)) }
        }
    }
}

@Composable
private fun HistoryTimelineCard(
    record: ConversionRecord,
    onPreview: () -> Unit,
    onShare: () -> Unit,
    onSaveCopy: () -> Unit,
    onDelete: () -> Unit
) {
    val dateLabel = remember(record.createdAtMillis) {
        SimpleDateFormat("MMM d, HH:mm", Locale.getDefault()).format(Date(record.createdAtMillis))
    }
    val accent = formatBadgeColor(record.outputExtension)

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .clickable(onClick = onPreview)
            .testTag("history_item_${record.id}"),
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 1.dp
    ) {
        Row(modifier = Modifier.height(IntrinsicSize.Min)) {
            Box(
                modifier = Modifier
                    .width(5.dp)
                    .fillMaxHeight()
                    .background(accent)
            )
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    FormatBadge(record.outputExtension)
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = record.outputName,
                            style = MaterialTheme.typography.titleSmall,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = "${record.operationLabel} • $dateLabel",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    SavingsPill(record.savingsPercent)
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "${FileHelper.formatSize(record.originalSizeBytes)} → ${FileHelper.formatSize(record.outputSizeBytes)}",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(
                            onClick = onPreview,
                            modifier = Modifier.minimumInteractiveComponentSize()
                        ) {
                            Icon(Icons.Default.Visibility, contentDescription = "Preview ${record.outputName}", modifier = Modifier.size(19.dp))
                        }
                        IconButton(
                            onClick = onShare,
                            modifier = Modifier.minimumInteractiveComponentSize()
                        ) {
                            Icon(Icons.Default.Share, contentDescription = "Share ${record.outputName}", modifier = Modifier.size(19.dp))
                        }
                        IconButton(
                            onClick = onSaveCopy,
                            modifier = Modifier.minimumInteractiveComponentSize()
                        ) {
                            Icon(Icons.Default.Download, contentDescription = "Save copy of ${record.outputName}", modifier = Modifier.size(19.dp))
                        }
                        IconButton(
                            onClick = onDelete,
                            modifier = Modifier.minimumInteractiveComponentSize()
                        ) {
                            Icon(
                                Icons.Default.DeleteOutline,
                                contentDescription = "Delete ${record.outputName}",
                                tint = MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(19.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}
