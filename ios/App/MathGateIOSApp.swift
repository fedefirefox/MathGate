import SwiftUI

@main
struct MathGateIOSApp: App {
    @StateObject private var controller = ScreenTimeController()

    var body: some Scene {
        WindowGroup {
            ContentView()
                .environmentObject(controller)
        }
    }
}
