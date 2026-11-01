package com.dshremote.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.FolderZip
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.InstallMobile
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.TableChart
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dshremote.app.AppViewModel
import com.dshremote.app.FileGroup
import com.dshremote.app.RemoteFile
import com.dshremote.app.UiState
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private data class KindChip(val key: String, val label: String)

private val KIND_CHIPS = listOf(
    KindChip("all", "全部"),
    KindChip("sheet", "表格"),
    KindChip("doc", "文档"),
    KindChip("image", "图片"),
    KindChip("archive", "压缩包"),
    KindChip("apk", "安装包"),
    KindChip("other", "其他"),
)

private fun iconFor(kind: String): ImageVector = when (kind) {
    "sheet" -> Icons.Outlined.TableChart
    "image" -> Icons.Outlined.Image
    "archive" -> Icons.Outlined.FolderZip
    "apk" -> Icons.Outlined.InstallMobile
    else -> Icons.Outlined.Description
}

/** 交付物：列出电脑会话目录里的文件，点一下取回到手机并打开 */
@Composable
fun FilesSheet(vm: AppViewModel, st: UiState) {
    if (!st.filesOpen) return
    var kind by remember(st.filesOverview) { mutableStateOf("all") }

    val match: (RemoteFile) -> Boolean = { f ->
        kind == "all" || when (kind) {
            "other" -> f.kind !in setOf("sheet", "doc", "image", "archive", "apk")
            else -> f.kind == kind
        }
    }
    val groups: List<FileGroup> = if (st.filesOverview) {
        st.fileGroups.mapNotNull { g ->
            val fs = g.files.filter(match)
            if (fs.isEmpty()) null else g.copy(files = fs)
        }
    } else {
        emptyList()
    }
    val single = if (st.filesOverview) emptyList() else st.files.filter(match)
    val total = if (st.filesOverview) groups.sumOf { it.files.size } else single.size

    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.55f)),
        contentAlignment = Alignment.BottomCenter,
    ) {
        Surface(
            color = MaterialTheme.colorScheme.surface,
            shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.76f),
        ) {
            Column(Modifier.fillMaxSize()) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(start = 18.dp, end = 6.dp, top = 12.dp, bottom = 2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text("交付物", fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
                        Text(
                            when {
                                st.filesOverview -> "全部会话 · $total 个文件"
                                st.filesCwd.isNotBlank() -> st.filesCwd
                                else -> "电脑上的文件"
                            },
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    IconButton(onClick = { vm.refreshFiles() }) {
                        Icon(Icons.Outlined.Refresh, contentDescription = "刷新", modifier = Modifier.size(20.dp))
                    }
                    IconButton(onClick = { vm.closeFiles() }) {
                        Icon(Icons.Outlined.Close, contentDescription = "关闭", modifier = Modifier.size(20.dp))
                    }
                }

                // 分类筛选
                Row(
                    Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = 14.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    KIND_CHIPS.forEach { c ->
                        val selected = kind == c.key
                        Surface(
                            color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                            shape = RoundedCornerShape(50),
                            modifier = Modifier.clickable { kind = c.key },
                        ) {
                            Text(
                                c.label,
                                fontSize = 12.sp,
                                color = if (selected) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 11.dp, vertical = 5.dp),
                            )
                        }
                    }
                }

                if (st.filesLoading) {
                    LinearProgressIndicator(Modifier.fillMaxWidth().height(2.dp))
                }

                when {
                    total == 0 && !st.filesLoading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(
                            if (kind == "all") "还没有交付物" else "这个分类下没有文件",
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }

                    st.filesOverview -> LazyColumn(Modifier.fillMaxSize()) {
                        groups.forEach { g ->
                            item(key = "h-" + g.sessionId) { GroupHeader(g) }
                            items(g.files, key = { g.sessionId + "/" + it.path }) { f ->
                                FileRow(f, busy = st.filesBusy == f.path) { vm.downloadFile(f, g.sessionId) }
                            }
                        }
                    }

                    else -> LazyColumn(Modifier.fillMaxSize()) {
                        items(single, key = { it.path }) { f ->
                            FileRow(f, busy = st.filesBusy == f.path) { vm.downloadFile(f) }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun GroupHeader(g: FileGroup) {
    Column(
        Modifier
            .fillMaxWidth()
            .padding(start = 18.dp, end = 18.dp, top = 12.dp, bottom = 4.dp),
    ) {
        Text(
            g.title?.ifBlank { null } ?: "未命名会话",
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            "${g.cwd} · ${g.files.size} 个文件",
            fontSize = 10.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun FileRow(f: RemoteFile, busy: Boolean, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(enabled = !busy, onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(38.dp)
                .clip(RoundedCornerShape(11.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                iconFor(f.kind),
                contentDescription = null,
                modifier = Modifier.size(19.dp),
                tint = MaterialTheme.colorScheme.onSurface,
            )
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(f.name, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                "${humanSize(f.size)} · ${shortTime(f.mtime)}",
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.width(8.dp))
        if (busy) {
            CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
        } else {
            Icon(
                Icons.Outlined.Download,
                contentDescription = "取回",
                modifier = Modifier.size(19.dp),
                tint = MaterialTheme.colorScheme.primary,
            )
        }
    }
}

private fun humanSize(bytes: Long): String = when {
    bytes >= 1024 * 1024 -> String.format(Locale.US, "%.1f MB", bytes / 1024.0 / 1024.0)
    bytes >= 1024 -> String.format(Locale.US, "%.1f KB", bytes / 1024.0)
    else -> "$bytes B"
}

private fun shortTime(ms: Long): String =
    if (ms <= 0) "" else SimpleDateFormat("MM-dd HH:mm", Locale.getDefault()).format(Date(ms))
