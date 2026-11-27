package com.cybertube.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.cybertube.app.core.ChannelData
import com.cybertube.app.core.FeedMerge
import com.cybertube.app.core.Store
import com.cybertube.app.core.Sub
import com.cybertube.app.core.VideoItem
import com.cybertube.app.core.YtRepo
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import org.schabi.newpipe.extractor.Page

// ======================= SEARCH =======================

class SearchVM : ViewModel() {
    var query by mutableStateOf("")
    var results by mutableStateOf<List<VideoItem>>(emptyList())
    var next by mutableStateOf<Page?>(null)
    var loading by mutableStateOf(false)
    var error by mutableStateOf<String?>(null)
    var suggestions by mutableStateOf<List<String>>(emptyList())
    private var searched: String? = null
    private var suggJob: Job? = null

    fun onQueryChange(q: String) {
        query = q
        suggJob?.cancel()
        if (q.trim().length < 2) {
            suggestions = emptyList(); return
        }
        suggJob = viewModelScope.launch {
            delay(250)
            suggestions = runCatching { YtRepo.suggestions(q.trim()) }.getOrDefault(emptyList())
        }
    }

    fun search(q: String = query) {
        val s = q.trim()
        if (s.isEmpty()) return
        query = s
        suggJob?.cancel()
        suggestions = emptyList()
        searched = s
        loading = true
        error = null
        viewModelScope.launch {
            try {
                val p = YtRepo.search(s)
                results = p.items
                next = p.next
            } catch (e: Exception) {
                error = e.message ?: e.javaClass.simpleName
                results = emptyList()
                next = null
            } finally {
                loading = false
            }
        }
    }

    fun more() {
        val n = next ?: return
        val s = searched ?: return
        if (loading) return
        loading = true
        viewModelScope.launch {
            try {
                val p = YtRepo.searchMore(s, n)
                val known = results.map { it.url }.toSet()
                results = results + p.items.filter { it.url !in known }
                next = p.next
            } catch (e: Exception) {
                error = e.message ?: e.javaClass.simpleName
                next = null
            } finally {
                loading = false
            }
        }
    }
}

@Composable
fun SearchScreen(onPlay: (VideoItem) -> Unit) {
    val vm: SearchVM = viewModel()
    val listState = rememberLazyListState()

    // load more when near the end
    LaunchedEffect(listState, vm.results.size) {
        snapshotFlow { listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0 }
            .collect { last -> if (vm.results.isNotEmpty() && last >= vm.results.size - 3) vm.more() }
    }

    Column(Modifier.fillMaxSize()) {
        OutlinedTextField(
            value = vm.query,
            onValueChange = vm::onQueryChange,
            singleLine = true,
            placeholder = { Text("ค้นหาใน YouTube", color = Cy.dim) },
            leadingIcon = { Icon(Icons.Filled.Search, null, tint = Cy.green) },
            trailingIcon = {
                if (vm.query.isNotEmpty()) {
                    IconButton(onClick = { vm.onQueryChange("") }) {
                        Icon(Icons.Filled.Close, null, tint = Cy.dim)
                    }
                }
            },
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { vm.search() }),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = Cy.green,
                unfocusedBorderColor = Cy.dim.copy(alpha = 0.5f),
                cursorColor = Cy.green,
                focusedTextColor = Cy.text,
                unfocusedTextColor = Cy.text,
            ),
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
        )

        if (vm.suggestions.isNotEmpty()) {
            LazyColumn(Modifier.fillMaxSize()) {
                items(vm.suggestions) { s ->
                    Text(
                        "> $s",
                        color = Cy.text,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { vm.search(s) }
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                    )
                }
            }
        } else if (vm.results.isEmpty() && !vm.loading && vm.error == null &&
            Store.account.value != null && Store.subs.isNotEmpty()
        ) {
            HomeFeed(onPlay)
        } else if (vm.results.isEmpty() && !vm.loading) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CyberLogo(96.dp)
                    Row {
                        Text("Cyber", color = androidx.compose.ui.graphics.Color.White, fontSize = 28.sp, style = MaterialTheme.typography.titleLarge)
                        Text("Tube", color = Cy.cyan, fontSize = 28.sp, style = MaterialTheme.typography.titleLarge)
                    }
                    Text(
                        vm.error?.let { "เกิดข้อผิดพลาด: $it" } ?: "// พร้อมแล้ว พิมพ์คำค้นหาได้เลย",
                        color = if (vm.error != null) Cy.red else Cy.dim,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(16.dp),
                    )
                }
            }
        } else {
            LazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
                items(vm.results, key = { it.url }) { v -> VideoRow(v, onClick = { onPlay(v) }) }
                if (vm.loading) {
                    item {
                        Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(color = Cy.green)
                        }
                    }
                }
            }
        }
    }
}

