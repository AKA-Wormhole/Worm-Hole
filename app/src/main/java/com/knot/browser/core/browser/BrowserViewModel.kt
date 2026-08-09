package com.knot.browser.core.browser

import android.app.Application
import android.graphics.Bitmap
import android.net.Uri
import android.util.Patterns
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.knot.browser.core.ai.GeminiClient
import com.knot.browser.core.ai.GeminiResult
import com.knot.browser.core.ai.TranslateLanguage
import com.knot.browser.core.library.LibraryEntry
import com.knot.browser.core.library.LibraryRepository
import com.knot.browser.core.settings.SearchEngine
import com.knot.browser.core.settings.SettingsRepository
import com.knot.browser.core.settings.ThemeMode
import com.knot.browser.core.webview.TabThumbnailCache
import com.knot.browser.core.webview.WebViewCallbacks
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID

/** State for the Assistant (summarize) and Translate sheets. Shared
 * shape since both are "send page text to Gemini, show the result" --
 * only the prompt differs, so one state model covers both rather than
 * duplicating loading/error/result plumbing twice. */
sealed interface AiRequestState {
    data object Idle : AiRequestState
    data object Loading : AiRequestState
    data class Success(val text: String) : AiRequestState
    data class Error(val message: String) : AiRequestState
}

data class BrowserUiState(
    val tabs: List<Tab> = emptyList(),
    val activeTabId: String? = null,
    val recentlyClosedTabs: List<Tab> = emptyList(),
    val spaces: List<Space> = Space.defaultSpaces(),
    val activeSpaceId: String = Space.DEFAULT_SPACE_ID,
) {
    val activeTab: Tab?
        get() = tabs.firstOrNull { it.id == activeTabId }

    val activeSpace: Space?
        get() = spaces.firstOrNull { it.id == activeSpaceId }

    /** Tabs belonging to the currently active Space, in sidebar order --
     * the sidebar/tab list should always read through this rather than
     * filtering [tabs] itself. */
    val visibleTabs: List<Tab>
        get() = tabs.filter { it.spaceId == activeSpaceId }.sortedBy { it.sortOrder }
}

/** One-shot events the UI should react to but that aren't part of steady
 * state (external intents, error toasts, download prompts). */
sealed interface BrowserEvent {
    data class LaunchExternalApp(val uri: Uri) : BrowserEvent
    data class DownloadRequested(
        val url: String,
        val userAgent: String,
        val mimeType: String,
        val contentDisposition: String,
        val contentLength: Long,
    ) : BrowserEvent
    data class LoadError(val tabId: String, val message: String) : BrowserEvent
}

class BrowserViewModel(application: Application) : AndroidViewModel(application), WebViewCallbacks {

    private val settingsRepository = SettingsRepository(application)
    private val geminiClient = GeminiClient()
    private val libraryRepository = LibraryRepository(application)

    val bookmarks = libraryRepository.bookmarks.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val history = libraryRepository.history.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val searchEngine: StateFlow<SearchEngine> = settingsRepository.searchEngine
        .stateIn(viewModelScope, SharingStarted.Eagerly, SearchEngine.DEFAULT)

    val themeMode: StateFlow<ThemeMode> = settingsRepository.themeMode
        .stateIn(viewModelScope, SharingStarted.Eagerly, ThemeMode.DEFAULT)

    val geminiApiKey: StateFlow<String> = settingsRepository.geminiApiKey
        .stateIn(viewModelScope, SharingStarted.Eagerly, "")

    private val _assistantState = MutableStateFlow<AiRequestState>(AiRequestState.Idle)
    val assistantState: StateFlow<AiRequestState> = _assistantState.asStateFlow()

    private val _translateState = MutableStateFlow<AiRequestState>(AiRequestState.Idle)
    val translateState: StateFlow<AiRequestState> = _translateState.asStateFlow()

    private val _uiState = MutableStateFlow(BrowserUiState())
    val uiState: StateFlow<BrowserUiState> = _uiState.asStateFlow()

    private val _events = MutableSharedFlow<BrowserEvent>(extraBufferCapacity = 8)
    val events: SharedFlow<BrowserEvent> = _events

    init {
        newTab(activate = true)
    }

    fun setSearchEngine(engine: SearchEngine) {
        viewModelScope.launch { settingsRepository.setSearchEngine(engine) }
    }

