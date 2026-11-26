import { defineConfig, type Plugin } from 'vite'
import react from '@vitejs/plugin-react'
import { resolve } from 'node:path'
import { fileURLToPath } from 'node:url'
import { testFiles } from './testFiles.ts'

// The kDownloadFile library compiled to JS by Gradle (its jsMain exports the API with @JsExport):
//   ./gradlew :kDownloadFile:jsBrowserProductionLibraryDistribution   (or: npm run shared)
const sharedLib = fileURLToPath(
  new URL('../kDownloadFile/build/dist/js/productionLibrary/kdownloadfile.mjs', import.meta.url),
)

const samePath = (a: string, b: string) => resolve(a).toLowerCase() === resolve(b).toLowerCase()

/** The Kotlin library lives outside web/, so Vite does not watch it: reload the page when Gradle rebuilds it. */
function reloadOnSharedRebuild(): Plugin {
  return {
    name: 'kdownloadfile-reload',
    configureServer(server) {
      server.watcher.add(sharedLib)
      server.watcher.on('change', (file) => {
        if (!samePath(file, sharedLib)) return
        server.moduleGraph.getModulesByFile(sharedLib)?.forEach((m) => server.moduleGraph.invalidateModule(m))
        server.config.logger.info('kdownloadfile rebuilt → reloading', { timestamp: true })
        server.ws.send({ type: 'full-reload' })
      })
    },
  }
}

export default defineConfig({
  plugins: [react(), reloadOnSharedRebuild(), testFiles()],
  resolve: { alias: { kdownloadfile: sharedLib } },
  server: {
    port: 5174,
    fs: { allow: ['..'] },
  },
})
