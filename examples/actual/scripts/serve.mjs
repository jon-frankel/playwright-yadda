// Serves Actual Budget's own dev server (http://localhost:3001) for the Playwright tests.
//
// The app is not vendored here. This fetches one pinned commit of actualbudget/actual into .cache/
// on first use and installs its dependencies there (a few minutes, several hundred MB), then runs
// `yarn start` from it, which is what Actual's own Playwright config runs. Delete .cache/ to start over.
//
//   node scripts/serve.mjs --prepare   fetch and install only
//   node scripts/serve.mjs             prepare if needed, then serve
import { spawn, spawnSync } from "node:child_process";
import fs from "node:fs";
import path from "node:path";
import { fileURLToPath } from "node:url";

const REPOSITORY = "https://github.com/actualbudget/actual.git";
// Pinned so the app, and so the scenarios, don't change under the feature files.
const COMMIT = "d2dae07f5630dc191aa74aba6b88e243cce39193";

const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), "..");
const appDir = path.join(root, ".cache", `actual-${COMMIT.slice(0, 8)}`);
const installedMarker = path.join(appDir, ".example-installed");

// Actual pins Yarn 4 through its packageManager field; corepack runs exactly that without installing
// anything globally.
const yarnEnv = { ...process.env, COREPACK_ENABLE_DOWNLOAD_PROMPT: "0" };

function run(command, args, options = {}) {
  const result = spawnSync(command, args, { stdio: "inherit", env: yarnEnv, ...options });
  if (result.status !== 0) throw new Error(`${command} ${args.join(" ")} failed`);
}

function prepare() {
  if (fs.existsSync(installedMarker)) return;

  if (!fs.existsSync(path.join(appDir, ".git"))) {
    console.log(`Fetching Actual ${COMMIT.slice(0, 8)} (first run only)...`);
    fs.rmSync(appDir, { recursive: true, force: true });
    fs.mkdirSync(appDir, { recursive: true });
    run("git", ["init", "-q"], { cwd: appDir });
    run("git", ["fetch", "-q", "--depth", "1", REPOSITORY, COMMIT], { cwd: appDir });
    run("git", ["checkout", "-q", "FETCH_HEAD"], { cwd: appDir });
  }

  console.log("Installing Actual's dependencies (first run only)...");
  run("corepack", ["yarn", "install", "--immutable"], { cwd: appDir });
  // Written last, so an interrupted install is redone instead of trusted.
  fs.writeFileSync(installedMarker, `${COMMIT}\n`);
}

prepare();
if (process.argv.includes("--prepare")) process.exit(0);

// `yarn start` fans out into several processes. They stay in this process's group, because Playwright
// stops a web server by killing the group it started: processes that left it would survive the run
// and keep Playwright's output pipes open, so its teardown would never finish.
const server = spawn("corepack", ["yarn", "start"], {
  cwd: appDir,
  stdio: "inherit",
  env: yarnEnv,
});

function stop(signal) {
  try {
    // Reaches the whole group when this process leads it, e.g. when started by Playwright.
    process.kill(-process.pid, signal);
  } catch {
    server.kill(signal);
  }
}

for (const signal of ["SIGINT", "SIGTERM"]) {
  process.on(signal, () => stop(signal));
}
server.on("exit", (code, signal) => process.exit(code ?? (signal ? 1 : 0)));