/** หน้าแนะนำ: วิดีโอล่าสุดจากช่องที่ติดตาม (แสดงเฉพาะตอนเชื่อมบัญชี Google) ใช้ฟีด/แคชชุดเดียวกับแท็บติดตาม */
@Composable
private fun HomeFeed(onPlay: (VideoItem) -> Unit) {
    val vm: FeedVM = viewModel()
    LaunchedEffect(Store.subs.size) { vm.refresh() }
    LazyColumn(Modifier.fillMaxSize()) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.weight(1f)) { SectionLabel("// แนะนำ จากช่องที่ติดตาม") }
                if (vm.loading) {
                    CircularProgressIndicator(color = Cy.green, strokeWidth = 2.dp, modifier = Modifier.padding(14.dp).size(20.dp))
                } else {
                    IconButton(onClick = { vm.refresh(force = true) }) {
                        Icon(Icons.Filled.Refresh, "รีเฟรช", tint = Cy.green)
                    }
                }
            }
        }
        if (vm.failed > 0) {
            item {
                Text(
                    "// โหลดไม่สำเร็จ ${vm.failed} ช่อง กดรีเฟรชเพื่อลองใหม่",
                    color = Cy.red, fontSize = 11.sp, modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                )
            }
        }
        items(vm.items, key = { it.url }) { v -> VideoRow(v, onClick = { onPlay(v) }) }
    }
}

// ======================= SUBSCRIPTIONS =======================

class FeedVM : ViewModel() {
    var items by mutableStateOf<List<VideoItem>>(emptyList())
    var loading by mutableStateOf(false)
    var failed by mutableStateOf(0) // channels that could not be loaded in the last refresh
    private var job: Job? = null

    /** Last good result per channel url: (fetch time, videos). */
    private val cache = HashMap<String, Pair<Long, List<VideoItem>>>()

    /** Only channels that are new or older than 15 min are fetched; [force] fetches all of them. */
    fun refresh(force: Boolean = false) {
        val subs = Store.subs.toList().take(20)
        job?.cancel()
        failed = 0
        val now = System.currentTimeMillis()
        val stale = subs.filter { s ->
            val c = cache[s.url]
            force || c == null || now - c.first >= TTL_MS
        }
        publish(subs) // show what we already have (and drop unsubscribed channels) right away
        if (stale.isEmpty()) {
            loading = false; return
        }
        loading = true
        job = viewModelScope.launch {
            val gate = Semaphore(PARALLEL)
            val results = stale.map { s ->
                async {
                    gate.withPermit {
                        try {
                            val videos = YtRepo.channel(s.url).items.take(6)
                            cache[s.url] = System.currentTimeMillis() to videos
                            true
                        } catch (e: CancellationException) {
                            throw e
                        } catch (e: Exception) {
                            false // keep the older result; retried on the next refresh
                        }
                    }
                }
            }.awaitAll()
            failed = results.count { !it }
            publish(subs)
            loading = false
        }
    }

    private fun publish(subs: List<Sub>) {
        items = FeedMerge.newestFirst(subs.mapNotNull { cache[it.url]?.second }, { it.uploadedAt }, { it.url })
    }

    private companion object {
        const val TTL_MS = 15 * 60_000L
        const val PARALLEL = 4
    }
}

@Composable
fun SubsScreen(onPlay: (VideoItem) -> Unit, onChannel: (String) -> Unit) {
    val vm: FeedVM = viewModel()
    LaunchedEffect(Store.subs.size) { vm.refresh() }

    if (Store.subs.isEmpty()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(
                "// ยังไม่ได้ติดตามช่องใด\nเปิดวิดีโอแล้วกด \"ติดตาม\"",
                color = Cy.dim,
                textAlign = TextAlign.Center,
            )
        }
        return
    }
    LazyColumn(Modifier.fillMaxSize()) {
        item {
            LazyRow(
                contentPadding = androidx.compose.foundation.layout.PaddingValues(12.dp),
                horizontalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                items(Store.subs, key = { it.url }) { s ->
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.width(64.dp).clickable { onChannel(s.url) },
                    ) {
                        Avatar(s.avatar, 52)
                        Text(
                            s.name, color = Cy.text, fontSize = 10.sp, maxLines = 1,
                            overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 4.dp),
                        )
                    }
                }
            }
        }
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.weight(1f)) { SectionLabel("// ล่าสุด") }
                if (vm.loading) {
                    CircularProgressIndicator(color = Cy.green, strokeWidth = 2.dp, modifier = Modifier.padding(14.dp).size(20.dp))
                } else {
                    IconButton(onClick = { vm.refresh(force = true) }) {
                        Icon(Icons.Filled.Refresh, "รีเฟรชฟีด", tint = Cy.green)
                    }
                }
            }
        }
        if (vm.failed > 0) {
            item {
                Text(
                    "// โหลดไม่สำเร็จ ${vm.failed} ช่อง กดรีเฟรชเพื่อลองใหม่",
                    color = Cy.red, fontSize = 11.sp, modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                )
            }
        }
        items(vm.items, key = { it.url }) { v -> VideoRow(v, onClick = { onPlay(v) }) }
    }
}

