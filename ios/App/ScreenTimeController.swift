import DeviceActivity
import FamilyControls
import Foundation
import ManagedSettings
import SwiftUI

struct MathProblem: Codable, Identifiable {
    let id: String
    let level: String
    let topic: String
    let prompt: String
    let latex: String
    let answer: Double
    let tolerance: Double
    let hint: String

    func accepts(_ response: String) -> Bool {
        let value = Double(response.trimmingCharacters(in: .whitespacesAndNewlines)
            .replacingOccurrences(of: ",", with: "."))
        guard let value, value.isFinite else { return false }
        return abs(value - answer) <= tolerance
    }
}
private struct ProblemBank: Codable {
    let schema_version: Int
    let revision: Int
    let problems: [MathProblem]
}

@MainActor
final class ScreenTimeController: ObservableObject {
    @Published var selection = FamilyActivitySelection()
    @Published var authorized = false
    @Published var selected = false
    @Published var enabled = false
    @Published var shielded = false
    @Published var errorMessage: String?
    @Published var difficulty = "standard"
    @Published var currentProblem: MathProblem?
    @Published var rewardReady = false

    private let center = DeviceActivityCenter()
    private let shield = ManagedSettingsStore()
    private let problems: [MathProblem]

    init() {
        if let url = Bundle.main.url(forResource: "problem_bank", withExtension: "json"),
           let data = try? Data(contentsOf: url),
           let decoded = try? JSONDecoder().decode(ProblemBank.self, from: data),
           decoded.schema_version == 1 {
            problems = decoded.problems
        } else {
            problems = []
        }
        if let data = MathGateShared.defaults?.data(forKey: "selection"),
           let existing = try? JSONDecoder().decode(FamilyActivitySelection.self, from: data) {
            selection = existing
            selected = !existing.applicationTokens.isEmpty
        }
        authorized = AuthorizationCenter.shared.authorizationStatus == .approved
        refreshState()
        nextProblem()
    }

    func refreshState() {
        MathGateShared.resetIfNewDay()
        authorized = AuthorizationCenter.shared.authorizationStatus == .approved
        shielded = MathGateShared.defaults?.bool(forKey: "isShielded") == true
        selected = !selection.applicationTokens.isEmpty
        enabled = authorized && selected &&
            (MathGateShared.defaults?.bool(forKey: "monitoringEnabled") == true)
    }

    func requestPermission() async {
        do {
            try await AuthorizationCenter.shared.requestAuthorization(for: .individual)
            authorized = AuthorizationCenter.shared.authorizationStatus == .approved
            errorMessage = authorized ? nil : "Screen Time authorization wasn't granted."
        } catch {
            errorMessage = "Screen Time request failed: " + error.localizedDescription
        }
    }

    func chooseDifficulty(_ newLevel: String) {
        guard ["foundation", "standard", "advanced"].contains(newLevel) else { return }
        difficulty = newLevel
        nextProblem()
    }

    func nextProblem() {
        let options = problems.filter { $0.level == difficulty }
        currentProblem = options.randomElement()
    }

    func activate() {
        guard authorized else {
            errorMessage = "First authorize Screen Time access."
            return
        }
        guard selection.applicationTokens.count == 1 else {
            errorMessage = "Choose exactly one app—Instagram—in Apple's protected picker."
            return
        }
        do {
            try MathGateShared.saveSelection(selection)
            try startDailyMonitoring()
            selected = true
            enabled = true
            errorMessage = nil
            MathGateShared.defaults?.set(true, forKey: "monitoringEnabled")
        } catch {
            errorMessage = "Couldn't start monitoring: " + error.localizedDescription
        }
    }

    private func startDailyMonitoring() throws {
        let schedule = DeviceActivitySchedule(
            intervalStart: DateComponents(hour: 0, minute: 0),
            intervalEnd: DateComponents(hour: 23, minute: 59),
            repeats: true
        )
        let limit = MathGateShared.freeMinutes +
            MathGateShared.bonusMinutes * MathGateShared.earnedBonuses
        let event = DeviceActivityEvent(
            applications: selection.applicationTokens,
            threshold: DateComponents(hour: limit / 60, minute: limit % 60),
            includesPastActivity: true
        )
        let name = DeviceActivityName(MathGateShared.schedule)
        center.stopMonitoring([name])
        try center.startMonitoring(
            name,
            during: schedule,
            events: [DeviceActivityEvent.Name(MathGateShared.event): event]
        )
    }

    func check(_ response: String) -> Bool {
        guard shielded, let currentProblem, currentProblem.accepts(response) else {
            return false
        }
        rewardReady = true
        return true
    }

    func claimFiveMinutes() {
        guard rewardReady && shielded && authorized && selected else { return }
        // The monitor may be late to fire; user must reopen MathGate to solve.
        let previous = MathGateShared.earnedBonuses
        MathGateShared.defaults?.set(previous + 1, forKey: "earnedBonuses")
        do {
            try startDailyMonitoring()
            shield.shield.applications = nil
            MathGateShared.defaults?.set(false, forKey: "isShielded")
            rewardReady = false
            shielded = false
            errorMessage = nil
            nextProblem()
        } catch {
            MathGateShared.defaults?.set(previous, forKey: "earnedBonuses")
            errorMessage = "Could not extend monitoring: " + error.localizedDescription
        }
    }
}
