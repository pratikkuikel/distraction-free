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

    fun buildAdultTrie(context: Context): DomainTrie {
        val trie = DomainTrie()
        load(AppPaths.adultList(context), trie)
        return trie
    }

    /// Small and stable enough to hardcode — mirrors the macOS daemon's
    /// Config.youtubeDomains.
    val youtubeDomains = listOf("youtube.com", "youtube-nocookie.com", "ytimg.com", "youtu.be")

    fun buildYoutubeTrie(): DomainTrie {
        val trie = DomainTrie()
        youtubeDomains.forEach { trie.insert(it) }
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
