package com.example.ui.viewmodel

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.ConvertFlowApp
import com.example.data.converter.ConversionEngine
import com.example.data.converter.FileHelper
import com.example.data.converter.FilePreviewContent
import com.example.data.converter.ImageEngine
import com.example.data.converter.OoxmlSpreadsheetHandler
import com.example.data.converter.OoxmlWordHandler
import com.example.data.converter.PdfEngine
import com.example.data.converter.SampleFileGenerator
import com.example.data.converter.TextFormatConverter
import com.example.data.converter.ZipEngine
import com.example.data.local.ConversionRecord
import com.example.data.local.HistoryRepository
import com.example.data.local.PreferencesRepository
import com.example.data.local.UserPreferences
import com.example.domain.model.ConversionCatalog
import com.example.domain.model.ConversionGroup
import com.example.domain.model.ConversionOptions
import com.example.domain.model.ConversionPipeline
import com.example.domain.model.ConversionProgress
import com.example.domain.model.ConversionUiState
import com.example.domain.model.FileFormat
import com.example.domain.model.FormatCategory
import com.example.domain.model.PdfDpiPreset
import com.example.domain.model.PdfPageSize
import com.example.domain.model.PickedFile
import com.example.domain.model.PipelineMode
import com.example.domain.model.ResizeMode
import com.example.domain.model.StudioTool
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class ConvertFlowViewModel(
    application: Application,
    private val historyRepository: HistoryRepository,
    private val preferencesRepository: PreferencesRepository,
    private val conversionEngine: ConversionEngine
) : AndroidViewModel(application) {

    val preferences: StateFlow<UserPreferences> = preferencesRepository.preferences
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), UserPreferences())

    private val _selectedGroup = MutableStateFlow(ConversionGroup.ALL)
    val selectedGroup: StateFlow<ConversionGroup> = _selectedGroup.asStateFlow()

    private val _pipelineSearch = MutableStateFlow("")
    val pipelineSearch: StateFlow<String> = _pipelineSearch.asStateFlow()

    val filteredPipelines: StateFlow<List<ConversionPipeline>> = combine(
        _selectedGroup,
        _pipelineSearch
    ) { group, query ->
        val q = query.trim()
        ConversionCatalog.allPipelines.filter { pipeline ->
            val matchesGroup = group == ConversionGroup.ALL || pipeline.group == group
            val matchesQuery = q.isEmpty() ||
                pipeline.title.contains(q, ignoreCase = true) ||
                pipeline.subtitle.contains(q, ignoreCase = true) ||
                pipeline.sourceFormats.any { it.ext.contains(q, ignoreCase = true) || it.displayName.contains(q, ignoreCase = true) } ||
                pipeline.targetFormat.ext.contains(q, ignoreCase = true) ||
                pipeline.targetFormat.displayName.contains(q, ignoreCase = true)
            matchesGroup && matchesQuery
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), ConversionCatalog.allPipelines)

    private val _selectedPipeline = MutableStateFlow(ConversionCatalog.allPipelines.first())
    val selectedPipeline: StateFlow<ConversionPipeline> = _selectedPipeline.asStateFlow()

    private val _historyFilter = MutableStateFlow<FormatCategory?>(null)
    val historyFilter: StateFlow<FormatCategory?> = _historyFilter.asStateFlow()

    private val _historySearch = MutableStateFlow("")
    val historySearch: StateFlow<String> = _historySearch.asStateFlow()

    val allRecords: StateFlow<List<ConversionRecord>> = historyRepository.records
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val filteredRecords: StateFlow<List<ConversionRecord>> = combine(
        allRecords,
        _historyFilter,
        _historySearch
    ) { records, filter, query ->
        records.filter { rec ->
            val matchesCat = filter == null || rec.category == filter.name
            val q = query.trim()
            val matchesQuery = q.isEmpty() ||
                rec.outputName.contains(q, ignoreCase = true) ||
                rec.sourceName.contains(q, ignoreCase = true) ||
                rec.operationLabel.contains(q, ignoreCase = true)
            matchesCat && matchesQuery
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _pickedFiles = MutableStateFlow<List<PickedFile>>(emptyList())
    val pickedFiles: StateFlow<List<PickedFile>> = _pickedFiles.asStateFlow()

    private val _targetFormat = MutableStateFlow<FileFormat?>(ConversionCatalog.allPipelines.first().targetFormat)
    val targetFormat: StateFlow<FileFormat?> = _targetFormat.asStateFlow()

    private val _options = MutableStateFlow(ConversionOptions())
    val options: StateFlow<ConversionOptions> = _options.asStateFlow()

    private val _conversionState = MutableStateFlow<ConversionUiState>(ConversionUiState.Idle)
    val conversionState: StateFlow<ConversionUiState> = _conversionState.asStateFlow()

    private val _activeTool = MutableStateFlow<StudioTool?>(null)
    val activeTool: StateFlow<StudioTool?> = _activeTool.asStateFlow()

    private val _isInspecting = MutableStateFlow(false)
    val isInspecting: StateFlow<Boolean> = _isInspecting.asStateFlow()

    private val _storageUsedBytes = MutableStateFlow(0L)
    val storageUsedBytes: StateFlow<Long> = _storageUsedBytes.asStateFlow()

    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 8)
    val messages = _messages.asSharedFlow()

    init {
        viewModelScope.launch {
            val prefs = preferencesRepository.preferences.first()
            _options.value = _options.value.copy(
                quality = prefs.defaultQuality,
                pdfDpi = prefs.defaultPdfDpi,
                pdfPageSize = prefs.defaultPdfPageSize,
                stripExif = prefs.stripExifByDefault
            )
            refreshStorageStats()
        }
    }

    fun refreshStorageStats() {
        viewModelScope.launch(Dispatchers.IO) {
            _storageUsedBytes.value = FileHelper.calculateConvertedStorageBytes(getApplication())
        }
    }

    fun setPipelineGroup(group: ConversionGroup) {
        _selectedGroup.value = group
    }

    fun setPipelineSearch(query: String) {
        _pipelineSearch.value = query
    }

    fun selectPipeline(pipeline: ConversionPipeline, keepCompatibleFiles: Boolean = true) {
        _selectedPipeline.value = pipeline
        _activeTool.value = null
        _conversionState.value = ConversionUiState.Idle
        applyOptionsForMode(pipeline.mode)

        val current = _pickedFiles.value
        val compatible = if (keepCompatibleFiles) {
            current.filter { it.format in pipeline.sourceFormats }
        } else {
            emptyList()
        }

        if (compatible.isEmpty()) {
            _pickedFiles.value = emptyList()
            _targetFormat.value = pipeline.targetFormat
        } else {
            val adjusted = if (pipeline.allowMultiple) compatible else compatible.take(1)
            _pickedFiles.value = adjusted
            _targetFormat.value = resolvedTargetForPipeline(pipeline, adjusted.firstOrNull())
        }
    }

    private fun applyOptionsForMode(mode: PipelineMode) {
        when (mode) {
            PipelineMode.COMPRESS_IMAGE -> updateOptions {
                it.copy(quality = 72, targetSizeKb = null, resizeMode = ResizeMode.NONE, pageRange = "")
            }
            PipelineMode.RESIZE_IMAGE -> updateOptions {
                it.copy(resizeMode = ResizeMode.PERCENTAGE, scalePercent = 50, targetSizeKb = null, pageRange = "")
            }
            PipelineMode.COMPRESS_PDF -> updateOptions {
                it.copy(pdfDpi = PdfDpiPreset.SCREEN_72, quality = 70, pdfPageSize = PdfPageSize.ORIGINAL, pageRange = "")
            }
            PipelineMode.RESIZE_PDF -> updateOptions {
                it.copy(pdfPageSize = PdfPageSize.A4, pdfDpi = PdfDpiPreset.EBOOK_150, quality = 85, pageRange = "")
            }
            PipelineMode.SPLIT_PDF -> updateOptions {
                it.copy(pageRange = "1", pdfPageSize = PdfPageSize.ORIGINAL, quality = 92)
            }
            PipelineMode.MERGE_PDF -> updateOptions {
                it.copy(pageRange = "")
            }
            PipelineMode.CONVERT, PipelineMode.EXTRACT_ZIP -> updateOptions {
                it.copy(pageRange = "", resizeMode = ResizeMode.NONE, targetSizeKb = null)
            }
        }
    }

    private fun resolvedTargetForPipeline(pipeline: ConversionPipeline, primary: PickedFile?): FileFormat {
        return when (pipeline.mode) {
            PipelineMode.COMPRESS_IMAGE, PipelineMode.RESIZE_IMAGE -> {
                primary?.format?.takeIf { it in pipeline.sourceFormats } ?: pipeline.targetFormat
            }
            else -> pipeline.targetFormat
        }
    }

    fun onUrisPicked(uris: List<Uri>, append: Boolean = false) {
        if (uris.isEmpty()) return
        val pipeline = _selectedPipeline.value

        viewModelScope.launch {
            _isInspecting.value = true
            _conversionState.value = ConversionUiState.Idle
            val inspected = withContext(Dispatchers.IO) {
                uris.mapNotNull { uri -> FileHelper.inspectUri(getApplication(), uri) }
            }
            _isInspecting.value = false

            val valid = inspected.filter { it.format in pipeline.sourceFormats }
            val rejectedCount = uris.size - valid.size

            if (valid.isEmpty()) {
                _messages.tryEmit(
                    "Invalid file type. ${pipeline.title} only accepts ${pipeline.allowedSourceLabel}."
                )
                return@launch
            }

            if (rejectedCount > 0) {
                _messages.tryEmit(
                    "Skipped $rejectedCount incompatible file(s). Only ${pipeline.allowedSourceLabel} accepted."
                )
            }

            val finalSelection = when {
                pipeline.allowMultiple && append -> (_pickedFiles.value + valid).distinctBy { it.uri }
                pipeline.allowMultiple -> valid
                else -> valid.take(1)
            }
            _pickedFiles.value = finalSelection
            _targetFormat.value = resolvedTargetForPipeline(pipeline, finalSelection.firstOrNull())
        }
    }

    fun loadSampleFile(preset: SampleFileGenerator.SamplePreset) {
        viewModelScope.launch {
            _isInspecting.value = true
            val uri = withContext(Dispatchers.IO) {
                SampleFileGenerator.generateSample(getApplication(), preset)
            }
            _isInspecting.value = false
            onUrisPicked(listOf(uri))
        }
    }

    fun loadSampleForCurrentPipeline() {
        val pipeline = _selectedPipeline.value
        val sampleFormat = pipeline.sourceFormats.first()
        viewModelScope.launch {
            _isInspecting.value = true
            val uris = withContext(Dispatchers.IO) {
                if (pipeline.mode == PipelineMode.MERGE_PDF) {
                    listOf(
                        SampleFileGenerator.generateSampleForFormat(getApplication(), FileFormat.PDF, "Report_Part_1.pdf"),
                        SampleFileGenerator.generateSampleForFormat(getApplication(), FileFormat.PDF, "Report_Part_2.pdf")
                    )
                } else {
                    listOf(SampleFileGenerator.generateSampleForFormat(getApplication(), sampleFormat))
                }
            }
            _isInspecting.value = false
            onUrisPicked(uris)
        }
    }

    fun updateOptions(transform: (ConversionOptions) -> ConversionOptions) {
        _options.value = transform(_options.value)
    }

    fun launchStudioTool(tool: StudioTool) {
        val pipeline = ConversionCatalog.byId(tool.pipelineId) ?: ConversionCatalog.allPipelines.first()
        selectPipeline(pipeline, keepCompatibleFiles = true)
        _activeTool.value = tool
    }

    fun clearActiveTool() {
        _activeTool.value = null
    }

    fun clearSelection() {
        _pickedFiles.value = emptyList()
        _conversionState.value = ConversionUiState.Idle
    }

    fun resetConversionState() {
        _conversionState.value = ConversionUiState.Idle
    }

    fun runConversion() {
        val pipeline = _selectedPipeline.value
        val files = _pickedFiles.value
        val target = _targetFormat.value ?: pipeline.targetFormat
        if (files.isEmpty()) {
            _messages.tryEmit("Upload a ${pipeline.allowedSourceLabel} file first.")
            return
        }
        if (pipeline.mode == PipelineMode.MERGE_PDF && files.size < 2) {
            _messages.tryEmit("Add at least 2 PDF files to merge.")
            return
        }

        viewModelScope.launch {
            _conversionState.value = ConversionUiState.Running(
                ConversionProgress("Starting engine...", 0.05f)
            )
            runCatching {
                conversionEngine.executeConversion(
                    pickedFiles = files,
                    targetFormat = target,
                    options = _options.value,
                    operationLabel = pipeline.title,
                    extractArchive = pipeline.mode == PipelineMode.EXTRACT_ZIP
                ) { progress ->
                    _conversionState.value = ConversionUiState.Running(progress)
                }
            }.onSuccess { output ->
                _conversionState.value = ConversionUiState.Success(output)
                refreshStorageStats()
            }.onFailure { err ->
                _conversionState.value = ConversionUiState.Error(
                    err.message ?: "Conversion failed on this file."
                )
            }
        }
    }

    suspend fun loadPreview(filePath: String, extension: String): FilePreviewContent = withContext(Dispatchers.IO) {
        val file = File(filePath)
        if (!file.exists()) return@withContext FilePreviewContent.Unavailable("File no longer exists on device.")
        val format = FileFormat.fromExtension(extension)
            ?: return@withContext FilePreviewContent.Unavailable("Unknown file extension: .$extension")

        runCatching {
            when (format) {
                FileFormat.PDF -> {
                    val count = PdfEngine.getPageCount(file)
                    FilePreviewContent.PdfPages(count, file)
                }
                FileFormat.PNG, FileFormat.JPG, FileFormat.WEBP, FileFormat.BMP -> {
                    val dims = ImageEngine.inspectDimensions(file) ?: (0 to 0)
                    FilePreviewContent.ImageFile(file, dims.first, dims.second)
                }
                FileFormat.XLSX -> {
                    val data = OoxmlSpreadsheetHandler.readXlsx(file)
                    val first = data.sheets.firstOrNull()
                    FilePreviewContent.TableGrid(first?.name ?: "Sheet 1", first?.rows ?: emptyList())
                }
                FileFormat.CSV -> {
                    val data = OoxmlSpreadsheetHandler.readDelimited(file.readText(Charsets.UTF_8), ',')
                    val first = data.sheets.firstOrNull()
                    FilePreviewContent.TableGrid(first?.name ?: "CSV", first?.rows ?: emptyList())
                }
                FileFormat.TSV -> {
                    val data = OoxmlSpreadsheetHandler.readDelimited(file.readText(Charsets.UTF_8), '\t')
                    val first = data.sheets.firstOrNull()
                    FilePreviewContent.TableGrid(first?.name ?: "TSV", first?.rows ?: emptyList())
                }
                FileFormat.DOCX -> {
                    val doc = OoxmlWordHandler.readDocx(file, file.nameWithoutExtension)
                    FilePreviewContent.DocumentBlocks(doc.blocks)
                }
                FileFormat.MD -> {
                    val doc = TextFormatConverter.fromMarkdown(file.nameWithoutExtension, file.readText(Charsets.UTF_8))
                    FilePreviewContent.DocumentBlocks(doc.blocks)
                }
                FileFormat.HTML -> {
                    val doc = TextFormatConverter.fromHtml(file.nameWithoutExtension, file.readText(Charsets.UTF_8))
                    FilePreviewContent.DocumentBlocks(doc.blocks)
                }
                FileFormat.TXT, FileFormat.JSON, FileFormat.XML -> {
                    FilePreviewContent.PlainCode(file.readText(Charsets.UTF_8).take(40_000))
                }
                FileFormat.ZIP -> {
                    FilePreviewContent.ZipContents(ZipEngine.inspectZip(file))
                }
            }
        }.getOrElse {
            FilePreviewContent.Unavailable(it.message ?: "Preview failed.")
        }
    }

    fun saveCopyToUri(sourceFile: File, destUri: Uri) {
        viewModelScope.launch(Dispatchers.IO) {
            val ok = FileHelper.copyFileToUri(getApplication(), sourceFile, destUri)
            _messages.tryEmit(if (ok) "Saved copy to device storage." else "Failed to save copy.")
        }
    }

    fun setHistoryFilter(category: FormatCategory?) {
        _historyFilter.value = category
    }

    fun setHistorySearch(query: String) {
        _historySearch.value = query
    }

    fun deleteRecord(record: ConversionRecord) {
        viewModelScope.launch {
            historyRepository.delete(record)
            refreshStorageStats()
            _messages.tryEmit("Removed ${record.outputName}")
        }
    }

    fun clearAllHistory() {
        viewModelScope.launch {
            historyRepository.clearAll(FileHelper.convertedDir(getApplication()))
            refreshStorageStats()
            _messages.tryEmit("Cleared conversion history.")
        }
    }

    fun setDefaultQuality(q: Int) = viewModelScope.launch {
        preferencesRepository.setDefaultQuality(q)
        updateOptions { it.copy(quality = q) }
    }

    fun setDefaultPdfDpi(dpi: PdfDpiPreset) = viewModelScope.launch {
        preferencesRepository.setDefaultPdfDpi(dpi)
        updateOptions { it.copy(pdfDpi = dpi) }
    }

    fun setDefaultPdfPageSize(size: PdfPageSize) = viewModelScope.launch {
        preferencesRepository.setDefaultPdfPageSize(size)
        updateOptions { it.copy(pdfPageSize = size) }
    }

    fun setStripExif(strip: Boolean) = viewModelScope.launch {
        preferencesRepository.setStripExif(strip)
        updateOptions { it.copy(stripExif = strip) }
    }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = (this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as ConvertFlowApp)
                ConvertFlowViewModel(
                    application = app,
                    historyRepository = app.container.historyRepository,
                    preferencesRepository = app.container.preferencesRepository,
                    conversionEngine = app.container.conversionEngine
                )
            }
        }
    }
}
