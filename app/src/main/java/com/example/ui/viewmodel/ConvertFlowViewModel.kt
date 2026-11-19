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
import com.example.domain.model.ConversionOptions
import com.example.domain.model.ConversionUiState
import com.example.domain.model.FileFormat
import com.example.domain.model.FormatCategory
import com.example.domain.model.PdfDpiPreset
import com.example.domain.model.PdfPageSize
import com.example.domain.model.PickedFile
import com.example.domain.model.ResizeMode
import com.example.domain.model.StudioTool
import com.example.domain.model.ThemePreference
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

    private val _targetFormat = MutableStateFlow<FileFormat?>(null)
    val targetFormat: StateFlow<FileFormat?> = _targetFormat.asStateFlow()

    private val _availableTargets = MutableStateFlow<List<FileFormat>>(emptyList())
    val availableTargets: StateFlow<List<FileFormat>> = _availableTargets.asStateFlow()

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

    fun onUrisPicked(uris: List<Uri>) {
        if (uris.isEmpty()) return
        viewModelScope.launch {
            _isInspecting.value = true
            _conversionState.value = ConversionUiState.Idle
            val inspected = withContext(Dispatchers.IO) {
                uris.mapNotNull { uri -> FileHelper.inspectUri(getApplication(), uri) }
            }
            _isInspecting.value = false

            if (inspected.isEmpty()) {
                _messages.tryEmit("Unsupported file format. Try PDF, DOCX, XLSX, CSV, TXT, MD, HTML, JSON, or an image.")
                return@launch
            }

            if (inspected.size < uris.size) {
                _messages.tryEmit("Skipped ${uris.size - inspected.size} unsupported file(s).")
            }

            _pickedFiles.value = inspected
            recomputeTargetsForSelection(inspected)
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

    private fun recomputeTargetsForSelection(files: List<PickedFile>) {
        if (files.isEmpty()) {
            _availableTargets.value = emptyList()
            _targetFormat.value = null
            return
        }
        val primary = files.first()
        val multi = files.size > 1
        val targets = FileFormat.supportedTargetsFor(primary.format, multipleFiles = multi)
        _availableTargets.value = targets

        val tool = _activeTool.value
        val preferred = when (tool) {
            StudioTool.COMPRESS_IMAGE -> primary.format.takeIf { it.category == FormatCategory.IMAGE }
            StudioTool.RESIZE_IMAGE -> primary.format.takeIf { it.category == FormatCategory.IMAGE }
            StudioTool.IMAGES_TO_PDF -> FileFormat.PDF
            StudioTool.PDF_TO_IMAGES -> FileFormat.PNG
            StudioTool.COMPRESS_PDF, StudioTool.RESIZE_PDF, StudioTool.MERGE_PDF, StudioTool.SPLIT_PDF -> FileFormat.PDF
            StudioTool.ZIP_ARCHIVE -> FileFormat.ZIP
            else -> when {
                multi && files.all { it.format.category == FormatCategory.IMAGE } -> FileFormat.PDF
                multi && files.all { it.format == FileFormat.PDF } -> FileFormat.PDF
                multi -> FileFormat.ZIP
                primary.format == FileFormat.PDF -> FileFormat.DOCX
                primary.format == FileFormat.DOCX -> FileFormat.PDF
                primary.format == FileFormat.XLSX -> FileFormat.CSV
                primary.format == FileFormat.CSV -> FileFormat.XLSX
                primary.format == FileFormat.JSON -> FileFormat.CSV
                primary.format == FileFormat.PNG -> FileFormat.JPG
                primary.format == FileFormat.JPG -> FileFormat.PDF
                else -> targets.firstOrNull()
            }
        }
        _targetFormat.value = if (preferred != null && preferred in targets) preferred else targets.firstOrNull()
    }

    fun selectTargetFormat(format: FileFormat) {
        _targetFormat.value = format
        _conversionState.value = ConversionUiState.Idle
    }

    fun updateOptions(transform: (ConversionOptions) -> ConversionOptions) {
        _options.value = transform(_options.value)
    }

    fun launchStudioTool(tool: StudioTool) {
        _activeTool.value = tool
        _conversionState.value = ConversionUiState.Idle
        when (tool) {
            StudioTool.COMPRESS_IMAGE -> {
                updateOptions { it.copy(quality = 72, targetSizeKb = null, resizeMode = ResizeMode.NONE) }
            }
            StudioTool.RESIZE_IMAGE -> {
                updateOptions { it.copy(resizeMode = ResizeMode.PERCENTAGE, scalePercent = 50, targetSizeKb = null) }
            }
            StudioTool.IMAGES_TO_PDF -> {
                updateOptions { it.copy(pdfPageSize = PdfPageSize.A4, quality = 90) }
            }
            StudioTool.PDF_TO_IMAGES -> {
                updateOptions { it.copy(pdfDpi = PdfDpiPreset.EBOOK_150, quality = 90) }
            }
            StudioTool.COMPRESS_PDF -> {
                updateOptions { it.copy(pdfDpi = PdfDpiPreset.SCREEN_72, quality = 70, pdfPageSize = PdfPageSize.ORIGINAL, pageRange = "") }
            }
            StudioTool.RESIZE_PDF -> {
                updateOptions { it.copy(pdfPageSize = PdfPageSize.A4, pdfDpi = PdfDpiPreset.EBOOK_150, quality = 85, pageRange = "") }
            }
            StudioTool.MERGE_PDF -> {
                updateOptions { it.copy(pageRange = "") }
            }
            StudioTool.SPLIT_PDF -> {
                updateOptions { it.copy(pageRange = "1", pdfPageSize = PdfPageSize.ORIGINAL, quality = 92) }
            }
            StudioTool.SPREADSHEET_STUDIO -> Unit
            StudioTool.ZIP_ARCHIVE -> Unit
        }
        val current = _pickedFiles.value
        if (current.isNotEmpty()) {
            recomputeTargetsForSelection(current)
        }
    }

    fun clearActiveTool() {
        _activeTool.value = null
    }

    fun clearSelection() {
        _pickedFiles.value = emptyList()
        _availableTargets.value = emptyList()
        _targetFormat.value = null
        _conversionState.value = ConversionUiState.Idle
        _activeTool.value = null
    }

    fun resetConversionState() {
        _conversionState.value = ConversionUiState.Idle
    }

    fun runConversion(customOpLabel: String? = null) {
        val files = _pickedFiles.value
        val target = _targetFormat.value
        if (files.isEmpty() || target == null) {
            _messages.tryEmit("Select a file and output format first.")
            return
        }

        viewModelScope.launch {
            _conversionState.value = ConversionUiState.Running(
                com.example.domain.model.ConversionProgress("Starting local engine...", 0.05f)
            )
            runCatching {
                conversionEngine.executeConversion(
                    pickedFiles = files,
                    targetFormat = target,
                    options = _options.value,
                    operationLabel = customOpLabel ?: _activeTool.value?.title
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
                FileFormat.TXT, FileFormat.JSON -> {
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
            _messages.tryEmit("Cleared local conversion history.")
        }
    }

    fun setTheme(theme: ThemePreference) = viewModelScope.launch { preferencesRepository.setTheme(theme) }
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