// ======================= LIBRARY =======================

@Composable
fun LibraryScreen(onPlay: (VideoItem) -> Unit) {
    LaunchedEffect(Unit) { Store.purgeExpiredHistory() }
    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier.fillMaxWidth().padding(start = 12.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("// ประวัติการดู (เก็บ 7 วัน)", color = Cy.green, style = MaterialTheme.typography.labelMedium, modifier = Modifier.weight(1f))
            if (Store.history.isNotEmpty()) {
                IconButton(onClick = { Store.clearHistory() }) {
                    Icon(Icons.Filled.DeleteSweep, "ล้างประวัติ", tint = Cy.dim)
                }
            }
        }
        if (Store.history.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("// ยังไม่มีรายการ", color = Cy.dim)
            }
        } else {
            LazyColumn(Modifier.fillMaxSize()) {
                items(Store.history.toList(), key = { it.url }) { v -> VideoRow(v, onClick = { onPlay(v) }) }
            }
        }
    }
}

// ======================= CHANNEL =======================

@Composable
fun ChannelScreen(url: String, onBack: () -> Unit, onPlay: (VideoItem) -> Unit) {
    var data by remember(url) { mutableStateOf<ChannelData?>(null) }
    var items by remember(url) { mutableStateOf<List<VideoItem>>(emptyList()) }
    var next by remember(url) { mutableStateOf<Page?>(null) }
    var error by remember(url) { mutableStateOf<String?>(null) }
    var loadingMore by remember(url) { mutableStateOf(false) }
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    val listState = rememberLazyListState()

    LaunchedEffect(url) {
        try {
            val d = YtRepo.channel(url)
            data = d; items = d.items; next = d.next
        } catch (e: Exception) {
            error = e.message ?: e.javaClass.simpleName
        }
    }

    fun more() {
        val d = data ?: return
        val tab = d.tab ?: return
        val n = next ?: return
        if (loadingMore) return
        loadingMore = true
        scope.launch {
            try {
                val p = YtRepo.channelMore(tab, n)
                val known = items.map { it.url }.toSet()
                items = items + p.items.filter { it.url !in known }
                next = p.next
            } catch (e: Exception) {
                next = null
            } finally {
                loadingMore = false
            }
        }
    }

    LaunchedEffect(listState, items.size) {
        snapshotFlow { listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0 }
            .collect { last -> if (items.isNotEmpty() && last >= items.size - 1) more() }
    }

    Column(Modifier.fillMaxSize().background(Cy.bg)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, "back", tint = Cy.green)
            }
            Text("// ช่อง", color = Cy.green, style = MaterialTheme.typography.labelMedium)
        }
        val d = data
        when {
            error != null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("เกิดข้อผิดพลาด: $error", color = Cy.red, modifier = Modifier.padding(16.dp))
            }
            d == null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = Cy.green)
            }
            else -> LazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
                item {
                    Row(
                        Modifier.fillMaxWidth().padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Avatar(d.avatar, 64)
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(d.name, color = Cy.text, style = MaterialTheme.typography.titleMedium, maxLines = 2)
                            if (d.subscribers >= 0) {
                                Text("ผู้ติดตาม ${fmtCount(d.subscribers)} คน", color = Cy.dim, fontSize = 12.sp)
                            }
                        }
                        SubscribeButton(d.url, d.name, d.avatar)
                    }
                }
                items(items, key = { it.url }) { v -> VideoRow(v, onClick = { onPlay(v) }) }
                if (loadingMore) {
                    item {
                        Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(color = Cy.green)
                        }
                    }
                }
            }
        }
    }
}
