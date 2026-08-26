package com.wormhole.browser.core.browser

import android.app.Application
import android.graphics.Bitmap
import android.net.Uri
import android.util.Patterns
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.wormhole.browser.core.ai.GeminiClient
import com.wormhole.browser.core.library.LibraryEntry
import com.wormhole.browser.core.library.LibraryRepository
import com.wormhole.browser.core.library.SearchQuerySuggestion
import com.wormhole.browser.core.library.ShortcutEntry
import com.wormhole.browser.core.search.SearchSuggestionsClient
import com.wormhole.browser.core.settings.SearchEngine
import com.wormhole.browser.core.settings.SettingsRepository
import com.wormhole.browser.core.settings.ThemeMode
import com.wormhole.browser.core.webview.TabThumbnailCache
import com.wormhole.browser.core.gecko.EngineCallbacks
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import com.wormhole.browser.core.browser.session.BrowserSessionStore
import com.wormhole.browser.core.browser.session.PersistedSession
import com.wormhole.browser.core.browser.session.PersistedTab
import com.wormhole.browser.core.browser.session.PersistedSpace
import java.util.UUID

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

    val visibleTabs: List<Tab>
        get() = tabs.filter { it.spaceId == activeSpaceId }.sortedBy { it.sortOrder }
}

sealed interface BrowserEvent {
    data class LaunchExternalApp(val uri: Uri) : BrowserEvent
    data class DownloadRequested(
        val url: String,
        val userAgent: String,
        val mimeType: String,
        val contentDisposition: String,
        val contentLength: Long,
    ) : BrowserEvent
    data class LoadError(
        val tabId: String,
        val message: String,
        val url: String = "",
        val title: String = "Connection failed",
        val hint: String = "Check your internet connection or DNS, then try again.",
    ) : BrowserEvent
    data class RendererCrashed(val tabId: String) : BrowserEvent

    data class SslErrorOccurred(
        val tabId: String,
        val url: String,
        val primaryErrorCode: Int,
        val onProceed: () -> Unit,
        val onCancel: () -> Unit,
    ) : BrowserEvent

    data class MediaPermissionRequested(
        val tabId: String,
        val origin: String,
        val resources: List<String>,
        val onGrant: (List<String>) -> Unit,
        val onDeny: () -> Unit,
    ) : BrowserEvent

    data class GeolocationPermissionRequested(
        val tabId: String,
        val origin: String,
        val onAllow: (retain: Boolean) -> Unit,
        val onDeny: () -> Unit,
    ) : BrowserEvent

    data class BlobDownloadReady(val base64Data: String, val mimeType: String, val fileName: String) : BrowserEvent
    data class BlobDownloadFailed(val message: String) : BrowserEvent
}

class BrowserViewModel(application: Application) : AndroidViewModel(application), EngineCallbacks {

    private val settingsRepository = SettingsRepository(application)
    private val geminiClient = GeminiClient()
    private val sessionStore = BrowserSessionStore(application)
    private val libraryRepository = LibraryRepository(application)
    private val searchSuggestionsClient = SearchSuggestionsClient()

    val bookmarks = libraryRepository.bookmarks.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val history = libraryRepository.history.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val shortcuts = libraryRepository.shortcuts.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val recentSearches = libraryRepository.recentSearches.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val hasStoredRecentSearches = libraryRepository.hasStoredRecentSearches.stateIn(viewModelScope, SharingStarted.Eagerly, false)

    suspend fun searchOmnibox(query: String) = libraryRepository.searchOmnibox(query)

    suspend fun fetchSearchSuggestions(query: String): List<SearchQuerySuggestion> =
        searchSuggestionsClient.suggestionsFor(query, limit = 8).map { SearchQuerySuggestion(it) }

    val searchEngine: StateFlow<SearchEngine> = settingsRepository.searchEngine
        .stateIn(viewModelScope, SharingStarted.Eagerly, SearchEngine.DEFAULT)

    val themeMode: StateFlow<ThemeMode> = settingsRepository.themeMode
        .stateIn(viewModelScope, SharingStarted.Eagerly, ThemeMode.DEFAULT)

