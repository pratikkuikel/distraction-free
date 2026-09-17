import Foundation

FileHandle.standardError.write("[dfdaemon] starting, loading blocklists...\n".data(using: .utf8)!)

try? FileManager.default.createDirectory(atPath: Config.stateDir, withIntermediateDirectories: true)

let alwaysOnTrie = DomainTrie()
BlocklistLoader.load(path: Config.alwaysOnList, into: alwaysOnTrie)

let socialTrie = DomainTrie()
BlocklistLoader.load(path: Config.socialList, into: socialTrie)

let server = DNSServer(alwaysOnTrie: alwaysOnTrie, socialTrie: socialTrie)
do {
    try server.run()
} catch {
    FileHandle.standardError.write("[dfdaemon] fatal: \(error)\n".data(using: .utf8)!)
    exit(1)
}
