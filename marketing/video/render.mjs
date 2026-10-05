// Render a scene to MP4: node render.mjs <scene> <out.mp4> [fps]
// Also: node render.mjs <scene> --stills <dir> t1 t2 ...
import { chromium } from "playwright";
import { createServer } from "node:http";
import { readFile } from "node:fs/promises";
import { spawn } from "node:child_process";
import { extname, join, dirname } from "node:path";
import { fileURLToPath } from "node:url";
import { mkdirSync } from "node:fs";

const root = dirname(fileURLToPath(import.meta.url));
const [scene, out, ...rest] = process.argv.slice(2);
const types = { ".html": "text/html", ".js": "text/javascript", ".png": "image/png", ".jpg": "image/jpeg", ".webp": "image/webp", ".svg": "image/svg+xml", ".css": "text/css", ".woff2": "font/woff2" };

const server = createServer(async (req, res) => {
  try {
    const path = join(root, decodeURIComponent(new URL(req.url, "http://x").pathname));
    const body = await readFile(path);
    res.writeHead(200, { "content-type": types[extname(path)] || "application/octet-stream" });
    res.end(body);
  } catch {
    res.writeHead(404).end();
  }
}).listen(0);
const port = server.address().port;

const browser = await chromium.launch();
const page = await browser.newPage({ viewport: { width: 1920, height: 1080 } });
await page.goto(`http://localhost:${port}/index.html?scene=${scene}&static`);
await page.waitForFunction(() => window.READY === true, null, { timeout: 60000 });
const stage = page.locator("#stage");

if (out === "--stills") {
  const [dir, ...times] = rest;
  mkdirSync(dir, { recursive: true });
  for (const t of times) {
    await page.evaluate((t) => window.renderAt(t), Number(t));
    await stage.screenshot({ path: join(dir, `${scene}-${t}.png`) });
  }
} else {
  const fps = Number(rest[0] || 30);
  const duration = await page.evaluate(() => window.DURATION);
  const frames = Math.round(duration * fps);
  const ff = spawn("ffmpeg", ["-y", "-v", "error", "-f", "image2pipe", "-framerate", String(fps), "-i", "-",
    "-c:v", "libx264", "-pix_fmt", "yuv420p", "-crf", "18", "-preset", "medium", "-movflags", "+faststart", out], { stdio: ["pipe", "inherit", "inherit"] });
  for (let i = 0; i < frames; i++) {
    await page.evaluate((t) => window.renderAt(t), i / fps);
    const buf = await stage.screenshot({ type: "jpeg", quality: 95 });
    if (!ff.stdin.write(buf)) await new Promise((r) => ff.stdin.once("drain", r));
    if (i % 60 === 0) process.stderr.write(`${scene}: frame ${i}/${frames}\n`);
  }
  ff.stdin.end();
  await new Promise((r) => ff.on("close", r));
}
await browser.close();
server.close();
