import Foundation

/// Reversed-label trie for O(number of labels) domain blocking lookups,
/// regardless of how many hundreds of thousands of domains are loaded.
/// A match at any node on the path from root to leaf blocks that domain
/// and every subdomain under it.
final class DomainTrie {
    private final class Node {
        var children: [Substring: Node] = [:]
        var isBlocked = false
    }

    private let root = Node()
    private(set) var count = 0

    /// Insert a domain (apex or already-stripped of a leading "*.") so that
    /// it and all its subdomains are considered blocked.
    func insert(_ domain: String) {
        let labels = Self.labels(of: domain)
        guard !labels.isEmpty else { return }
        var node = root
        for label in labels.reversed() {
            if let next = node.children[label] {
                node = next
            } else {
                let next = Node()
                node.children[label] = next
                node = next
            }
        }
        if !node.isBlocked {
            node.isBlocked = true
            count += 1
        }
    }

    /// True if `domain` equals, or is a subdomain of, any inserted domain.
    /// Short-circuits as soon as a blocked ancestor is found.
    func isBlocked(_ domain: String) -> Bool {
        let labels = Self.labels(of: domain)
        guard !labels.isEmpty else { return false }
        var node = root
        for label in labels.reversed() {
            guard let next = node.children[label] else { return false }
            if next.isBlocked { return true }
            node = next
        }
        return false
    }

    private static func labels(of domain: String) -> [Substring] {
        var d = Substring(domain.lowercased())
        if d.hasSuffix(".") { d = d.dropLast() }
        return d.split(separator: ".")
    }
}
