import DeviceActivity
import FamilyControls
import Foundation
import ManagedSettings

/// This extension applies the Apple-native shield after the monitored
/// usage reaches 20 + 5 * solved-problem minutes within today's interval.
final class MathGateMonitorExtension: DeviceActivityMonitor {
    private let managed = ManagedSettingsStore()

    override func intervalDidStart(for activity: DeviceActivityName) {
        super.intervalDidStart(for: activity)
        if activity == DeviceActivityName(MathGateShared.schedule) {
            MathGateShared.resetIfNewDay()
            managed.shield.applications = nil
            MathGateShared.defaults?.set(false, forKey: "isShielded")
        }
    }

    override func eventDidReachThreshold(_ event: DeviceActivityEvent.Name,
                                         activity: DeviceActivityName) {
        super.eventDidReachThreshold(event, activity: activity)
        guard String(describing: activity) == MathGateShared.schedule,
              event == DeviceActivityEvent.Name(MathGateShared.event) else { return }
        let selected = MathGateShared.selectedApps()
        guard !selected.isEmpty else { return }
        managed.shield.applications = selected
        MathGateShared.defaults?.set(true, forKey: "isShielded")
    }

    override func intervalDidEnd(for activity: DeviceActivityName) {
        super.intervalDidEnd(for: activity)
        managed.shield.applications = nil
        MathGateShared.defaults?.set(false, forKey: "isShielded")
    }
}
