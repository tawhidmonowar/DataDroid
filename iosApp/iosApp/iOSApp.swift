import SwiftUI
import Shared

@main
struct iOSApp: App {
    init() {
        MainViewControllerKt.startDataDroid()
    }

    var body: some Scene {
        WindowGroup {
            ContentView()
        }
    }
}
