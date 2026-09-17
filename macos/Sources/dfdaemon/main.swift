import Foundation

FileHandle.standardError.write("[dfdaemon] starting, loading blocklists...\n".data(using: .utf8)!)

try? FileManager.default.createDirectory(atPath: Config.stateDir, withIntermediateDirectories: true)

let alwaysOnTrie = DomainTrie()
BlocklistLoader.load(path: Config.alwaysOnList, into: alwaysOnTrie)

let adultTrie = DomainTrie()
BlocklistLoader.load(path: Config.adultList, into: adultTrie)

let socialTrie = DomainTrie()
BlocklistLoader.load(path: Config.socialList, into: socialTrie)

let youtubeTrie = DomainTrie()
for domain in Config.youtubeDomains { youtubeTrie.insert(domain) }

let server = DNSServer(alwaysOnTrie: alwaysOnTrie, adultTrie: adultTrie, socialTrie: socialTrie, youtubeTrie: youtubeTrie)
do {
    try server.run()
} catch {
    FileHandle.standardError.write("[dfdaemon] fatal: \(error)\n".data(using: .utf8)!)
    exit(1)
}