    fun setThemeMode(mode: ThemeMode) {
        viewModelScope.launch { settingsRepository.setThemeMode(mode) }
    }

    fun setGeminiApiKey(key: String) {
        viewModelScope.launch { settingsRepository.setGeminiApiKey(key) }
    }

    fun addBookmark(tab: Tab) {
        if (tab.url.isBlank()) return
        viewModelScope.launch {
            libraryRepository.addBookmark(LibraryEntry(tab.title.ifBlank { tab.url }, tab.url, System.currentTimeMillis()))
        }
    }

    fun removeBookmark(url: String) {
        viewModelScope.launch { libraryRepository.removeBookmark(url) }
    }

    fun clearHistory() {
        viewModelScope.launch { libraryRepository.clearHistory() }
    }

    // --- Assistant / Translate (Gemini) ---------------------------------
    // Both take already-extracted page text as a parameter rather than
    // reaching for a WebView themselves: the ViewModel doesn't hold
    // platform view references (see WebViewPool's own note on this), so
    // the Composable that has the live WebView extracts the text first.

    fun summarizePage(pageText: String) {
        if (pageText.isBlank()) {
            _assistantState.value = AiRequestState.Error("There's no page content to summarize yet.")
            return
        }
        _assistantState.value = AiRequestState.Loading
        viewModelScope.launch {
            val prompt = """
                Summarize the following web page content for someone who hasn't read it.
                Use short paragraphs or bullet points, cover the main points, and skip
                navigation/menu text that isn't part of the actual content.

                PAGE CONTENT:
                $pageText
            """.trimIndent()
            _assistantState.value = geminiClient.generateText(geminiApiKey.value, prompt).toRequestState()
        }
    }

    fun setAssistantLoading() {
        _assistantState.value = AiRequestState.Loading
    }

    fun resetAssistantState() {
        _assistantState.value = AiRequestState.Idle
    }

    fun translatePage(pageText: String, targetLanguage: TranslateLanguage) {
        if (pageText.isBlank()) {
            _translateState.value = AiRequestState.Error("There's no page content to translate yet.")
            return
        }
        _translateState.value = AiRequestState.Loading
        viewModelScope.launch {
            val prompt = """
                Translate the following web page content into ${targetLanguage.displayName}.
                Preserve the original meaning and tone. Return only the translation, with no
                preamble, notes, or explanation.

                PAGE CONTENT:
                $pageText
            """.trimIndent()
            _translateState.value = geminiClient.generateText(geminiApiKey.value, prompt).toRequestState()
        }
    }

    fun setTranslateLoading() {
        _translateState.value = AiRequestState.Loading
    }

    fun resetTranslateState() {
        _translateState.value = AiRequestState.Idle
    }

    private fun GeminiResult.toRequestState(): AiRequestState = when (this) {
        is GeminiResult.Success -> AiRequestState.Success(text)
        is GeminiResult.Failure.MissingApiKey ->
            AiRequestState.Error("Add a Gemini API key in Settings to use this feature.")
        is GeminiResult.Failure.NoContent ->
            AiRequestState.Error("Gemini didn't return a result. Try again.")
        is GeminiResult.Failure.Http ->
            AiRequestState.Error("Gemini error ($code): $message")
        is GeminiResult.Failure.Network ->
            AiRequestState.Error("Network error: $message")
    }

    // --- Tab management -----------------------------------------------

    fun newTab(url: String? = null, activate: Boolean = true, spaceId: String = Space.DEFAULT_SPACE_ID): Tab {
        val currentMaxOrder = _uiState.value.tabs
            .filter { it.spaceId == spaceId }
            .maxOfOrNull { it.sortOrder } ?: -1
        val tab = Tab(
            url = url.orEmpty(),
            displayUrl = url.orEmpty(),
            isBlankTab = url.isNullOrBlank(),
            spaceId = spaceId,
            sortOrder = currentMaxOrder + 1,
        )
        _uiState.update { state ->
            state.copy(
                tabs = state.tabs + tab,
                activeTabId = if (activate) tab.id else state.activeTabId,
            )
        }
        return tab
    }

