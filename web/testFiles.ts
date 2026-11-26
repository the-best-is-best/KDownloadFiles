import type { Plugin } from 'vite'
import type { ServerResponse } from 'node:http'

/**
 * Test files served by the dev server itself, so the browser can fetch them (an outside server
 * would need CORS headers). The data is sent slowly so the percentage can be seen moving.
 *
 *   /test-files/10mb.bin        10 MB with Content-Length    → percentage known
 *   /test-files/no-length.bin   5 MB without Content-Length  → spinner, then 100%
 *   /test-files/missing.bin     404
 */
const MB = 1024 * 1024
const CHUNK = 256 * 1024
const CHUNK_DELAY_MS = 50

export function testFiles(): Plugin {
  return {
    name: 'kdownloadfile-test-files',
    configureServer(server) {
      server.middlewares.use('/test-files', (req, res) => {
        switch (req.url?.split('?')[0]) {
          case '/10mb.bin':
            return sendSlowly(res, 10 * MB, true)
          case '/no-length.bin':
            return sendSlowly(res, 5 * MB, false)
          default:
            res.statusCode = 404
            res.end('Not found')
        }
      })
    },
  }
}

function sendSlowly(res: ServerResponse, size: number, withLength: boolean) {
  res.statusCode = 200
  res.setHeader('Content-Type', 'application/octet-stream')
  if (withLength) res.setHeader('Content-Length', size)
  const chunk = Buffer.alloc(CHUNK, 7)
  let sent = 0
  const timer = setInterval(() => {
    const length = Math.min(CHUNK, size - sent)
    res.write(length === CHUNK ? chunk : chunk.subarray(0, length))
    sent += length
    if (sent >= size) {
      clearInterval(timer)
      res.end()
    }
  }, CHUNK_DELAY_MS)
  res.on('close', () => clearInterval(timer))
}
