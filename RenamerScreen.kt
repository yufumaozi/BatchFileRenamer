@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.dsh.batchrenamer.ui

import android.app.Application
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Undo
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.dsh.batchrenamer.model.FileScope
import com.dsh.batchrenamer.model.NamingMode
import com.dsh.batchrenamer.model.RenamePlan
import com.dsh.batchrenamer.model.RenameRule
import com.dsh.batchrenamer.model.SortOrder

private const val MAX_PREVIEW_ROWS = 200

private val TEMPLATE_PRESETS = listOf(
    "{name}_{n}",
    "{n}",
    "IMG_{date}_{n}",
    "{yyyy}{MM}{dd}_{n}",
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RenamerScreen() {
    val context = LocalContext.current
    val viewModel: RenamerViewModel = viewModel(
        factory = viewModelFactory {
            initializer {
                RenamerViewModel(context.applicationContext as Application)
            }
        }
    )

    val state by viewModel.state.collectAsState()
    val execution by viewModel.execution.collectAsState()

    val snackbarHostState = remember { SnackbarHostState() }
    LaunchedEffect(state.error) {
        val message = state.error
        if (message != null) {
            snackbarHostState.showSnackbar(message)
            viewModel.clearError()
        }
    }

    val pickFolder = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        if (uri != null) viewModel.onFolderPicked(uri)
    }

    var showConfirm by remember { mutableStateOf(false) }

    val actionable = state.plans.count { !it.unchanged && it.conflict == null }
    val conflicts = state.plans.count { it.conflict != null }
    val folderChosen = state.folderUri != null

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("批量重命名") },
                actions = {
                    if (state.undoAvailable) {
                        IconButton(onClick = { viewModel.undoLastBatch() }) {
                            Icon(Icons.Default.Undo, contentDescription = "撤销上一批")
                        }
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        bottomBar = {
            Surface(tonalElevation = 3.dp, shadowElevation = 12.dp) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .padding(horizontal = 16.dp, vertical = 12.dp)
                ) {
                    Button(
                        onClick = { showConfirm = true },
                        enabled = folderChosen && actionable > 0 && execution is ExecutionUi.Idle,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp)
                    ) {
                        Text(
                            when {
                                !folderChosen -> "请先选择文件夹"
                                actionable == 0 -> "没有可重命名的文件"
                                else -> "开始重命名（$actionable 个文件）"
                            }
                        )
                    }
                }
            }
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                FolderSection(
                    state = state,
                    onPick = { pickFolder.launch(null) },
                    onReload = viewModel::reload,
                )
            }
            item {
                RuleSection(
                    state = state,
                    onRuleChange = { viewModel.updateRule(it) },
                )
            }
            item {
                SectionHeader(
                    title = "3 · 预览",
                    trailing = if (state.plans.isEmpty()) "" else "${state.plans.size} 项",
                )
            }
            if (state.buildingPreview) {
                item {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth().height(2.dp))
                }
            }
            if (conflicts > 0) {
                item {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Default.Warning,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text = "$conflicts 项名称冲突，执行时会自动跳过",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                }
            }

            val shown = state.plans.take(MAX_PREVIEW_ROWS)
            items(shown, key = { it.file.uri.toString() }) { plan ->
                PlanRow(plan)
            }

            if (state.plans.size > MAX_PREVIEW_ROWS) {
                item {
                    Text(
                        text = "预览只显示前 $MAX_PREVIEW_ROWS 项，实际会处理全部 ${state.plans.size} 项",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }

    if (showConfirm) {
        AlertDialog(
            onDismissRequest = { showConfirm = false },
            title = { Text("确认重命名？") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("将把 $actionable 个文件改成新名字。")
                    if (conflicts > 0) {
                        Text(
                            "$conflicts 个冲突项会被跳过。",
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                    Text(
                        "完成后可以通过右上角按钮撤销。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    showConfirm = false
                    viewModel.execute()
                }) { Text("开始") }
            },
            dismissButton = {
                TextButton(onClick = { showConfirm = false }) { Text("取消") }
            },
        )
    }

    when (val current = execution) {
        is ExecutionUi.Running -> ProgressDialog(current)
        is ExecutionUi.Finished -> ResultDialog(
            result = current.result,
            undoing = current.undoing,
            onDismiss = { viewModel.dismissExecution() },
            onUndo = {
                viewModel.dismissExecution()
                viewModel.undoLastBatch()
            },
        )
        ExecutionUi.Idle -> Unit
    }
}

@Composable
private fun SectionHeader(title: String, trailing: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            modifier = Modifier.weight(1f),
        )
        if (trailing.isNotEmpty()) {
            Text(
                text = trailing,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun FolderSection(
    state: RenamerUiState,
    onPick: () -> Unit,
    onReload: () -> Unit,
) {
    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text("1 · 选择文件夹", style = MaterialTheme.typography.titleSmall)

            Text(
                text = state.folderName ?: "还没有选择文件夹",
                style = MaterialTheme.typography.bodyMedium,
                color = if (state.folderName == null) {
                    MaterialTheme.colorScheme.onSurfaceVariant
                } else {
                    MaterialTheme.colorScheme.onSurface
                },
            )

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = onPick) {
                    Icon(Icons.Default.Folder, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("选择文件夹")
                }
                if (state.folderUri != null) {
                    OutlinedButton(onClick = onReload) {
                        Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("刷新")
                    }
                }
            }

            when {
                state.loadingFolder -> LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                state.folderUri != null -> Text(
                    text = "共发现 ${state.files.size} 个文件",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                else -> Unit
            }
        }
    }
}

@Composable
private fun RuleSection(
    state: RenamerUiState,
    onRuleChange: (transform: (RenameRule) -> RenameRule) -> Unit,
) {
    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("2 · 命名规则", style = MaterialTheme.typography.titleSmall)

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = state.rule.mode == NamingMode.TEMPLATE,
                    onClick = { onRuleChange { it.copy(mode = NamingMode.TEMPLATE) } },
                    label = { Text("自定义模板") },
                )
                FilterChip(
                    selected = state.rule.mode == NamingMode.SEQUENCE,
                    onClick = { onRuleChange { it.copy(mode = NamingMode.SEQUENCE) } },
                    label = { Text("纯序列号") },
                )
            }

            if (state.rule.mode == NamingMode.TEMPLATE) {
                OutlinedTextField(
                    value = state.rule.template,
                    onValueChange = { value -> onRuleChange { it.copy(template = value) } },
                    label = { Text("命名模板") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Text(
                    text = "{name} 原名 · {n} 序号 · {date} 日期 · {time} 时间 · {ext} 扩展名",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    TEMPLATE_PRESETS.forEach { preset ->
                        AssistChip(
                            onClick = { onRuleChange { it.copy(template = preset) } },
                            label = { Text(preset, fontFamily = FontFamily.Monospace, fontSize = 12.sp) },
                        )
                    }
                }
            } else {
                Text(
                    text = "结果形如 001.jpg、002.jpg（按下方位数补零）",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                NumberField(
                    label = "起始序号",
                    value = state.rule.startIndex,
                    onValueChange = { value -> onRuleChange { it.copy(startIndex = value) } },
                    modifier = Modifier.weight(1f),
                )
                NumberField(
                    label = "补零位数",
                    value = state.rule.digits,
                    onValueChange = { value -> onRuleChange { it.copy(digits = value) } },
                    modifier = Modifier.weight(1f),
                    hint = "0 = 不补零",
                )
            }

            SortOrderPicker(
                selected = state.rule.sortOrder,
                onSelect = { order -> onRuleChange { it.copy(sortOrder = order) } },
            )

            ScopePicker(
                selected = state.rule.scope,
                onSelect = { scope -> onRuleChange { it.copy(scope = scope) } },
            )
        }
    }
}

@Composable
private fun NumberField(
    label: String,
    value: Int,
    onValueChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
    hint: String? = null,
) {
    var text by remember(value) { mutableStateOf(value.toString()) }
    val fieldLabel = if (hint.isNullOrBlank()) label else "$label（$hint）"

    OutlinedTextField(
        value = text,
        onValueChange = { raw ->
            val digitsOnly = raw.filter { it.isDigit() }.take(6)
            text = digitsOnly
            digitsOnly.toIntOrNull()?.let(onValueChange)
        },
        label = { Text(fieldLabel) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        modifier = modifier,
    )
}

@Composable
private fun SortOrderPicker(
    selected: SortOrder,
    onSelect: (SortOrder) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }

    Box {
        OutlinedButton(
            onClick = { expanded = true },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(
                text = "排序：${selected.label}",
                modifier = Modifier.weight(1f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.bodyMedium,
            )
            Icon(Icons.Default.ArrowDropDown, contentDescription = null)
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            SortOrder.entries.forEach { order ->
                DropdownMenuItem(
                    text = { Text(order.label) },
                    onClick = {
                        onSelect(order)
                        expanded = false
                    },
                )
            }
        }
    }
}

@Composable
private fun ScopePicker(
    selected: FileScope,
    onSelect: (FileScope) -> Unit,
) {
    val options = FileScope.entries
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        options.chunked(2).forEach { rowItems ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                rowItems.forEach { scope ->
                    FilterChip(
                        selected = selected == scope,
                        onClick = { onSelect(scope) },
                        label = { Text(scope.label) },
                    )
                }
            }
        }
    }
}