    val geminiApiKey: StateFlow<String> = settingsRepository.geminiApiKey
        .stateIn(viewModelScope, SharingStarted.Eagerly, "")

    val onboardingCompleted: StateFlow<Boolean?> = settingsRepository.onboardingCompleted
        .map { completed -> completed }
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    val dynamicColorEnabled: StateFlow<Boolean> = settingsRepository.dynamicColorEnabled
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    val dynamicBackgroundEnabled: StateFlow<Boolean> = settingsRepository.dynamicBackgroundEnabled
        .stateIn(viewModelScope, SharingStarted.Eagerly, true)

    val trackerBlockingEnabled: StateFlow<Boolean> = settingsRepository.trackerBlockingEnabled
        .stateIn(viewModelScope, SharingStarted.Eagerly, true)

    val adBlockingEnabled: StateFlow<Boolean> = settingsRepository.adBlockingEnabled
        .stateIn(viewModelScope, SharingStarted.Eagerly, true)

    val popupBlockingEnabled: StateFlow<Boolean> = settingsRepository.popupBlockingEnabled
        .stateIn(viewModelScope, SharingStarted.Eagerly, true)

    val homepageUrl: StateFlow<String> = settingsRepository.homepageUrl
        .stateIn(viewModelScope, SharingStarted.Eagerly, "")

    val webDarkModeEnabled: StateFlow<Boolean> = settingsRepository.webDarkModeEnabled
        .stateIn(viewModelScope, SharingStarted.Eagerly, true)

    private val _assistantState = MutableStateFlow<AiRequestState>(AiRequestState.Idle)
    val assistantState: StateFlow<AiRequestState> = _assistantState.asStateFlow()


    private val _aiAnswerState = MutableStateFlow<AiRequestState>(AiRequestState.Idle)
    val aiAnswerState: StateFlow<AiRequestState> = _aiAnswerState.asStateFlow()

    private val _uiState = MutableStateFlow(BrowserUiState())
    val uiState: StateFlow<BrowserUiState> = _uiState.asStateFlow()

    private val _events = MutableSharedFlow<BrowserEvent>(extraBufferCapacity = 8)
    val events: SharedFlow<BrowserEvent> = _events

    init {

        viewModelScope.launch {
            kotlinx.coroutines.flow.combine(
                trackerBlockingEnabled, adBlockingEnabled, popupBlockingEnabled,
            ) { trackers, ads, popups -> Triple(trackers, ads, popups) }.collect { (trackers, ads, popups) ->
                applyContentBlockerSettings(trackers, ads, popups)
            }
        }
        viewModelScope.launch {
            webDarkModeEnabled.collect { enabled ->
                // Dark mode applied via Gecko CSS injection in BrowserScreen
            }
        }
        viewModelScope.launch {
            val restored = sessionStore.load()
            if (restored?.tabs?.isNotEmpty() == true) {
                val tabs = restored.tabs.map { persisted ->
                    Tab(
                        id = persisted.id,
                        title = persisted.title,
                        url = persisted.url,
                        displayUrl = persisted.displayUrl,
                        faviconUrl = persisted.faviconUrl,
                        spaceId = persisted.spaceId,
                        createdAtMillis = persisted.createdAtMillis,
                        isBlankTab = persisted.isBlankTab,
                        sortOrder = persisted.sortOrder,
                        isIncognito = false,
                    )
                }
                _uiState.value = BrowserUiState(
                    tabs = tabs,
                    activeTabId = restored.activeTabId?.takeIf { id -> tabs.any { it.id == id } } ?: tabs.first().id,
                    activeSpaceId = restored.activeSpaceId.takeIf { id -> restored.spaces.any { it.id == id } } ?: Space.DEFAULT_SPACE_ID,
                    spaces = restored.spaces.mapNotNull { persisted ->
                        val accent = runCatching { SpaceAccent.valueOf(persisted.accent) }.getOrNull() ?: return@mapNotNull null
                        Space(persisted.id, persisted.name, accent, persisted.order)
                    }.ifEmpty { Space.defaultSpaces() },
                )
            } else {
                newTab(activate = true)
            }
        }
    }

