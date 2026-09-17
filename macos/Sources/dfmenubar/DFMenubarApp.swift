import SwiftUI

struct DFMenubarApp: App {
    @StateObject private var state = AppState()

    var body: some Scene {
        MenuBarExtra("Distraction Free", systemImage: "shield.lefthalf.filled") {
            MenubarContentView(state: state)
        }
        .menuBarExtraStyle(.window)
    }
}
