// 入口：HTTP + WebSocket + 静态 PWA
import { createServer } from 'node:http'
import { mkdir } from 'node:fs/promises'
import { createReadStream } from 'node:fs'
import { stat } from 'node:fs/promises'
import { join, extname, normalize } from 'node:path'
import { fileURLToPath } from 'node:url'
import { openDb } from './db.mjs'
import { createHub } from './ws.mjs'
import { createHttp } from './http.mjs'

const PORT = Number(process.env.PORT || 8787)
const DATA_DIR = process.env.DATA_DIR || '/data'
const BLOB_MAX_MB = Number(process.env.BLOB_MAX_MB || 50)
const PUBLIC_DIR = process.env.PUBLIC_DIR || fileURLToPath(new URL('../public/', import.meta.url))
// 额外静态目录：放安装包、更新清单这类「部署后还要替换」的文件（挂载宿主机目录，重建容器不丢）
const ASSETS_DIR = process.env.ASSETS_DIR || '/app/assets'
const ASSETS_PREFIX = '/assets/'

const cfg = {
  dataDir: DATA_DIR,
  blobDir: join(DATA_DIR, 'blobs'),
  blobMaxMb: BLOB_MAX_MB,
}

const MIME = {
  '.html': 'text/html; charset=utf-8',
  '.js': 'text/javascript; charset=utf-8',
  '.mjs': 'text/javascript; charset=utf-8',
  '.css': 'text/css; charset=utf-8',
  '.json': 'application/json; charset=utf-8',
  '.webmanifest': 'application/manifest+json',
  '.svg': 'image/svg+xml',
  '.png': 'image/png',
  '.ico': 'image/x-icon',
  '.woff2': 'font/woff2',
}

async function serveStatic(req, res, pathname) {
  const rel = normalize(decodeURIComponent(pathname)).replace(/^(\.\.[/\\])+/, '')
  let root = PUBLIC_DIR
  let sub = rel
  const assetHit = [ASSETS_PREFIX, '/app/'].find((p) => pathname.startsWith(p))
  if (assetHit) {
    root = ASSETS_DIR
    sub = pathname.slice(assetHit.length - 1)
  }
  let file = join(root, sub)
  try {
    const info = await stat(file)
    if (info.isDirectory()) file = join(file, 'index.html')
  } catch {
    // /assets 是文件下载区，不做 SPA 回退
    if (root === ASSETS_DIR) {
      res.writeHead(404, { 'content-type': 'text/plain; charset=utf-8' })
      res.end('not found')
      return
    }
    // SPA 回退
    try {
      file = join(PUBLIC_DIR, 'index.html')
      await stat(file)
    } catch {
      res.writeHead(404, { 'content-type': 'text/plain; charset=utf-8' })
      res.end('not found')
      return
    }
  }
  const info = await stat(file).catch(() => null)
  if (!info) {
    res.writeHead(404)
    res.end()
    return
  }
  const type = MIME[extname(file)] || 'application/octet-stream'
  const base = { 'content-type': type, 'cache-control': 'no-cache', 'accept-ranges': 'bytes' }

  // 支持断点续传：手机弱网下 19MB 的包一次下不完时，浏览器可以从断点继续
  const range = String(req.headers.range || '').trim()
  if (range) {
    const m = /^bytes=(\d*)-(\d*)$/.exec(range)
    if (m) {
      let start = m[1] === '' ? info.size - Number(m[2] || 0) : Number(m[1])
      let end = m[1] === '' || m[2] === '' ? info.size - 1 : Number(m[2])
      if (!Number.isFinite(start) || !Number.isFinite(end) || start < 0 || start > end || start >= info.size) {
        res.writeHead(416, { ...base, 'content-range': `bytes */${info.size}` })
        res.end()
        return
      }
      end = Math.min(end, info.size - 1)
      res.writeHead(206, { ...base, 'content-length': end - start + 1, 'content-range': `bytes ${start}-${end}/${info.size}` })
      createReadStream(file, { start, end }).pipe(res)
      return
    }
  }

  res.writeHead(200, { ...base, 'content-length': info.size })
  createReadStream(file).pipe(res)
}

async function main() {
  await mkdir(cfg.dataDir, { recursive: true })
  await mkdir(cfg.blobDir, { recursive: true })

  const db = openDb(join(cfg.dataDir, 'relay.db'))
  const hub = createHub({ db, cfg })
  const api = createHttp({ db, hub, cfg })

  const server = createServer((req, res) => {
    const pathname = new URL(req.url, 'http://localhost').pathname
    if (pathname.startsWith('/api/') || pathname === '/healthz') {
      return api(req, res)
    }
    if (req.method === 'GET' || req.method === 'HEAD') return serveStatic(req, res, pathname)
    res.writeHead(405)
    res.end()
  })

  hub.attach(server)

  server.listen(PORT, () => {
    console.log(`[relay] listening on :${PORT} data=${DATA_DIR} blobMax=${BLOB_MAX_MB}MB`)
  })

  const shutdown = (sig) => {
    console.log(`[relay] ${sig}, shutting down`)
    server.close(() => process.exit(0))
    setTimeout(() => process.exit(0), 5000).unref()
  }
  process.on('SIGTERM', () => shutdown('SIGTERM'))
  process.on('SIGINT', () => shutdown('SIGINT'))
}

main().catch((err) => {
  console.error('[relay] fatal', err)
  process.exit(1)
})
