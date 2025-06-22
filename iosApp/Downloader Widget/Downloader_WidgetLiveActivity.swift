import ActivityKit
import WidgetKit
import SwiftUI
import KDownloadFileInterop

struct Downloader_WidgetLiveActivity: Widget {
    var body: some WidgetConfiguration {
        ActivityConfiguration(for: DownloadAttributes.self) { context in
            LiveActivityView(
                fileName: context.attributes.fileName,
                progress: context.state.progress,
                status: context.state.status
            )
        } dynamicIsland: { context in
            DynamicIsland {
                DynamicIslandExpandedRegion(.leading) {
                    Image(systemName: "arrow.down.circle.fill")
                        .foregroundColor(.blue)
                        .font(.title2)
                }
                DynamicIslandExpandedRegion(.trailing) {
                    Text(String(format: "%.0f%%", context.state.progress * 100))
                        .fontWeight(.semibold)
                        .foregroundColor(.blue)
                }
                DynamicIslandExpandedRegion(.bottom) {
                    VStack(alignment: .leading, spacing: 6) {
                        Text(context.attributes.fileName)
                            .bold()
                            .font(.headline)
                            .lineLimit(1)
                            .truncationMode(.middle)
                        ProgressView(value: context.state.progress)
                            .progressViewStyle(LinearProgressViewStyle(tint: .blue))
                        Text(context.state.status)
                            .font(.caption2)
                            .foregroundColor(.gray)
                    }
                    .padding(.top, 4)
                }
            } compactLeading: {
                Image(systemName: "arrow.down.circle.fill")
                    .foregroundColor(.blue)
            } compactTrailing: {
                Text(String(format: "%.0f%%", context.state.progress * 100))
                    .fontWeight(.bold)
                    .foregroundColor(.blue)
            } minimal: {
                Image(systemName: "arrow.down.circle.fill")
                    .foregroundColor(.blue)
            }
            .keylineTint(Color.blue)
        }
    }
}

struct LiveActivityView: View {
    let fileName: String
    let progress: Double
    let status: String

    var body: some View {
        VStack(spacing: 12) {
            Text("📁 \(fileName)")
                .font(.headline)
                .lineLimit(1)
                .truncationMode(.middle)
                .foregroundColor(.white)
            
            ProgressView(value: progress)
                .progressViewStyle(LinearProgressViewStyle(tint: .white))
                .scaleEffect(x: 1, y: 3, anchor: .center)
                .background(Color.white.opacity(0.3))
                .cornerRadius(4)
            
            Text(status)
                .font(.caption)
                .foregroundColor(.white.opacity(0.8))
        }
        .padding(16)
        .background(
            RoundedRectangle(cornerRadius: 20)
                .fill(LinearGradient(
                    gradient: Gradient(colors: [Color.blue.opacity(0.8), Color.cyan]),
                    startPoint: .topLeading,
                    endPoint: .bottomTrailing
                ))
        )
        .padding(8)
    }
}
