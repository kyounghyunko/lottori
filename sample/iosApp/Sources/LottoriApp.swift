import SwiftUI
import LottoriSampleKit

@main
struct LottoriApp: App {
    var body: some Scene { WindowGroup { PlayerView().ignoresSafeArea() } }
}

private struct PlayerView: UIViewControllerRepresentable {
    func makeUIViewController(context: Context) -> UIViewController {
        MainViewControllerKt.MainViewController()
    }
    func updateUIViewController(_ controller: UIViewController, context: Context) {}
}
