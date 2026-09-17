// swift-tools-version:5.9
import PackageDescription

let package = Package(
    name: "distraction-free",
    platforms: [.macOS(.v13)],
    targets: [
        .executableTarget(
            name: "dfdaemon",
            path: "Sources/dfdaemon"
        ),
        .executableTarget(
            name: "dfmenubar",
            path: "Sources/dfmenubar"
        ),
    ]
)
