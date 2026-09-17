import SwiftUI

struct MenubarContentView: View {
    @ObservedObject var state: AppState
    @State private var showDisableConfirm = false

    var body: some View {
        VStack(alignment: .leading, spacing: 12) {
            Text("Distraction Free")
                .font(.headline)

            VStack(alignment: .leading, spacing: 4) {
                statusRow("Adult / gambling / piracy", "Always blocked")
                statusRow("VPN / proxy / Tor bypass", "Always blocked")
                statusRow("SafeSearch", "Enforced")
                statusRow(
                    "Social media",
                    state.isInForcedSocialWindow ? "Blocked (nightly window)" :
                        state.socialManualBlock ? "Blocked (manual)" : "Open"
                )
            }
            .font(.system(size: 12))

            Divider()

            VStack(alignment: .leading, spacing: 2) {
                Text("Queries: \(state.totalQueries)  ·  Blocked: \(state.blockedAlwaysOn + state.blockedSocial)  ·  SafeSearch: \(state.safeSearchRewrites)")
                if let updated = state.statsUpdatedAt {
                    Text("Updated \(updated.formatted(date: .omitted, time: .shortened))")
                }
            }
            .font(.system(size: 11))
            .foregroundStyle(.secondary)

            Divider()

            Toggle(isOn: Binding(
                get: { state.socialManualBlock },
                set: { state.setSocialManualBlock($0) }
            )) {
                Text("Block social media now")
            }
            .disabled(state.isInForcedSocialWindow)
            .toggleStyle(.switch)

            Button("Update blocklist…") {
                state.runUpdateScript()
            }

            Divider()

            if let hours = state.disableHoursRemaining {
                Text("Disable requested — \(hours)h remaining")
                    .font(.system(size: 11))
                    .foregroundStyle(.secondary)
            }

            Button(role: .destructive) {
                showDisableConfirm = true
            } label: {
                Text("Disable protection")
            }
            .confirmationDialog(
                "Disabling takes effect 24 hours after your request, and the timer restarts on every new request.",
                isPresented: $showDisableConfirm,
                titleVisibility: .visible
            ) {
                Button("Request disable", role: .destructive) {
                    state.recordDisableAttempt()
                }
                Button("Cancel", role: .cancel) {}
            }

            Divider()

            Button("Quit menu (protection keeps running)") {
                NSApplication.shared.terminate(nil)
            }
        }
        .padding(14)
        .frame(width: 300)
    }

    private func statusRow(_ label: String, _ value: String) -> some View {
        HStack {
            Text(label)
            Spacer()
            Text(value).foregroundStyle(.secondary)
        }
    }
}
