import Foundation

enum BlocklistLoader {
    /// Loads a plain-text domain list: one domain per line, "#" comments and
    /// blank lines ignored, an optional leading "*." stripped (HaGeZi's
    /// wildcard format uses it to denote "this domain and its subdomains" —
    /// we always block subdomains via the trie regardless, so the prefix is
    /// just noise for our purposes).
    static func load(path: String, into trie: DomainTrie) {
        guard let data = FileManager.default.contents(atPath: path),
              let text = String(data: data, encoding: .utf8) else {
            FileHandle.standardError.write("warning: could not read blocklist at \(path)\n".data(using: .utf8)!)
            return
        }
        text.enumerateLines { line, _ in
            var l = Substring(line)
            if l.hasPrefix("#") || l.isEmpty { return }
            if l.hasPrefix("*.") { l = l.dropFirst(2) }
            l = l.trimmingCharacters(in: .whitespaces)[...]
            guard !l.isEmpty else { return }
            trie.insert(String(l))
        }
    }
}
