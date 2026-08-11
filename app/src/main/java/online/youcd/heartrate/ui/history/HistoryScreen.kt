package online.youcd.heartrate.ui.history

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import online.youcd.heartrate.data.db.SessionEntity
import online.youcd.heartrate.data.model.HeartRateZone
import online.youcd.heartrate.ui.components.Sparkline
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun HistoryScreen(
    onSessionClick: (Long) -> Unit,
    onStartTraining: () -> Unit,
    viewModel: HistoryViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsState()
    val sparklines by viewModel.sparklines.collectAsState()

    var selectMode by remember { mutableStateOf(false) }
    var selectedIds by remember { mutableStateOf<Set<Long>>(emptySet()) }
    var showHidden by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }

    val listState = rememberLazyListState()

    val allSessions = state.groups.flatMap { it.sessions }

    Column(modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        // 标题区
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 12.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "训练历史",
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f)
            )
            if (selectMode) {
                IconButton(onClick = {
                    selectMode = false
                    selectedIds = emptySet()
                }) {
                    Text("取消", color = MaterialTheme.colorScheme.primary)
                }
            }
        }

        // 统计摘要
        Text(
            text = "本月训练 ${state.monthCount} 次 · 累计 ${formatDuration(state.monthDurationMillis)}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 8.dp)
        )

        // 批量选择操作栏
        if (selectMode) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.surfaceVariant,
                shape = RoundedCornerShape(14.dp)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "已选 ${selectedIds.size} 项",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.weight(1f)
                    )
                    Button(
                        onClick = {
                            if (selectedIds.isNotEmpty()) showDeleteConfirm = true
                        },
                        enabled = selectedIds.isNotEmpty()
                    ) {
                        Text("删除选中")
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
        }

        if (allSessions.isEmpty()) {
            EmptyState(onStartTraining = onStartTraining)
        } else {
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                state.groups.forEach { group ->
                    stickyHeader {
                        GroupHeader(group = group)
                    }
                    items(group.sessions, key = { it.id }) { session ->
                        SessionCard(
                            session = session,
                            sparkline = sparklines[session.id] ?: emptyList(),
                            selectMode = selectMode,
                            selected = session.id in selectedIds,
                            onClick = {
                                if (selectMode) {
                                    selectedIds = if (session.id in selectedIds) {
                                        selectedIds - session.id
                                    } else {
                                        selectedIds + session.id
                                    }
                                } else {
                                    onSessionClick(session.id)
                                }
                            },
                            onLongClick = {
                                selectMode = true
                                selectedIds = selectedIds + session.id
                            }
                        )
                    }
                }

                if (state.hiddenCount > 0 && !showHidden) {
                    item {
                        TextButton(
                            onClick = { showHidden = true },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                "已隐藏 ${state.hiddenCount} 条未完成的记录 · 展开",
                                color = Color.White.copy(alpha = 0.7f),
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                }
                if (state.hiddenCount > 0 && showHidden) {
                    item {
                        GroupHeader(
                            group = SessionGroup("未完成", state.hiddenSessions)
                        )
                    }
                    items(state.hiddenSessions, key = { it.id }) { session ->
                        SessionCard(
                            session = session,
                            sparkline = emptyList(),
                            selectMode = selectMode,
                            selected = session.id in selectedIds,
                            onClick = {
                                if (selectMode) {
                                    selectedIds = if (session.id in selectedIds) {
                                        selectedIds - session.id
                                    } else {
                                        selectedIds + session.id
                                    }
                                } else {
                                    onSessionClick(session.id)
                                }
                            },
                            onLongClick = {
                                selectMode = true
                                selectedIds = selectedIds + session.id
                            }
                        )
                    }
                    item {
                        TextButton(
                            onClick = { showHidden = false },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                "收起未完成的记录",
                                color = MaterialTheme.colorScheme.outline,
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                }
                item { Spacer(Modifier.height(16.dp)) }
            }
        }
    }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("删除选中的 ${selectedIds.size} 条记录？") },
            text = { Text("删除后无法恢复。") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.deleteSessions(selectedIds.toList())
                    showDeleteConfirm = false
                    selectMode = false
                    selectedIds = emptySet()
                }) {
                    Text("删除", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) {
                    Text("取消")
                }
            }
        )
    }

}

