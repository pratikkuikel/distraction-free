import SwiftUI

struct MenubarContentView: View {
    @ObservedObject var state: AppState
    @State private var showDisableConfirm = false

    var body: some View {
        VStack(alignment: .leading, spacing: 12) {
            HStack {
                Text("Distraction Free")
                    .font(.headline)
                Spacer()
                if state.currentStreak > 0 {
                    Text("🔥 \(state.currentStreak)")
                        .font(.system(size: 14, weight: .bold))
                }
            }
            if state.bestStreak > 0 {
                Text("Best streak: \(state.bestStreak) day\(state.bestStreak == 1 ? "" : "s")")
                    .font(.system(size: 11))
                    .foregroundStyle(.secondary)
            }

            statsCard

            if !state.todayQuote.isEmpty {
                Text(state.todayQuote)
                    .font(.system(size: 12, weight: .medium, design: .rounded))
                    .italic()
                    .foregroundStyle(.secondary)
                    .fixedSize(horizontal: false, vertical: true)
            }

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

            // Only shown when it can actually do something — during the
            // forced nightly window social is already blocked regardless,
            // so a toggle that visibly does nothing is just confusing.
            if !state.isInForcedSocialWindow {
                Toggle(isOn: Binding(
                    get: { state.socialManualBlock },
                    set: { state.setSocialManualBlock($0) }
                )) {
                    Text("Block social media now")
                }
                .toggleStyle(.switch)
            }

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
                "Disabling takes effect 24 hours after your request, and the timer restarts on every new request. Your \(state.currentStreak)-day streak resets immediately, though.",
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
        .onAppear { state.startPolling() }
        .onDisappear { state.stopPolling() }
    }

    private var statsCard: some View {
        VStack(alignment: .leading, spacing: 8) {
            Text("STATS")
                .font(.system(size: 10, weight: .bold))
                .foregroundStyle(.secondary)
            HStack(alignment: .firstTextBaseline, spacing: 18) {
                statBlock(value: "\(state.totalBlocked)", label: "Blocked", color: .orange)
                statBlock(value: Formatting.dataSize(mb: state.dataBackMB), label: "Est. data saved", color: .purple)
                statBlock(value: Formatting.duration(minutes: state.timeBackMinutes), label: "Est. time back", color: .green)
            }
        }
        .padding(10)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(RoundedRectangle(cornerRadius: 10).fill(Color.primary.opacity(0.05)))
    }

    private func statBlock(value: String, label: String, color: Color) -> some View {
        VStack(alignment: .leading, spacing: 2) {
            Text(value)
                .font(.system(size: 16, weight: .bold))
                .foregroundStyle(color)
            Text(label)
                .font(.system(size: 10))
                .foregroundStyle(.secondary)
        }
    }

    private func statusRow(_ label: String, _ value: String) -> some View {
        HStack {
            Text(label)
            Spacer()
            Text(value).foregroundStyle(.secondary)
        }
    }
}
