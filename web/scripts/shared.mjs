// Builds the kDownloadFile library as JS (with TypeScript definitions) for the web app on any OS, with the Gradle wrapper jar.
import { spawnSync } from 'node:child_process'
import { fileURLToPath } from 'node:url'

const root = fileURLToPath(new URL('../../', import.meta.url))
const result = spawnSync(
  'java',
  [
    '-classpath',
    'gradle/wrapper/gradle-wrapper.jar',
    'org.gradle.wrapper.GradleWrapperMain',
    ':kDownloadFile:jsBrowserProductionLibraryDistribution',
    ...process.argv.slice(2),
  ],
  { cwd: root, stdio: 'inherit' },
)
if (result.error) console.error(`Could not start java: ${result.error.message}`)
process.exit(result.status ?? 1)
