// Builds and packs the library, then installs that tarball into the examples under examples/.
//
// Usage: node scripts/examples-install.mjs [--skip-build] [example ...]
//
// The examples depend on "file:../../.pack/playwright-yadda.tgz", so they run the same files a
// published install would get (the "files" list, "exports" and publishConfig apply), without a
// registry. The tarball changes with every build, so the script puts each example's lockfile back
// after installing; otherwise every library change would show up as a lockfile diff.
import fs from "node:fs";
import path from "node:path";
import { execFileSync } from "node:child_process";
import { fileURLToPath } from "node:url";

const rootDir = path.resolve(path.dirname(fileURLToPath(import.meta.url)), "..");
const examplesDir = path.join(rootDir, "examples");
const packDir = path.join(rootDir, ".pack");
const tarballName = "playwright-yadda.tgz";

const args = process.argv.slice(2);
const skipBuild = args.includes("--skip-build");
const requested = args.filter((arg) => !arg.startsWith("-"));

function run(command, commandArgs, cwd) {
  execFileSync(command, commandArgs, { cwd, stdio: "inherit" });
}

function listExamples() {
  const all = fs
    .readdirSync(examplesDir, { withFileTypes: true })
    .filter((entry) => entry.isDirectory())
    .map((entry) => entry.name)
    .filter((name) => fs.existsSync(path.join(examplesDir, name, "package.json")));
  const unknown = requested.filter((name) => !all.includes(name));
  if (unknown.length > 0) {
    throw new Error(`No such example: ${unknown.join(", ")}. Available: ${all.join(", ")}`);
  }
  return requested.length > 0 ? requested : all;
}

function packLibrary() {
  if (!skipBuild) run("pnpm", ["run", "build"], rootDir);
  fs.rmSync(packDir, { recursive: true, force: true });
  fs.mkdirSync(packDir, { recursive: true });
  run("pnpm", ["pack", "--pack-destination", packDir], rootDir);
  const [packed] = fs.readdirSync(packDir).filter((file) => file.endsWith(".tgz"));
  if (!packed) throw new Error(`pnpm pack wrote no tarball to ${packDir}`);
  fs.renameSync(path.join(packDir, packed), path.join(packDir, tarballName));
}

// The tarball changes with every build, so an integrity hash for it in a lockfile is never right for
// long, and pnpm fails the install on a mismatch. The lockfile is kept without one.
function withoutTarballIntegrity(lockfile) {
  return lockfile
    .split("\n")
    .map((line) =>
      line.includes("tarball: file:") ? line.replace(/integrity: sha512-[^,}]*,?\s*/, "") : line,
    )
    .join("\n");
}

function installExample(name) {
  const exampleDir = path.join(examplesDir, name);
  const lockfile = path.join(exampleDir, "pnpm-lock.yaml");
  const savedLockfile = fs.existsSync(lockfile) ? fs.readFileSync(lockfile, "utf8") : undefined;
  try {
    if (savedLockfile) {
      fs.writeFileSync(lockfile, withoutTarballIntegrity(savedLockfile));
    }
    run("pnpm", ["install", "--no-frozen-lockfile"], exampleDir);
  } finally {
    // Put the lockfile back so a library change doesn't show up as a lockfile diff.
    if (savedLockfile) fs.writeFileSync(lockfile, withoutTarballIntegrity(savedLockfile));
  }
  const installed = JSON.parse(
    fs.readFileSync(path.join(exampleDir, "node_modules/playwright-yadda/package.json"), "utf8"),
  );
  console.log(`✅ examples/${name} has playwright-yadda@${installed.version} from ${tarballName}`);
}

const examples = listExamples();
packLibrary();
for (const name of examples) installExample(name);
