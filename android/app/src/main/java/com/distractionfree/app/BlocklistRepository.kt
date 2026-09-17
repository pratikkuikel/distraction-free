package com.distractionfree.app

import android.content.Context
import java.io.File

/** Loads the plain-text domain lists (same format as the macOS daemon uses). */
object BlocklistRepository {

    fun buildAlwaysOnTrie(context: Context): DomainTrie {
        val trie = DomainTrie()
        load(AppPaths.alwaysOnList(context), trie)
        return trie
    }

    fun buildSocialTrie(context: Context): DomainTrie {
        val trie = DomainTrie()
        load(AppPaths.socialList(context), trie)
        return trie
    }

    private fun load(file: File, trie: DomainTrie) {
        if (!file.exists()) return
        file.forEachLine { rawLine ->
            var line = rawLine.trim()
            if (line.isEmpty() || line.startsWith("#")) return@forEachLine
            if (line.startsWith("*.")) line = line.substring(2)
            if (line.isNotEmpty()) trie.insert(line)
        }
    }
}
