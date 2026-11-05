import FamilyControls
import SwiftUI

private enum Theme {
    static let ink = Color(red: 0.04, green: 0.18, blue: 0.19)
    static let teal = Color(red: 0.00, green: 0.57, blue: 0.51)
    static let lime = Color(red: 0.84, green: 0.97, blue: 0.36)
    static let bg = Color(red: 0.97, green: 0.98, blue: 0.96)
    static let coral = Color(red: 0.95, green: 0.38, blue: 0.29)
}

struct ContentView: View {
    @EnvironmentObject private var model: ScreenTimeController
    @Environment(\.scenePhase) private var scenePhase
    @State private var showPicker = false
    @State private var answer = ""
    @State private var feedback = ""
    @State private var showReward = false

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 18) {
                HStack {
                    Circle().fill(Theme.teal).frame(width: 8, height: 8)
                    Text("MATHGATE  /  iOS").font(.caption.bold())
                        .foregroundStyle(Theme.teal)
                }
                Text("LESS SCROLL.\nMORE SOLVING.")
                    .font(.system(size: 35, weight: .black, design: .rounded))
                    .foregroundStyle(Theme.ink)
                Text("20 minutes of Instagram per day, then solve one Analysis I/II exercise to earn five more.")
                    .font(.subheadline).foregroundStyle(.secondary)

                VStack(alignment: .leading, spacing: 9) {
                    Text("TODAY'S ALLOWANCE")
                        .font(.caption.bold()).foregroundStyle(Theme.lime)
                    Text("\(MathGateShared.freeMinutes + MathGateShared.earnedBonuses * MathGateShared.bonusMinutes) min")
                        .font(.largeTitle.bold()).foregroundStyle(.white)
                    Text("Base 20 minutes · +5 for each solved problem")
                        .font(.caption).foregroundStyle(.white.opacity(0.9))
                    Text(model.shielded ? "INSTAGRAM SHIELDED — SOLVE BELOW" :
                        (model.enabled ? "MONITORING ENABLED" : "SETUP REQUIRED"))
                        .font(.caption.bold()).foregroundStyle(Theme.lime)
                }
                .padding(20).frame(maxWidth: .infinity, alignment: .leading)
                .background(Theme.ink, in: RoundedRectangle(cornerRadius: 20))

                if !model.enabled {
                    onboarding
                } else {
                    challenge
                }
                if let error = model.errorMessage {
                    Text(error).foregroundStyle(Theme.coral).font(.footnote)
                }
                Text("Apple displays a system Screen Time shield. To solve the challenge, open MathGate manually—the iOS shield can't redirect directly to another app. Apple may allow you to revoke permission or remove MathGate.")
                    .font(.footnote).foregroundStyle(.secondary)
            }
            .padding(22)
        }
        .background(Theme.bg.ignoresSafeArea())
        .familyActivityPicker(isPresented: $showPicker, selection: $model.selection)
        .sheet(isPresented: $showReward) {
            rewardSheet
        }
        .onChange(of: scenePhase) { _, phase in
            if phase == .active { model.refreshState() }
        }
    }

    private var onboarding: some View {
        VStack(alignment: .leading, spacing: 13) {
            Text("FIRST-TIME SETUP").font(.headline).foregroundStyle(Theme.ink)
            setupCard(number: "01", title: "Allow Screen Time",
                description: "Apple asks for your individual Screen Time authorization. This is required to monitor and shield selected apps.",
                complete: model.authorized, button: "Authorize Screen Time") {
                Task { await model.requestPermission() }
            }
            setupCard(number: "02", title: "Choose Instagram",
                description: "Use Apple's private app picker and select only Instagram. The app receives an opaque token, not a list of your installed apps.",
                complete: model.selected, button: "Choose protected app") {
                showPicker = true
            }
            setupCard(number: "03", title: "Start 20-minute limit",
                description: "Register a Device Activity monitor and enable Apple's system app shield after 20 minutes of actual usage.",
                complete: model.enabled, button: "Start protection") {
                model.activate()
            }
        }
    }

    private func setupCard(number: String, title: String, description: String,
                           complete: Bool, button: String, action: @escaping () -> Void)
        -> some View {
        VStack(alignment: .leading, spacing: 9) {
            Text(number + "  ·  " + title.uppercased() + (complete ? "  ✓" : ""))
                .font(.subheadline.bold()).foregroundStyle(Theme.ink)
            Text(description).font(.footnote).foregroundStyle(.secondary)
            Button(button, action: action)
                .buttonStyle(.borderedProminent)
                .tint(complete ? Theme.teal : Theme.ink)
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .padding(16)
        .background(.white, in: RoundedRectangle(cornerRadius: 18))
    }

    private var challenge: some View {
        VStack(alignment: .leading, spacing: 13) {
            Text("CHALLENGE DIFFICULTY")
                .font(.caption.bold()).foregroundStyle(Theme.teal)
            Picker("Difficulty", selection: Binding(
                get: { model.difficulty },
                set: { model.chooseDifficulty($0) }
            )) {
                Text("Foundation").tag("foundation")
                Text("Standard").tag("standard")
                Text("Advanced").tag("advanced")
            }
            .pickerStyle(.segmented)

            if model.shielded {
                Text("Instagram is locked. Solve this problem to unlock another 5 minutes.")
                    .font(.subheadline.bold())
                    .foregroundStyle(Theme.ink)
            } else {
                Text("Practice preview — no reward while Instagram is available.")
                    .font(.caption).foregroundStyle(.secondary)
            }
            if let p = model.currentProblem {
                Text(p.topic.uppercased()).font(.caption.bold())
                    .foregroundStyle(Theme.teal)
                Text(p.prompt).font(.body)
                Text(p.latex).font(.system(.body, design: .monospaced))
                    .textSelection(.enabled)
                    .padding(9)
                    .frame(maxWidth: .infinity, alignment: .leading)
                    .background(Theme.bg, in: RoundedRectangle(cornerRadius: 12))
                if model.shielded {
                    TextField("Type numerical result", text: $answer)
                        .keyboardType(.decimalPad)
                        .textFieldStyle(.roundedBorder)
                    Button("Check answer") {
                        if model.check(answer) {
                            showReward = true
                            feedback = ""
                        } else {
                            feedback = "Not quite. Hint: \(p.hint)"
                        }
                    }
                    .buttonStyle(.borderedProminent)
                    .tint(Theme.ink)
                }
                if !feedback.isEmpty {
                    Text(feedback).foregroundStyle(Theme.coral).font(.footnote)
                }
                Button("Show another practice problem") {
                    model.nextProblem()
                    answer = ""
                    feedback = ""
                }
                .disabled(model.shielded)
            }
            Button("Refresh protection state") { model.refreshState() }
                .font(.footnote)
        }
        .padding(17)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(.white, in: RoundedRectangle(cornerRadius: 18))
    }

    private var rewardSheet: some View {
        VStack(spacing: 18) {
            Text("🐾").font(.system(size: 55))
            Text("CONGRATS!").font(.largeTitle.bold())
                .foregroundStyle(Theme.coral)
            Text("The Feline Mathematics Council approves.")
                .foregroundStyle(Theme.ink)
            Text("The contour closes. The cat gets treats.\nYou get five more minutes.")
                .multilineTextAlignment(.center)
            Button("CLAIM +5:00") {
                model.claimFiveMinutes()
                if model.errorMessage == nil {
                    answer = ""
                    showReward = false
                }
            }
            .buttonStyle(.borderedProminent)
            .tint(Theme.teal)
        }
        .padding(24)
        .presentationDetents([.medium])
    }
}
