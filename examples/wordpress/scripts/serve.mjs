// Serves WordPress (PHP + SQLite in WebAssembly) on http://127.0.0.1:3000 for the Playwright tests.
//
// Installing WordPress takes about 3 seconds on every boot, and this example never needs a different
// site, so the install is done once: the first run builds a snapshot of an installed site into
// .cache/ (about 10 seconds), and every run after that boots from a fresh copy of it (under 1 second).
// The tests edit posts, so the cached snapshot itself is never served. Delete .cache/ to rebuild.
import { spawn, spawnSync } from "node:child_process";
import fs from "node:fs";
import { createRequire } from "node:module";
import os from "node:os";
import path from "node:path";
import { fileURLToPath } from "node:url";

const PORT = 3000;
const SITE_URL = `http://127.0.0.1:${PORT}`;
// Pinned so the snapshot, and so the tests, don't change when a new WordPress is released.
const WORDPRESS_VERSION = "7.1.3";

const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), "..");
const cacheDir = path.join(root, ".cache");
const snapshotDir = path.join(cacheDir, `wordpress-${WORDPRESS_VERSION}`);

const require = createRequire(import.meta.url);
const cli = path.join(
  path.dirname(require.resolve("@wp-playground/cli/package.json")),
  "wp-playground.js",
);

function playground(args, options = {}) {
  return spawn(process.execPath, [cli, ...args], { stdio: "inherit", ...options });
}

function buildSnapshot() {
  console.log(`Building the WordPress ${WORDPRESS_VERSION} snapshot (first run only)...`);
  fs.mkdirSync(cacheDir, { recursive: true });
  // Build beside the final location and rename at the end, so an interrupted build can't leave a
  // half-written snapshot that later runs would trust.
  const work = fs.mkdtempSync(path.join(cacheDir, "build-"));
  try {
    const zip = path.join(work, "wordpress.zip");
    const build = spawnSync(
      process.execPath,
      [
        cli,
        "build-snapshot",
        "--wp",
        WORDPRESS_VERSION,
        "--site-url",
        SITE_URL,
        "--verbosity",
        "quiet",
        "--outfile",
        zip,
      ],
      { stdio: "inherit" },
    );
    if (build.status !== 0) throw new Error("wp-playground build-snapshot failed");

    const extracted = path.join(work, "site");
    fs.mkdirSync(extracted);
    // The archive stores absolute paths (/wordpress/...). unzip strips them with one warning per
    // file and exits 1 although it extracted everything, so judge success by the result instead.
    const unzip = spawnSync("unzip", ["-q", zip, "-d", extracted], { stdio: "ignore" });
    const extractedSite = path.join(extracted, "wordpress");
    if (unzip.error || !fs.existsSync(path.join(extractedSite, "wp-config.php"))) {
      throw new Error("could not unzip the snapshot (is `unzip` installed?)");
    }

    fs.renameSync(extractedSite, snapshotDir);
  } finally {
    fs.rmSync(work, { recursive: true, force: true });
  }
}

if (!fs.existsSync(snapshotDir)) buildSnapshot();

// COPYFILE_FICLONE makes this a copy-on-write clone where the filesystem supports it (APFS, btrfs,
// XFS), about 0.4s instead of 1.2s, and falls back to a plain copy elsewhere.
const siteRoot = fs.mkdtempSync(path.join(os.tmpdir(), "playwright-yadda-wp-"));
const site = path.join(siteRoot, "wordpress");
fs.cpSync(snapshotDir, site, { recursive: true, mode: fs.constants.COPYFILE_FICLONE });

const server = playground([
  "server",
  "--port",
  String(PORT),
  "--auto-mount",
  site,
  "--wordpress-install-mode",
  "do-not-attempt-installing",
]);

function cleanup() {
  fs.rmSync(siteRoot, { recursive: true, force: true });
}

for (const signal of ["SIGINT", "SIGTERM"]) {
  process.on(signal, () => server.kill(signal));
}
server.on("exit", (code, signal) => {
  cleanup();
  process.exit(code ?? (signal ? 1 : 0));
});
