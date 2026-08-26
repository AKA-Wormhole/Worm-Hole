package com.wormhole.browser.core.gecko

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.mozilla.geckoview.GeckoSession

class GeckoFindController(private val session: GeckoSession) : FindController {
    override var query: String by mutableStateOf("")
        private set
    override var activeMatchIndex: Int by mutableStateOf(0)
        private set
    override var totalMatches: Int by mutableStateOf(0)
        private set
    override var isActive: Boolean by mutableStateOf(false)
        private set

    override fun start() {
        isActive = true
    }

    override fun search(text: String) {
        query = text
        if (text.isEmpty()) {
            clear()
            totalMatches = 0
            activeMatchIndex = 0
            return
        }
        runFind(text, 0)
    }

    override fun findNext() {
        if (query.isEmpty()) return
        runFind(query, FIND_FORWARD)
    }

    override fun findPrevious() {
        if (query.isEmpty()) return
        runFind(query, FIND_BACKWARDS)
    }

    override fun stop() {
        isActive = false
        query = ""
        totalMatches = 0
        activeMatchIndex = 0
        clear()
    }

    private fun runFind(text: String, flags: Int) {
        try {
            session.finder.find(text, flags).accept { result ->
                if (result == null) return@accept
                val found = readBool(result, "found") ?: true
                if (!found) {
                    totalMatches = 0
                    activeMatchIndex = 0
                    return@accept
                }
                activeMatchIndex = readInt(result, "current") ?: 0
                totalMatches = readInt(result, "total") ?: 1
            }
        } catch (_: Throwable) {
            totalMatches = 0
            activeMatchIndex = 0
        }
    }

    private fun clear() {
        try {
            session.finder.clear()
        } catch (_: Throwable) {
        }
    }

    private fun readInt(obj: Any, name: String): Int? {
        try {
            val f = obj.javaClass.getField(name)
            return f.getInt(obj)
        } catch (_: Throwable) {
        }
        try {
            val m = obj.javaClass.methods.firstOrNull { it.name == name || it.name.equals("get${name.replaceFirstChar { c -> c.uppercaseChar() }}", true) }
            val v = m?.invoke(obj)
            if (v is Int) return v
        } catch (_: Throwable) {
        }
        return null
    }

    private fun readBool(obj: Any, name: String): Boolean? {
        try {
            val f = obj.javaClass.getField(name)
            return f.getBoolean(obj)
        } catch (_: Throwable) {
        }
        try {
            val m = obj.javaClass.methods.firstOrNull { it.name == name }
            val v = m?.invoke(obj)
            if (v is Boolean) return v
        } catch (_: Throwable) {
        }
        return null
    }

    companion object {
        // GeckoSession.Finder flags (match mozilla-central values).
        private const val FIND_FORWARD = 1
        private const val FIND_BACKWARDS = 2
    }
}
