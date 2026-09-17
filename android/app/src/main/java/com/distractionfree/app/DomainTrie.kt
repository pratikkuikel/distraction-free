package com.distractionfree.app

/**
 * Reversed-label trie for O(number of labels) domain lookups regardless of
 * how many hundreds of thousands of domains are loaded — matches the macOS
 * daemon's approach so both platforms have the same fast-path guarantee.
 */
class DomainTrie {
    private class Node {
        val children = HashMap<String, Node>()
        var isBlocked = false
    }

    private val root = Node()
    var count = 0
        private set

    fun insert(domainRaw: String) {
        val labels = labelsOf(domainRaw)
        if (labels.isEmpty()) return
        var node = root
        for (i in labels.indices.reversed()) {
            node = node.children.getOrPut(labels[i]) { Node() }
        }
        if (!node.isBlocked) {
            node.isBlocked = true
            count++
        }
    }

    fun isBlocked(domainRaw: String): Boolean {
        val labels = labelsOf(domainRaw)
        if (labels.isEmpty()) return false
        var node = root
        for (i in labels.indices.reversed()) {
            node = node.children[labels[i]] ?: return false
            if (node.isBlocked) return true
        }
        return false
    }

    private fun labelsOf(domainRaw: String): List<String> {
        var d = domainRaw.lowercase()
        if (d.endsWith(".")) d = d.dropLast(1)
        if (d.isEmpty()) return emptyList()
        return d.split(".")
    }
}
