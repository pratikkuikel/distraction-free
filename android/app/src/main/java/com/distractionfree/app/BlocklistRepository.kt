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

    // fbcdn.net is deliberately excluded from shared/social-domains.txt with
    // a macOS-specific justification: macOS blocks by domain only (no
    // per-app exemption), and fbcdn.net is Meta's *shared* CDN that WhatsApp
    // Desktop also uses, so blocking it broke WhatsApp there. That
    // constraint doesn't hold on Android — WhatsApp is already exempted at
    // the UID level via AppExemptions/addDisallowedApplication, so its
    // traffic never reaches this trie regardless of domain rules. Without
    // fbcdn.net, Instagram (not exempted) kept working: its own domains
    // (instagram.com, cdninstagram.com) got blocked but photo/video content
    // loaded fine from fbcdn.net edge nodes instead. Real-device log showed
    // z-m-gateway.facebook.com being correctly blocked on every retry while
    // the app still worked — this was why.
    private val androidOnlySocialDomains = listOf("fbcdn.net")

    fun buildSocialTrie(context: Context): DomainTrie {
        val trie = DomainTrie()
        load(AppPaths.socialList(context), trie)
        androidOnlySocialDomains.forEach { trie.insert(it) }
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
