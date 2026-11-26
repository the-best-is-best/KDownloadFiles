import SwiftUI
import kDownloadFileKit

struct ContentView: View {
    /// The test files: a 10 MB file with a known size, a real PDF to open, and a missing file.
    @StateObject private var download10Mb = FileJob(
        title: "Download 10 MB",
        detail: "Shows the percentage while downloading."
    ) { onProgress in
        try await KDownloadFileSwift.shared.download(
            url: "https://proof.ovh.net/files/10Mb.dat",
            fileName: "progress-test.dat",
            folderName: "Test",
            onProgress: onProgress
        )
    }

    @StateObject private var downloadPdf = FileJob(
        title: "Download a PDF",
        detail: "Saved in Files › On My iPhone › Demo › Downloads › Reports."
    ) { onProgress in
        try await KDownloadFileSwift.shared.download(
            url: "https://www.rd.usda.gov/sites/default/files/pdf-sample_0.pdf",
            fileName: "sample.pdf",
            folderName: "Reports",
            onProgress: onProgress
        )
    }

    @StateObject private var saveData = FileJob(
        title: "Save 20 MB",
        detail: "Saves data the app already has (saveFile)."
    ) { onProgress in
        try await KDownloadFileSwift.shared.save(
            data: Data(count: 20 * 1024 * 1024),
            fileName: "save-test.dat",
            folderName: "Test",
            onProgress: onProgress
        )
    }

    @StateObject private var missingFile = FileJob(
        title: "Missing file (404)",
        detail: "Shows the error; nothing is saved."
    ) { onProgress in
        try await KDownloadFileSwift.shared.download(
            url: "https://proof.ovh.net/files/this-file-does-not-exist.dat",
            fileName: "missing.dat",
            folderName: "Test",
            onProgress: onProgress
        )
    }

    var body: some View {
        NavigationStack {
            List {
                Section {
                    FileJobRow(job: download10Mb)
                    FileJobRow(job: downloadPdf)
                } header: {
                    Text("Download")
                }

                Section {
                    FileJobRow(job: saveData)
                } header: {
                    Text("Save")
                }

                Section {
                    FileJobRow(job: missingFile)
                } header: {
                    Text("Errors")
                } footer: {
                    Text("All screens here are SwiftUI. Downloading, saving, progress and errors come from the Kotlin library.")
                }
            }
            .navigationTitle("KDownloadFile")
        }
    }
}

/// One row: title, button, and then the progress bar or the result.
struct FileJobRow: View {
    @ObservedObject var job: FileJob

    var body: some View {
        VStack(alignment: .leading, spacing: 10) {
            HStack(alignment: .firstTextBaseline) {
                VStack(alignment: .leading, spacing: 2) {
                    Text(job.title)
                        .font(.headline)
                    Text(job.detail)
                        .font(.footnote)
                        .foregroundStyle(.secondary)
                }
                Spacer()
                Button(job.isRunning ? "…" : "Start") { job.start() }
                    .buttonStyle(.borderedProminent)
                    .disabled(job.isRunning)
            }

            switch job.state {
            case .idle:
                EmptyView()

            case .running(let progress):
                if let progress, progress.isSizeKnown {
                    ProgressView(value: progress.fraction) {
                        EmptyView()
                    } currentValueLabel: {
                        Text("\(progress.percent)% · \(megabytes(progress.bytesDone)) / \(megabytes(progress.totalBytes)) MB")
                            .monospacedDigit()
                    }
                } else {
                    HStack(spacing: 8) {
                        ProgressView()
                        Text(progress.map { "\(megabytes($0.bytesDone)) MB (size unknown)" } ?? "Starting…")
                            .font(.footnote)
                            .foregroundStyle(.secondary)
                    }
                }

            case .done:
                HStack {
                    Label("Done", systemImage: "checkmark.circle.fill")
                        .foregroundStyle(.green)
                    Spacer()
                    Button("Open") { job.open() }
                        .buttonStyle(.bordered)
                }

            case .failed(let message):
                Label(message, systemImage: "exclamationmark.triangle.fill")
                    .foregroundStyle(.red)
            }
        }
        .padding(.vertical, 4)
    }

    private func megabytes(_ bytes: Int64) -> String {
        String(format: "%.1f", Double(bytes) / 1_048_576)
    }
}

#Preview {
    ContentView()
}