    private fun persistSession() {
        val state = _uiState.value
        viewModelScope.launch {
            sessionStore.save(
                PersistedSession(
                    tabs = state.tabs.filterNot { it.isIncognito }.map { tab ->
                        PersistedTab(tab.id, tab.title, tab.url, tab.displayUrl, tab.faviconUrl, tab.spaceId, tab.createdAtMillis, tab.isBlankTab, tab.sortOrder, false)
                    },
                    activeTabId = state.activeTabId,
                    activeSpaceId = state.activeSpaceId,
                    spaces = state.spaces.map { PersistedSpace(it.id, it.name, it.accent.name, it.order) },
                ),
            )
        }
    }

    fun setSearchEngine(engine: SearchEngine) {
        viewModelScope.launch { settingsRepository.setSearchEngine(engine) }
    }

    fun setThemeMode(mode: ThemeMode) {
        viewModelScope.launch { settingsRepository.setThemeMode(mode) }
    }

    fun setHomepageUrl(url: String) {
        viewModelScope.launch { settingsRepository.setHomepageUrl(url) }
    }

    fun setGeminiApiKey(key: String) {
        viewModelScope.launch { settingsRepository.setGeminiApiKey(key) }
    }

    fun setOnboardingCompleted(completed: Boolean) {
        viewModelScope.launch { settingsRepository.setOnboardingCompleted(completed) }
    }

    fun setDynamicColorEnabled(enabled: Boolean) {
        viewModelScope.launch { settingsRepository.setDynamicColorEnabled(enabled) }
    }

    fun setDynamicBackgroundEnabled(enabled: Boolean) {
        viewModelScope.launch { settingsRepository.setDynamicBackgroundEnabled(enabled) }
    }

    fun setTrackerBlockingEnabled(enabled: Boolean) {
        viewModelScope.launch { settingsRepository.setTrackerBlockingEnabled(enabled) }
        applyContentBlockerSettings(trackers = enabled, ads = adBlockingEnabled.value, popups = popupBlockingEnabled.value)
    }

    fun setAdBlockingEnabled(enabled: Boolean) {
        viewModelScope.launch { settingsRepository.setAdBlockingEnabled(enabled) }
        applyContentBlockerSettings(trackers = trackerBlockingEnabled.value, ads = enabled, popups = popupBlockingEnabled.value)
    }

    fun setPopupBlockingEnabled(enabled: Boolean) {
        viewModelScope.launch { settingsRepository.setPopupBlockingEnabled(enabled) }
        applyContentBlockerSettings(trackers = trackerBlockingEnabled.value, ads = adBlockingEnabled.value, popups = enabled)
    }

    private fun applyContentBlockerSettings(trackers: Boolean, ads: Boolean, popups: Boolean) {
        // Popup blocking is enforced per-session in WormHoleGeckoViewHost's
        // PromptDelegate.onPopupRequest, driven by this same popupBlockingEnabled
        // flow, so there's nothing to push into the Gecko runtime for it here.
        com.wormhole.browser.core.gecko.GeckoRuntimeHolder.setContentBlocking(
            trackerBlocking = trackers,
            adBlocking = ads,
        )
    }

    fun setWebDarkModeEnabled(enabled: Boolean) {
        viewModelScope.launch { settingsRepository.setWebDarkModeEnabled(enabled) }
    }

