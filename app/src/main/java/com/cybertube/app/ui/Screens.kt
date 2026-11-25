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
import com.cybertube.app.core.Store
import com.cybertube.app.core.VideoItem
import com.cybertube.app.core.YtRepo
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
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
            placeholder = { Text("search://youtube", color = Cy.dim) },
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
        } else if (vm.results.isEmpty() && !vm.loading) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("CYBERTUBE", color = Cy.green, fontSize = 28.sp, style = MaterialTheme.typography.titleLarge)
                    Text(
                        vm.error?.let { "ERR: $it" } ?: "// ready. type a query.",
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

// ======================= SUBSCRIPTIONS =======================

class FeedVM : ViewModel() {
    var items by mutableStateOf<List<VideoItem>>(emptyList())
    var loading by mutableStateOf(false)
    private var job: Job? = null

    fun refresh() {
        val subs = Store.subs.toList().take(20)
        job?.cancel()
        if (subs.isEmpty()) {
            items = emptyList(); loading = false; return
        }
        job = viewModelScope.launch {
            loading = true
            val lists = subs.map { s ->
                async { runCatching { YtRepo.channel(s.url).items.take(6) }.getOrDefault(emptyList()) }
            }.awaitAll()
            // round-robin merge so every channel shows up near the top
            val out = mutableListOf<VideoItem>()
            var i = 0
            while (true) {
                var added = false
                for (l in lists) if (i < l.size) { out += l[i]; added = true }
                if (!added) break
                i++
            }
            items = out.distinctBy { it.url }
            loading = false
        }
    }
}

@Composable
fun SubsScreen(onPlay: (VideoItem) -> Unit, onChannel: (String) -> Unit) {
    val vm: FeedVM = viewModel()
    LaunchedEffect(Store.subs.size) { vm.refresh() }

    if (Store.subs.isEmpty()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(
                "// no subscriptions yet.\nopen a video → SUBSCRIBE.",
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
        item { SectionLabel("// LATEST") }
        if (vm.loading && vm.items.isEmpty()) {
            item {
                Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = Cy.green)
                }
            }
        }
        items(vm.items, key = { it.url }) { v -> VideoRow(v, onClick = { onPlay(v) }) }
    }
}

// ======================= LIBRARY =======================

@Composable
fun LibraryScreen(onPlay: (VideoItem) -> Unit) {
    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier.fillMaxWidth().padding(start = 12.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("// HISTORY", color = Cy.green, style = MaterialTheme.typography.labelMedium, modifier = Modifier.weight(1f))
            if (Store.history.isNotEmpty()) {
                IconButton(onClick = { Store.clearHistory() }) {
                    Icon(Icons.Filled.DeleteSweep, "clear", tint = Cy.dim)
                }
            }
        }
        if (Store.history.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("// empty", color = Cy.dim)
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
            Text("// CHANNEL", color = Cy.green, style = MaterialTheme.typography.labelMedium)
        }
        val d = data
        when {
            error != null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("ERR: $error", color = Cy.red, modifier = Modifier.padding(16.dp))
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
                                Text("${fmtCount(d.subscribers)} subscribers", color = Cy.dim, fontSize = 12.sp)
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
