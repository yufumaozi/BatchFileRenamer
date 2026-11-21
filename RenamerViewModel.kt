package com.dsh.batchrenamer.ui

import android.app.Application
import android.content.Intent
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.dsh.batchrenamer.data.DocumentRepository
import com.dsh.batchrenamer.data.ExecutionResult
import com.dsh.batchrenamer.data.RenameExecutor
import com.dsh.batchrenamer.data.UndoStore
import com.dsh.batchrenamer.engine.ExifDates
import com.dsh.batchrenamer.engine.RenamePlanner
import com.dsh.batchrenamer.model.MediaFile
import com.dsh.batchrenamer.model.RenamePlan
import com.dsh.batchrenamer.model.RenameRule
import com.dsh.batchrenamer.model.SortOrder
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class RenamerUiState(
    val folderUri: Uri? = null,
    val folderName: String? = null,
    val files: List<MediaFile> = emptyList(),
    val loadingFolder: Boolean = false,
    val buildingPreview: Boolean = false,
    val rule: RenameRule = RenameRule(),
    val plans: List<RenamePlan> = emptyList(),
    val undoAvailable: Boolean = false,
    val error: String? = null,
)

sealed interface ExecutionUi {
    data object Idle : ExecutionUi
    data class Running(
        val stage: Int,
        val done: Int,
        val total: Int,
        val undoing: Boolean,
    ) : ExecutionUi {
        val fraction: Float
            get() = if (total <= 0) 0f else (done.toFloat() / total).coerceIn(0f, 1f)
    }

    data class Finished(
        val result: ExecutionResult,
        val undoing: Boolean,
    ) : ExecutionUi
}

class RenamerViewModel(application: Application) : AndroidViewModel(application) {

    private val app: Application = application
    private val repository = DocumentRepository(app)
    private val executor = RenameExecutor(app)
    private val undoStore = UndoStore(app)

    private val _state = MutableStateFlow(RenamerUiState())
    val state: StateFlow<RenamerUiState> = _state.asStateFlow()

    private val _execution = MutableStateFlow<ExecutionUi>(ExecutionUi.Idle)
    val execution: StateFlow<ExecutionUi> = _execution.asStateFlow()

    private var previewJob: Job? = null

    init {
        val snapshot = runCatching { undoStore.load() }.getOrNull()
        if (snapshot != null) {
            _state.update { it.copy(undoAvailable = true) }
            val uri = runCatching { Uri.parse(snapshot.treeUri) }.getOrNull()
            if (uri != null && runCatching { repository.folderName(uri) }.getOrNull() != null) {
                viewModelScope.launch { loadFolder(uri) }
            }
        }
    }

    fun onFolderPicked(uri: Uri) {
        val resolver = app.contentResolver
        val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
        runCatching { resolver.takePersistableUriPermission(uri, flags) }

        _state.update {
            it.copy(
                folderUri = uri,
                folderName = runCatching { repository.folderName(uri) }.getOrNull(),
                plans = emptyList(),
                error = null,
            )
        }
        viewModelScope.launch { loadFolder(uri) }
    }

    fun reload() {
        val uri = _state.value.folderUri ?: return
        viewModelScope.launch { loadFolder(uri) }
    }

    fun updateRule(transform: (RenameRule) -> RenameRule) {
        _state.update { it.copy(rule = transform(it.rule)) }
        schedulePreview()
    }

    fun clearError() {
        _state.update { it.copy(error = null) }
    }

    fun dismissExecution() {
        _execution.value = ExecutionUi.Idle
    }

    fun execute() {
        val snapshot = _state.value
        val uri = snapshot.folderUri ?: return
        if (snapshot.plans.none { !it.unchanged && it.conflict == null }) return

        _execution.value = ExecutionUi.Running(1, 0, snapshot.plans.size, undoing = false)
        viewModelScope.launch {
            val result = executor.execute(uri, snapshot.plans) { stage, done, total ->
                _execution.value = ExecutionUi.Running(stage, done, total, undoing = false)
            }
            if (result.renamed.isNotEmpty()) {
                runCatching { undoStore.save(uri.toString(), result.renamed) }
                _state.update { it.copy(undoAvailable = true) }
            }
            _execution.value = ExecutionUi.Finished(result, undoing = false)
            loadFolder(uri)
        }
    }

    fun undoLastBatch() {
        val snapshot = runCatching { undoStore.load() }.getOrNull() ?: return
        val uri = runCatching { Uri.parse(snapshot.treeUri) }.getOrNull() ?: return

        _execution.value = ExecutionUi.Running(1, 0, snapshot.records.size, undoing = true)
        viewModelScope.launch {
            val result = executor.undo(uri, snapshot.records) { stage, done, total ->
                _execution.value = ExecutionUi.Running(stage, done, total, undoing = true)
            }
            if (result.failureCount == 0) {
                runCatching { undoStore.clear() }
                _state.update { it.copy(undoAvailable = false) }
            }
            _execution.value = ExecutionUi.Finished(result, undoing = true)
            loadFolder(uri)
        }
    }

    private suspend fun loadFolder(uri: Uri) {
        _state.update { it.copy(loadingFolder = true, error = null) }
        try {
            val files = repository.listFiles(uri)
            val name = runCatching { repository.folderName(uri) }.getOrNull()
            _state.update {
                it.copy(files = files, folderName = name, loadingFolder = false)
            }
            schedulePreview()
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (t: Throwable) {
            _state.update {
                it.copy(
                    loadingFolder = false,
                    error = "读取文件夹失败：${t.message ?: t.javaClass.simpleName}",
                )
            }
        }
    }

    private fun schedulePreview() {
        previewJob?.cancel()
        previewJob = viewModelScope.launch {
            delay(180)
            buildPreview()
        }
    }

    private suspend fun buildPreview() {
        val snapshot = _state.value
        val files = snapshot.files
        val rule = snapshot.rule

        if (files.isEmpty()) {
            _state.update { it.copy(plans = emptyList(), buildingPreview = false) }
            return
        }

        _state.update { it.copy(buildingPreview = true) }
        val plans = try {
            withContext(Dispatchers.IO) {
                val needsCaptureTime = rule.sortOrder == SortOrder.TAKEN_ASC ||
                    rule.sortOrder == SortOrder.TAKEN_DESC ||
                    CAPTURE_TOKEN.containsMatchIn(rule.effectiveTemplate)

                val takenAt: (MediaFile) -> Long? = if (needsCaptureTime) {
                    { file -> ExifDates.takenAt(app, file.uri) }
                } else {
                    { null }
                }
                RenamePlanner(takenAt = takenAt).plan(files, rule)
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (t: Throwable) {
            _state.update {
                it.copy(
                    buildingPreview = false,
                    error = "生成预览失败：${t.message ?: t.javaClass.simpleName}",
                )
            }
            return
        }

        _state.update { it.copy(plans = plans, buildingPreview = false) }
    }

    private companion object {
        val CAPTURE_TOKEN = Regex("\\{(date|time|yyyy|MM|dd|HH|mm|ss)\\}")
    }
}
