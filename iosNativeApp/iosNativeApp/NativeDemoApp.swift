import SwiftUI

/// Native SwiftUI sample for KDownloadFile: every screen is SwiftUI; only the downloading and
/// saving come from the Kotlin library (kDownloadFileKit).
@main
struct NativeDemoApp: App {
    var body: some Scene {
        WindowGroup {
            ContentView()
        }
    }
}
