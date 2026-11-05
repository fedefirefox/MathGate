import Foundation
import FamilyControls

enum MathGateShared {
    static let group = "group.dev.mathgate.shared"
    static let schedule = "mathgate.daily.v1"
    static let event = "mathgate.threshold.v1"
    static let freeMinutes = 20
    static let bonusMinutes = 5

    static var defaults: UserDefaults? { UserDefaults(suiteName: group) }

    static func dayKey() -> String {
        let formatter = DateFormatter()
        formatter.calendar = .current
        formatter.timeZone = .current
        formatter.dateFormat = "yyyy-MM-dd"
        return formatter.string(from: Date())
    }

    static func resetIfNewDay() {
        guard let preferences = defaults else { return }
        let today = dayKey()
        if preferences.string(forKey: "activeDay") != today {
            preferences.set(today, forKey: "activeDay")
            preferences.set(0, forKey: "earnedBonuses")
            preferences.set(false, forKey: "isShielded")
        }
    }

    static var earnedBonuses: Int {
        resetIfNewDay()
        return max(0, defaults?.integer(forKey: "earnedBonuses") ?? 0)
    }

    static func saveSelection(_ selection: FamilyActivitySelection) throws {
        guard let preferences = defaults else {
            throw NSError(domain: "MathGate", code: 1,
                          userInfo: [NSLocalizedDescriptionKey: "App Group is not configured."])
        }
        let data = try JSONEncoder().encode(selection)
        preferences.set(data, forKey: "selection")
    }

    static func selectedApps() -> Set<ApplicationToken> {
        guard let data = defaults?.data(forKey: "selection"),
              let selection = try? JSONDecoder().decode(FamilyActivitySelection.self, from: data)
        else { return [] }
        return selection.applicationTokens
    }
}