    fun closeTab(tabId: String) {
        TabThumbnailCache.remove(tabId)
        _uiState.update { state ->
            val remaining = state.tabs.filterNot { it.id == tabId }
            val newActiveId = when {
                state.activeTabId != tabId -> state.activeTabId
                remaining.isEmpty() -> null
                else -> {
                    val closedIndex = state.tabs.indexOfFirst { it.id == tabId }
                    val fallbackIndex = closedIndex.coerceAtMost(remaining.size - 1)
                    remaining[fallbackIndex].id
                }
            }
            state.copy(
                tabs = remaining,
                activeTabId = newActiveId,
                recentlyClosedTabs = listOfNotNull(state.tabs.firstOrNull { it.id == tabId }) + state.recentlyClosedTabs.take(9),
            )
        }
        if (_uiState.value.tabs.isEmpty()) {
            newTab(activate = true)
        }
    }


    fun reopenClosedTab() {
        val closed = _uiState.value.recentlyClosedTabs.firstOrNull() ?: return
        val newTab = closed.copy(
            id = UUID.randomUUID().toString(),
            createdAtMillis = System.currentTimeMillis(),
            isLoading = false,
            loadProgress = 0f,
        )
        _uiState.update { state ->
            val maxOrder = state.tabs.filter { it.spaceId == newTab.spaceId }.maxOfOrNull { it.sortOrder } ?: -1
            state.copy(
                tabs = state.tabs + newTab.copy(sortOrder = maxOrder + 1),
                activeTabId = newTab.id,
                recentlyClosedTabs = state.recentlyClosedTabs.drop(1),
            )
        }
    }

    fun duplicateTab(tab: Tab) {
        val maxOrder = _uiState.value.tabs.filter { it.spaceId == tab.spaceId }.maxOfOrNull { it.sortOrder } ?: -1
        val copy = tab.copy(
            id = UUID.randomUUID().toString(),
            createdAtMillis = System.currentTimeMillis(),
            sortOrder = maxOrder + 1,
            isLoading = false,
            loadProgress = 0f,
        )
        _uiState.update { state -> state.copy(tabs = state.tabs + copy, activeTabId = copy.id) }
    }

    fun selectTab(tabId: String) {
        _uiState.update { state ->
            if (state.tabs.any { it.id == tabId }) state.copy(activeTabId = tabId) else state
        }
    }

    fun closeAllTabsInSpace(spaceId: String) {
        _uiState.value.tabs.filter { it.spaceId == spaceId }.forEach { TabThumbnailCache.remove(it.id) }
        _uiState.update { state ->
            val remaining = state.tabs.filterNot { it.spaceId == spaceId }
            val newActiveId = if (state.tabs.firstOrNull { it.id == state.activeTabId }?.spaceId == spaceId) {
                remaining.firstOrNull()?.id
            } else {
                state.activeTabId
            }
            state.copy(tabs = remaining, activeTabId = newActiveId)
        }
    }

    // --- Space management -----------------------------------------------

    fun switchSpace(spaceId: String) {
        _uiState.update { state ->
            if (state.spaces.none { it.id == spaceId }) return@update state
            val newActiveTabId = state.tabs
                .filter { it.spaceId == spaceId }
                .minByOrNull { it.sortOrder }
                ?.id
            state.copy(activeSpaceId = spaceId, activeTabId = newActiveTabId)
        }
        // A Space with no tabs yet should still land somewhere useful --
        // open a blank tab in it rather than showing an empty content area.
        if (_uiState.value.tabs.none { it.spaceId == spaceId }) {
            newTab(activate = true, spaceId = spaceId)
        }
    }

    fun createSpace(name: String, accent: SpaceAccent): Space {
        val newOrder = (_uiState.value.spaces.maxOfOrNull { it.order } ?: -1) + 1
        val space = Space(
            id = UUID.randomUUID().toString(),
            name = name,
            accent = accent,
            order = newOrder,
        )
        _uiState.update { state -> state.copy(spaces = state.spaces + space) }
        switchSpace(space.id)
        return space
    }

    /** Applies a new sidebar order for tabs within the currently active
     * Space -- called after a drag-reorder gesture completes with the
     * final list order. */
    fun reorderTabsInActiveSpace(orderedTabIds: List<String>) {
        _uiState.update { state ->
            val orderById = orderedTabIds.withIndex().associate { (index, id) -> id to index }
            state.copy(
                tabs = state.tabs.map { tab ->
                    val newOrder = orderById[tab.id] ?: return@map tab
                    tab.copy(sortOrder = newOrder)
                },
            )
        }
    }