@Composable
private fun GroupHeader(group: SessionGroup) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.background)
            .padding(top = 14.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .width(3.dp)
                .height(14.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.6f))
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = group.label,
            style = MaterialTheme.typography.labelMedium,
            color = Color.White.copy(alpha = 0.85f),
            fontWeight = FontWeight.Bold
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = "${group.sessions.size} 次",
            style = MaterialTheme.typography.bodySmall,
            color = Color.White.copy(alpha = 0.5f)
        )
    }
}

@Composable
private fun SessionCard(
    session: SessionEntity,
    sparkline: List<Int>,
    selectMode: Boolean,
    selected: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {
    val isInvalid = session.durationMillis == 0L && session.maxBpm == 0
    val contentAlpha = if (isInvalid) 0.75f else 1f

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick,
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ),
        color = if (selected) {
            MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
        } else {
            MaterialTheme.colorScheme.surfaceVariant
        },
        shape = RoundedCornerShape(18.dp),
        border = androidx.compose.foundation.BorderStroke(
            width = if (selected) 1.5.dp else 0.dp,
            color = MaterialTheme.colorScheme.primary
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (selectMode) {
                Box(
                    modifier = Modifier
                        .size(22.dp)
                        .clip(CircleShape)
                        .background(
                            if (selected) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.outline.copy(alpha = 0.1f)
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    if (selected) {
                        Icon(
                            Icons.Filled.Check,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.size(14.dp)
                        )
                    }
                }
                Spacer(Modifier.width(10.dp))
            }

            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(
                        text = formatTime(session.startTime),
                        fontSize = 17.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color.White.copy(alpha = contentAlpha)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = if (session.durationMillis > 0) {
                            formatDuration(session.durationMillis)
                        } else {
                            "--:--"
                        },
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (isInvalid) {
                            MaterialTheme.colorScheme.outline
                        } else {
                            MaterialTheme.colorScheme.primary
                        }
                    )
                }
                Spacer(Modifier.height(3.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (!isInvalid) {
                        Text(
                            text = "自由训练",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.White.copy(alpha = 0.7f)
                        )
                    }
                }
            }

            // 迷你心率图
            if (!isInvalid && sparkline.isNotEmpty()) {
                val maxHr = if (session.maxHr > 0) session.maxHr else session.maxBpm
                Sparkline(
                    samples = sparkline,
                    color = Color(HeartRateZone.zoneColor(session.avgBpm.coerceAtLeast(1), maxHr)),
                    modifier = Modifier
                        .width(56.dp)
                        .height(26.dp)
                )
                Spacer(Modifier.width(10.dp))
            }

            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = if (session.calories > 0) {
                        "${session.calories}"
                    } else {
                        "--"
                    },
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (session.calories > 0) {
                        Color(0xFFFF2D55).copy(alpha = contentAlpha)
                    } else {
                        MaterialTheme.colorScheme.outline.copy(alpha = contentAlpha)
                    }
                )
                Text(
                    text = if (session.calories > 0) "千卡" else "未完成",
                    style = MaterialTheme.typography.bodySmall,
                    color = if (session.calories > 0) Color(0xFFFF2D55).copy(alpha = 0.8f) else Color.White.copy(alpha = 0.6f)
                )
                Text(
                    text = if (session.avgBpm > 0) "平均 ${session.avgBpm}" else "",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.White.copy(alpha = 0.6f)
                )
            }
        }
    }
}

@Composable
private fun EmptyState(onStartTraining: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier
                .size(72.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                Icons.Filled.Favorite,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(36.dp)
            )
        }
        Spacer(Modifier.height(16.dp))
        Text(
            text = "还没有训练记录",
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onSurface
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = "去开启第一次运动吧",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(20.dp))
        Button(
            onClick = onStartTraining,
            shape = RoundedCornerShape(16.dp)
        ) {
            Icon(Icons.Filled.PlayArrow, contentDescription = null)
            Spacer(Modifier.width(6.dp))
            Text("开始训练")
        }
    }
}

private fun formatTime(millis: Long): String =
    SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(millis))

private fun formatDuration(millis: Long): String {
    val totalSeconds = millis / 1000
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    return when {
        hours > 0 -> String.format(Locale.US, "%d:%02d:%02d", hours, minutes, seconds)
        else -> String.format(Locale.US, "%02d:%02d", minutes, seconds)
    }
}
