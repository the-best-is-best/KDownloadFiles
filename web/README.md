# KDownloadFile Web (React)

A React test page on top of the **same Kotlin library** used by Android and iOS.
The library's `jsMain` exports its API with `@JsExport`, Gradle compiles it to a JS library with
TypeScript definitions, and Vite + React import it.

```
kDownloadFile ──(Gradle, Kotlin/JS)──▶ kDownloadFile/build/dist/js/productionLibrary/kdownloadfile.mjs (+ .d.mts)
                                                         │
web ──(Vite, React)──── import 'kdownloadfile' ◀────────┘
```

## Run it

```bash
cd web
npm install
npm run shared   # builds :kDownloadFile for JS (Gradle); run again after changing Kotlin code
npm run dev      # http://localhost:5174
```

While `npm run dev` runs, rebuilding with `npm run shared` reloads the page by itself.

## What is where

- `../kDownloadFile/src/jsMain/.../KDownloadFileWeb.kt`: the exported API, used from TypeScript
  as is, with no wrapper:

  ```ts
  import { downloadFile, openFile, DownloadResult } from 'kdownloadfile'

  // The browser asks the user where to save ("Save as"), then the file downloads there.
  const result = await downloadFile(url, 'report.pdf', { onProgress: (p) => setPercent(p.percent) })
  if (result instanceof DownloadResult.Success) openFile(result.path)
  else if (result instanceof DownloadResult.HttpError && result.statusCode === 404) showNoResult()
  ```

  Also `saveBytes(Uint8Array, …)` and `saveText(string, …)`. Every call resolves (never rejects)
  with a `DownloadResult`: `Success`, `HttpError`, `NoInternet`, `Cancelled` or `Failed`.
- `../kDownloadFile/src/jsMain/.../internal/FileSystemAccess.kt`: the browser's "Save as" window.
- `src/App.tsx`: the buttons, the progress bar and the result.
- `testFiles.ts`: test files served by the dev server (`/test-files/10mb.bin`, `no-length.bin`,
  and 404 for anything else). They are local because a browser cannot fetch from another site
  that sends no CORS headers.
