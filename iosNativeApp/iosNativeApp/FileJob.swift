import Foundation
import kDownloadFileKit

/// One download or save in the list, with its progress and result.
@MainActor
final class FileJob: ObservableObject, Identifiable {
    enum State {
        case idle
        /// `progress` is nil until the first update arrives.
        case running(progress: FileProgress?)
        case done(path: String)
        case failed(message: String)
    }

    typealias Action = (_ onProgress: @escaping (FileProgress) -> Void) async throws -> FileOutcome

    let id = UUID()
    let title: String
    let detail: String
    private let action: Action

    @Published private(set) var state: State = .idle

    init(title: String, detail: String, action: @escaping Action) {
        self.title = title
        self.detail = detail
        self.action = action
    }

    var isRunning: Bool {
        if case .running = state { return true }
        return false
    }

    func start() {
        guard !isRunning else { return }
        state = .running(progress: nil)
        Task {
            do {
                // The library calls onProgress on the main thread.
                let outcome = try await action { [weak self] progress in
                    self?.state = .running(progress: progress)
                }
                state = Self.state(for: outcome)
            } catch {
                state = .failed(message: "Cancelled")
            }
        }
    }

    func open() {
        if case .done(let path) = state {
            KDownloadFileSwift.shared.open(path: path)
        }
    }

    private static func state(for outcome: FileOutcome) -> State {
        switch outcome {
        case let success as FileOutcome.Success:
            return .done(path: success.path)
        case let http as FileOutcome.HttpError:
            return .failed(message: http.statusCode == 404
                ? "File not found (404)"
                : "Server error \(http.statusCode)")
        case is FileOutcome.NoInternet:
            return .failed(message: "No internet connection")
        case let failed as FileOutcome.Failed:
            return .failed(message: failed.message)
        default:
            return .failed(message: "Unknown result")
        }
    }
}