    fun openExternalUrl(url: String) {
        newTab(url = url, activate = true)
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

    fun addShortcut(title: String, url: String) {
        if (url.isBlank()) return
        viewModelScope.launch {
            libraryRepository.addShortcut(ShortcutEntry(title.ifBlank { url }, url, System.currentTimeMillis()))
        }
    }

    fun removeShortcut(url: String) {
        viewModelScope.launch { libraryRepository.removeShortcut(url) }
    }

    fun clearHistory() {
        viewModelScope.launch { libraryRepository.clearHistory() }
    }

    fun clearAllBrowsingData() {
        viewModelScope.launch {
            libraryRepository.clearHistory()
            com.wormhole.browser.core.gecko.GeckoStorage.clearBrowsingData(getApplication())
            getApplication<Application>().cacheDir?.let { cacheDir ->
                cacheDir.listFiles()?.forEach { it.deleteRecursively() }
            }
        }
    }

    fun recordSearchQuery(query: String) {
        viewModelScope.launch { libraryRepository.recordSearchQuery(query) }
    }

    fun recordTypedQueryIfSearch(input: String) {
        val trimmed = input.trim()
        if (trimmed.isBlank()) return
        if (trimmed.startsWith("http://") || trimmed.startsWith("https://")) return
        val looksLikeUrl = Patterns.WEB_URL.matcher(trimmed).matches() && !trimmed.contains(" ")
        if (looksLikeUrl) return
        recordSearchQuery(trimmed)
    }

    fun clearRecentSearches() {
        viewModelScope.launch { libraryRepository.clearRecentSearches() }
    }

    fun deleteHistoryEntry(url: String) {
        viewModelScope.launch { libraryRepository.deleteHistoryEntry(url) }
    }

    fun summarizePage(pageText: String) {
        if (pageText.isBlank()) {
            _assistantState.value = AiRequestState.Error("There's no page content to summarize yet.")
            return
        }
        _assistantState.value = AiRequestState.Loading
        viewModelScope.launch {
            val prompt = """
                Summarize the following web page content for someone who hasn't read it.

                - Focus only on the actual article/content. Ignore and never mention
                  any leftover navigation links, menu items, buttons, footer links,
                  cookie/consent notices, "skip to content" text, or site chrome that
                  may still be present in the extracted text below.
                - Write 2-4 short sentences or a few concise bullet points covering
                  only the main substance -- what the page is about and its key
                  points -- the way Chrome's or Firefox's page summary would.
                - Do not describe the page's UI, layout, or navigation elements.

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


    fun askWormHole(query: String) {
        if (query.isBlank()) {
            _aiAnswerState.value = AiRequestState.Error("Ask something to get an answer.")
            return
        }
        _aiAnswerState.value = AiRequestState.Loading
        viewModelScope.launch {
            val systemInstruction = """
                You answer questions directly and concisely for a mobile browser's AI answer
                screen. Format every response as:
                Line 1: a short title for the answer (no markdown, no quotes around it).
                Then several bullet points, each on its own line, in the exact form
                "Label - one or two sentence description." where Label is 1-3 words.
                Do not use markdown symbols like # or *. Do not add a preamble or closing
                remark. Keep the whole answer under 150 words.
            """.trimIndent()
            _aiAnswerState.value = geminiClient.generateText(geminiApiKey.value, query, systemInstruction).toRequestState()
        }
    }

    fun resetAiAnswerState() {
        _aiAnswerState.value = AiRequestState.Idle
    }

    private fun GeminiClient.Result.toRequestState(): AiRequestState = when (this) {
        is GeminiClient.Result.Success -> AiRequestState.Success(text)
        is GeminiClient.Result.Failure -> AiRequestState.Error(
            if (code != 0) "Gemini error ($code): $message" else message,
        )
    }

    fun newTab(url: String? = null, activate: Boolean = true, spaceId: String = Space.DEFAULT_SPACE_ID, incognito: Boolean = false): Tab {
        val resolvedUrl = when {
            !url.isNullOrBlank() -> url
            incognito -> null
            homepageUrl.value.isNotBlank() -> homepageUrl.value
            else -> null
        }
        val currentMaxOrder = _uiState.value.tabs
            .filter { it.spaceId == spaceId }
            .maxOfOrNull { it.sortOrder } ?: -1
        val tab = Tab(
            url = resolvedUrl.orEmpty(),
            displayUrl = resolvedUrl.orEmpty(),
            isBlankTab = resolvedUrl.isNullOrBlank(),
            spaceId = spaceId,
            sortOrder = currentMaxOrder + 1,
            isIncognito = incognito,
        )
        _uiState.update { state ->
            state.copy(
                tabs = state.tabs + tab,
                activeTabId = if (activate) tab.id else state.activeTabId,
            )
        }
        persistSession()
        return tab
    }

    fun closeTab(tabId: String) {
        TabThumbnailCache.remove(tabId)
        _uiState.update { state ->
            val remaining = state.tabs.filterNot { it.id == tabId }
            // Fallback must stay within the space the closed tab belonged to --
            // picking a tab from another space here would leave activeTabId
            // pointing outside activeSpaceId, so the loaded page and the
            // visible tab strip (which filters by activeSpaceId) would disagree
            // about which tab/space is actually active.
            val closedSpaceId = state.tabs.firstOrNull { it.id == tabId }?.spaceId
            val remainingInSameSpace = remaining.filter { it.spaceId == closedSpaceId }
            val newActiveId = when {
                state.activeTabId != tabId -> state.activeTabId
                remainingInSameSpace.isNotEmpty() -> {
                    val closedIndex = state.tabs
                        .filter { it.spaceId == closedSpaceId }
                        .indexOfFirst { it.id == tabId }
                    val fallbackIndex = closedIndex.coerceIn(0, remainingInSameSpace.size - 1)
                    remainingInSameSpace[fallbackIndex].id
                }
                else -> null
            }
            state.copy(
                tabs = remaining,
                activeTabId = newActiveId,
                recentlyClosedTabs = listOfNotNull(state.tabs.firstOrNull { it.id == tabId }) + state.recentlyClosedTabs.take(9),
            )
        }
        if (_uiState.value.visibleTabs.isEmpty()) {
            newTab(activate = true, spaceId = _uiState.value.activeSpaceId)
        }
        persistSession()
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
        persistSession()
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
        persistSession()
    }

    fun selectTab(tabId: String) {
        _uiState.update { state ->
            if (state.tabs.any { it.id == tabId }) state.copy(activeTabId = tabId) else state
        }
        persistSession()
    }

    fun closeAllTabsInSpace(spaceId: String, incognitoOnly: Boolean? = null) {
        fun matches(tab: Tab) = tab.spaceId == spaceId && (incognitoOnly == null || tab.isIncognito == incognitoOnly)

        _uiState.value.tabs.filter(::matches).forEach {
            TabThumbnailCache.remove(it.id)
        }
        _uiState.update { state ->
            val remaining = state.tabs.filterNot(::matches)
            // Same rule as closeTab: never hand activeTabId to a tab outside
            // activeSpaceId. If the space being cleared wasn't the active one,
            // the active tab is untouched and stays exactly where it was.
            val newActiveId = if (state.activeTabId != null &&
                state.tabs.firstOrNull { it.id == state.activeTabId }?.let(::matches) == true
            ) {
                remaining.firstOrNull { it.spaceId == state.activeSpaceId }?.id
            } else {
                state.activeTabId
            }
            state.copy(tabs = remaining, activeTabId = newActiveId)
        }
        if (_uiState.value.visibleTabs.isEmpty() && _uiState.value.activeSpaceId == spaceId) {
            newTab(activate = true, spaceId = spaceId)
        }
        persistSession()
    }

    fun switchSpace(spaceId: String) {
        _uiState.update { state ->
            if (state.spaces.none { it.id == spaceId }) return@update state
            val newActiveTabId = state.tabs
                .filter { it.spaceId == spaceId }
                .minByOrNull { it.sortOrder }
                ?.id
            state.copy(activeSpaceId = spaceId, activeTabId = newActiveTabId)
        }

        if (_uiState.value.tabs.none { it.spaceId == spaceId }) {
            newTab(activate = true, spaceId = spaceId)
        }
        persistSession()
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
        val blank = url.isBlank() || NavigationUrls.isAboutBlank(url)
        _uiState.update { state ->
            state.copy(tabs = state.tabs.map {
                if (it.id == tabId) {
                    it.copy(
                        url = if (blank) "" else url,
                        displayUrl = if (blank) "" else url,
                        isBlankTab = blank,
                        title = if (blank) "New Tab" else provisionalTitleFor(url),
                    )
                } else it
            })
        }
    }

    fun goHome(tabId: String) {
        _uiState.update { state ->
            state.copy(tabs = state.tabs.map {
                if (it.id == tabId) {
                    it.copy(
                        url = "",
                        displayUrl = "",
                        title = "New Tab",
                        isBlankTab = true,
                        isLoading = false,
                        loadProgress = 0f,
                        isSecure = false,
                        canGoBack = false,
                        canGoForward = false,
                    )
                } else it
            })
        }
        persistSession()
    }

    private fun provisionalTitleFor(url: String): String = try {
        val uri = Uri.parse(url)
        val query = uri.getQueryParameter("q")
        val isSearchUrl = SearchEngine.entries.any { engine ->
            url.startsWith(engine.buildQueryUrl("").substringBefore('?'))
        }
        when {
            !query.isNullOrBlank() && isSearchUrl -> query
            !uri.host.isNullOrBlank() -> uri.host!!.removePrefix("www.")
            else -> url
        }
    } catch (_: Exception) {
        url
    }

    override fun onPageStarted(tabId: String, url: String) {
        if (url.isBlank() || NavigationUrls.isAboutBlank(url)) return
        updateTab(tabId) { current ->
            if (current.isBlankTab) current else current.copy(
                url = url,
                displayUrl = url,
                isLoading = true,
                isBlankTab = false,
                isSecure = url.startsWith("https://"),
            )
        }
    }

    override fun onUrlChanged(tabId: String, url: String) {
        if (url.isBlank() || NavigationUrls.isAboutBlank(url)) return
        updateTab(tabId) { current ->
            if (current.isBlankTab) current
            else if (current.url == url) {
                current.copy(displayUrl = url, isBlankTab = false, isSecure = url.startsWith("https://"))
            } else {
                current.copy(
                    url = url,
                    displayUrl = url,
                    isBlankTab = false,
                    isSecure = url.startsWith("https://"),
                )
            }
        }
    }

    override fun onPageFinished(tabId: String, url: String) {
        val committed = url.takeUnless { it.isBlank() || NavigationUrls.isAboutBlank(it) }
        updateTab(tabId) { current ->
            if (current.isBlankTab) {
                current.copy(isLoading = false, loadProgress = 0f)
            } else {
                val nextUrl = committed ?: current.url
                current.copy(
                    url = nextUrl,
                    displayUrl = nextUrl,
                    isLoading = false,
                    loadProgress = 1f,
                    isBlankTab = nextUrl.isBlank(),
                    isSecure = nextUrl.startsWith("https://"),
                )
            }
        }
        val tab = _uiState.value.tabs.firstOrNull { it.id == tabId }
        if (tab != null && !tab.isIncognito && !committed.isNullOrBlank() &&
            (committed.startsWith("http://") || committed.startsWith("https://"))
        ) {
            viewModelScope.launch {
                libraryRepository.addHistory(LibraryEntry(tab.title.ifBlank { committed }, committed, System.currentTimeMillis()))
            }
        }
        persistSession()
    }

    override fun onProgressChanged(tabId: String, progress: Int) {
        updateTab(tabId) { current ->
            if (current.isBlankTab) current
            else current.copy(loadProgress = progress / 100f, isLoading = progress in 1..99)
        }
    }

    override fun onTitleChanged(tabId: String, title: String) {
        updateTab(tabId) { current ->
            if (current.isBlankTab) current
            else current.copy(title = title.ifBlank { current.displayUrl })
        }
    }

    override fun onFaviconChanged(tabId: String, favicon: Bitmap?) {
        val tab = uiState.value.tabs.find { it.id == tabId } ?: return
        if (favicon == null || tab.url.isBlank()) return
        com.wormhole.browser.core.webview.FaviconCache.put(tab.url, favicon)
    }

    override fun onNavigationStateChanged(tabId: String, canGoBack: Boolean, canGoForward: Boolean) {
        updateTab(tabId) { it.copy(canGoBack = canGoBack, canGoForward = canGoForward) }
    }

    private val nonRenderableDownloadExtensions = setOf(
        "zip", "rar", "7z", "tar", "gz", "apk", "exe", "dmg", "iso",
        "doc", "docx", "xls", "xlsx", "ppt", "pptx",
        "mp3", "mp4", "mov", "avi", "mkv",
    )

    override fun shouldOverrideUrl(tabId: String, url: String): Boolean {
        val uri = Uri.parse(url)
        val scheme = uri.scheme?.lowercase()

        if (scheme == "blob" || scheme == "data") {
            return false
        }
        if (scheme == "http" || scheme == "https") {
            val lastSegment = uri.lastPathSegment?.lowercase().orEmpty()
            val extension = lastSegment.substringAfterLast('.', missingDelimiterValue = "")
            if (extension in nonRenderableDownloadExtensions) {
                val mimeType = android.webkit.MimeTypeMap.getSingleton()
                    .getMimeTypeFromExtension(extension) ?: "application/octet-stream"
                viewModelScope.launch {
                    _events.emit(
                        BrowserEvent.DownloadRequested(
                            url = url,
                            userAgent = "Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36",
                            mimeType = mimeType,
                            contentDisposition = "attachment; filename=\"${uri.lastPathSegment}\"",
                            contentLength = -1L,
                        ),
                    )
                }
                return true
            }
            return false
        }
        if (scheme != null) {
            viewModelScope.launch { _events.emit(BrowserEvent.LaunchExternalApp(uri)) }
            return true
        }
        return false
    }

    override fun onRendererCrashed(tabId: String) {
        viewModelScope.launch { _events.emit(BrowserEvent.RendererCrashed(tabId)) }
    }

    override fun onReceivedError(
        tabId: String,
        errorDescription: String,
        isMainFrame: Boolean,
        failedUrl: String,
        title: String,
        hint: String,
    ) {
        if (isMainFrame) {
            viewModelScope.launch {
                _events.emit(
                    BrowserEvent.LoadError(
                        tabId = tabId,
                        message = errorDescription,
                        url = failedUrl,
                        title = title,
                        hint = hint,
                    ),
                )
            }
        }
    }

    override fun onSslErrorReceived(
        tabId: String,
        url: String,
        primaryErrorCode: Int,
        onProceed: () -> Unit,
        onCancel: () -> Unit,
    ) {
        viewModelScope.launch {
            _events.emit(BrowserEvent.SslErrorOccurred(tabId, url, primaryErrorCode, onProceed, onCancel))
        }
    }

    override fun onMediaPermissionRequested(
        tabId: String,
        origin: String,
        resources: List<String>,
        onGrant: (List<String>) -> Unit,
        onDeny: () -> Unit,
    ) {
        viewModelScope.launch {
            _events.emit(BrowserEvent.MediaPermissionRequested(tabId, origin, resources, onGrant, onDeny))
        }
    }

    override fun onGeolocationPermissionRequested(
        tabId: String,
        origin: String,
        onAllow: (retain: Boolean) -> Unit,
        onDeny: () -> Unit,
    ) {
        viewModelScope.launch {
            _events.emit(BrowserEvent.GeolocationPermissionRequested(tabId, origin, onAllow, onDeny))
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

    override fun onNewWindowRequested(tabId: String, url: String): String? {
        val currentTab = _uiState.value.tabs.firstOrNull { it.id == tabId }
        val created = newTab(
            url = url.takeUnless { it.isBlank() || NavigationUrls.isAboutBlank(it) },
            spaceId = currentTab?.spaceId ?: _uiState.value.activeSpaceId,
            incognito = currentTab?.isIncognito == true,
        )
        return created.id
    }

    override fun onBlobDownloadReady(tabId: String, base64Data: String, mimeType: String, fileName: String) {
        viewModelScope.launch {
            _events.emit(BrowserEvent.BlobDownloadReady(base64Data, mimeType, fileName))
        }
    }

    override fun onBlobDownloadFailed(tabId: String, error: String) {
        viewModelScope.launch {
            _events.emit(BrowserEvent.BlobDownloadFailed(error))
        }
    }

    private inline fun updateTab(tabId: String, transform: (Tab) -> Tab) {
        _uiState.update { state ->
            state.copy(tabs = state.tabs.map { if (it.id == tabId) transform(it) else it })
        }
    }

    override fun onCleared() {
        super.onCleared()

    }
}