    // --- Navigation intents (UI calls these; actual WebView.loadUrl
    // happens in the Composable that owns the WebView instance, since
    // the ViewModel doesn't hold platform view references) -------------

    /** Normalizes what the user typed into either a URL or a search query
     * pointed at the currently selected search engine. */
    fun resolveInput(input: String): String {
        val trimmed = input.trim()
        val looksLikeUrl = Patterns.WEB_URL.matcher(trimmed).matches() && !trimmed.contains(" ")
        return when {
            trimmed.startsWith("http://") || trimmed.startsWith("https://") -> trimmed
            looksLikeUrl -> "https://$trimmed"
            else -> searchEngine.value.buildQueryUrl(trimmed)
        }
    }

    fun updateTabUrl(tabId: String, url: String) {
        _uiState.update { state ->
            state.copy(tabs = state.tabs.map {
                if (it.id == tabId) it.copy(url = url, displayUrl = url, isBlankTab = false) else it
            })
        }
    }

    // --- WebViewCallbacks implementation --------------------------------
    // Called from the main thread by WebViewClient/WebChromeClient in
    // practice, so plain StateFlow.update {} is safe here.

    override fun onPageStarted(tabId: String, url: String) {
        updateTab(tabId) { it.copy(url = url, displayUrl = url, isLoading = true, isSecure = url.startsWith("https://")) }
    }

    override fun onPageFinished(tabId: String, url: String) {
        updateTab(tabId) { it.copy(url = url, displayUrl = url, isLoading = false, loadProgress = 1f) }
        val tab = _uiState.value.tabs.firstOrNull { it.id == tabId }
        if (tab != null && url.isNotBlank() && (url.startsWith("http://") || url.startsWith("https://"))) {
            viewModelScope.launch {
                libraryRepository.addHistory(LibraryEntry(tab.title.ifBlank { url }, url, System.currentTimeMillis()))
            }
        }
    }

    override fun onProgressChanged(tabId: String, progress: Int) {
        updateTab(tabId) { it.copy(loadProgress = progress / 100f, isLoading = progress in 1..99) }
    }

    override fun onTitleChanged(tabId: String, title: String) {
        updateTab(tabId) { current -> current.copy(title = title.ifBlank { current.displayUrl }) }
    }

    override fun onFaviconChanged(tabId: String, favicon: Bitmap?) {
        // Bitmap itself isn't stored in Tab (keeps state cheap); a favicon
        // cache keyed by tabId is a natural Stage 3 addition once the
        // sidebar needs to actually paint these.
    }

    override fun onNavigationStateChanged(tabId: String, canGoBack: Boolean, canGoForward: Boolean) {
        updateTab(tabId) { it.copy(canGoBack = canGoBack, canGoForward = canGoForward) }
    }

    override fun shouldOverrideUrl(tabId: String, url: String): Boolean {
        val uri = Uri.parse(url)
        val scheme = uri.scheme?.lowercase()
        if (scheme != null && scheme != "http" && scheme != "https") {
            viewModelScope.launch { _events.emit(BrowserEvent.LaunchExternalApp(uri)) }
            return true
        }
        return false
    }

    override fun onReceivedError(tabId: String, errorDescription: String, isMainFrame: Boolean) {
        if (isMainFrame) {
            viewModelScope.launch { _events.emit(BrowserEvent.LoadError(tabId, errorDescription)) }
        }
    }

    override fun onDownloadRequested(
        tabId: String,
        url: String,
        userAgent: String,
        mimeType: String,
        contentDisposition: String,
        contentLength: Long,
    ) {
        viewModelScope.launch {
            _events.emit(BrowserEvent.DownloadRequested(url, userAgent, mimeType, contentDisposition, contentLength))
        }
    }

    private inline fun updateTab(tabId: String, transform: (Tab) -> Tab) {
        _uiState.update { state ->
            state.copy(tabs = state.tabs.map { if (it.id == tabId) transform(it) else it })
        }
    }

    override fun onCleared() {
        super.onCleared()
        // WebViewPool.destroyAll() is invoked by the Composable/Activity
        // that owns the pool instance, not here -- ViewModel doesn't hold
        // a pool reference to keep platform views out of this layer.
    }
}
