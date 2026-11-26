import { useState } from 'react'
import { downloadFile, saveBytes, openFile, DownloadResult, type DownloadProgress } from 'kdownloadfile'

type Job = { label: string; run: (onProgress: (p: DownloadProgress) => void) => Promise<DownloadResult> }

const MB = 1024 * 1024

const jobs: Job[] = [
  { label: 'Download 10 MB', run: (p) => downloadFile('/test-files/10mb.bin', 'progress-test.bin', p) },
  { label: 'Download (no size)', run: (p) => downloadFile('/test-files/no-length.bin', 'no-length.bin', p) },
  { label: 'Save 20 MB', run: (p) => saveBytes(new Uint8Array(20 * MB), 'save-test.bin', p) },
  { label: 'Missing file (404)', run: (p) => downloadFile('/test-files/missing.bin', 'missing.bin', p) },
]

export function App() {
  const [running, setRunning] = useState<string | null>(null)
  const [progress, setProgress] = useState<DownloadProgress | null>(null)
  const [result, setResult] = useState<{ label: string; value: DownloadResult } | null>(null)

  async function start(job: Job) {
    setRunning(job.label)
    setProgress(null)
    setResult(null)
    const value = await job.run(setProgress)
    setResult({ label: job.label, value })
    setRunning(null)
  }

  return (
    <main>
      <h1>KDownloadFile · Web</h1>
      <p className="hint">The kDownloadFile library (Kotlin/JS, <code>@JsExport</code>) called from React.</p>

      <div className="buttons">
        {jobs.map((job) => (
          <button key={job.label} disabled={running != null} onClick={() => start(job)}>
            {job.label}
          </button>
        ))}
      </div>

      {running && <ProgressView label={running} progress={progress} />}
      {result && <ResultView label={result.label} result={result.value} />}
    </main>
  )
}

function ProgressView({ label, progress }: { label: string; progress: DownloadProgress | null }) {
  const percent = progress?.percent ?? null
  return (
    <section className="card" aria-live="polite">
      <div className="row">
        <span>{label}</span>
        <strong>{percent != null ? `${percent}%` : '…'}</strong>
      </div>
      {percent != null ? (
        <div className="bar" role="progressbar" aria-valuenow={percent} aria-valuemin={0} aria-valuemax={100}>
          <div className="fill" style={{ width: `${percent}%` }} />
        </div>
      ) : (
        <div className="bar indeterminate" role="progressbar" aria-busy="true">
          <div className="fill" />
        </div>
      )}
      {progress && (
        <small>
          {toMb(progress.bytesDone)} MB{progress.totalBytes != null ? ` / ${toMb(progress.totalBytes)} MB` : ' (size unknown)'}
        </small>
      )}
    </section>
  )
}

function ResultView({ label, result }: { label: string; result: DownloadResult }) {
  if (result instanceof DownloadResult.Success) {
    return (
      <section className="card ok">
        <div className="row">
          <span>{label}: done</span>
          <button onClick={() => openFile(result.path)}>Open</button>
        </div>
        <small className="path">{result.path}</small>
      </section>
    )
  }
  const text =
    result instanceof DownloadResult.HttpError
      ? `Server answered ${result.statusCode}${result.statusCode === 404 ? ' (file not found)' : ''}`
      : result instanceof DownloadResult.NoInternet
        ? 'No internet connection'
        : result instanceof DownloadResult.Failed
          ? result.message
          : 'Unknown result'
  return (
    <section className="card failed">
      <span>
        {label}: {text}
      </span>
    </section>
  )
}

const toMb = (bytes: number) => (bytes / MB).toFixed(1)