@Composable
private fun PlanRow(plan: RenamePlan) {
    val conflict = plan.conflict
    val targetColor = when {
        conflict != null -> MaterialTheme.colorScheme.error
        plan.unchanged -> MaterialTheme.colorScheme.onSurfaceVariant
        else -> MaterialTheme.colorScheme.primary
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = plan.file.name,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = when {
                    conflict != null -> "✗ ${conflict.label}：${plan.targetName}"
                    plan.unchanged -> "= 名字不变"
                    else -> "→ ${plan.targetName}"
                },
                style = MaterialTheme.typography.bodySmall,
                color = targetColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun ProgressDialog(running: ExecutionUi.Running) {
    AlertDialog(
        onDismissRequest = { },
        title = { Text(if (running.undoing) "正在撤销" else "正在重命名") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                LinearProgressIndicator(
                    progress = { running.fraction },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(6.dp),
                )
                Text(
                    text = "第 ${running.stage} / 2 步 · ${running.done} / ${running.total}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = "请不要关闭应用",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = { },
    )
}

@Composable
private fun ResultDialog(
    result: com.dsh.batchrenamer.data.ExecutionResult,
    undoing: Boolean,
    onDismiss: () -> Unit,
    onUndo: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (undoing) "撤销完成" else "重命名完成") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = if (undoing) {
                        "已还原 ${result.successCount} 个文件"
                    } else {
                        "成功 ${result.successCount} 个，失败 ${result.failureCount} 个"
                    },
                    style = MaterialTheme.typography.bodyMedium,
                )

                if (result.failures.isNotEmpty()) {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        result.failures.take(8).forEach { failure ->
                            Text(
                                text = "· ${failure.fileName}：${failure.reason}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        if (result.failures.size > 8) {
                            Text(
                                text = "…… 还有 ${result.failures.size - 8} 条失败记录",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error,
                            )
                        }
                    }
                }

                if (!undoing && result.successCount > 0) {
                    TextButton(onClick = onUndo) { Text("撤销这次修改") }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("完成") }
        },
    )
}
