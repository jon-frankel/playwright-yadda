// Installs a packed playwright-yadda tarball into an empty project and checks that it loads the way a
// consumer would load it: ESM import, CJS require, the ./reporter subpath, and the types.
//
// Usage: node scripts/smoke-test-package.mjs <path-to-tarball>
//
// This is the release check on the published shape of the package. The examples cover behavior.
import fs from "node:fs";
import os from "node:os";
import path from "node:path";
import { execFileSync } from "node:child_process";

const tarballArg = process.argv[2];
if (!tarballArg || !fs.existsSync(tarballArg)) {
  console.error("Usage: node scripts/smoke-test-package.mjs <path-to-tarball>");
  process.exit(1);
}
const tarball = path.resolve(tarballArg);

const dir = fs.mkdtempSync(path.join(os.tmpdir(), "playwright-yadda-smoke-"));
const run = (command, args) => execFileSync(command, args, { cwd: dir, stdio: "inherit" });
const write = (name, content) => fs.writeFileSync(path.join(dir, name), content);

try {
  write(
    "package.json",
    JSON.stringify({ name: "smoke", private: true, type: "module", version: "0.0.0" }),
  );
  run("npm", [
    "install",
    "--no-audit",
    "--no-fund",
    tarball,
    "@playwright/test@latest",
    "yadda@3",
    "typescript@latest",
    "@types/node",
    "@types/yadda",
  ]);

  write(
    "check.mjs",
    `import * as esm from "playwright-yadda";
import reporterEsm from "playwright-yadda/reporter";
import { createRequire } from "node:module";
const require = createRequire(import.meta.url);
const cjs = require("playwright-yadda");
const reporterCjs = require("playwright-yadda/reporter");
const pkg = require("playwright-yadda/package.json");

const fail = (message) => { console.error("FAIL: " + message); process.exit(1); };
if (Object.keys(esm).length === 0) fail("the ESM entry exports nothing");
for (const key of Object.keys(esm)) {
  if (typeof cjs[key] !== typeof esm[key]) fail("CJS and ESM differ on " + key);
}
if (!reporterEsm || !reporterCjs) fail("the ./reporter entry has no default export");
console.log("playwright-yadda@" + pkg.version + " exports: " + Object.keys(esm).join(", "));
`,
  );
  run("node", ["check.mjs"]);

  write(
    "types.ts",
    `import * as yadda from "playwright-yadda";
import Reporter from "playwright-yadda/reporter";
export const exported: typeof yadda = yadda;
export const reporter: typeof Reporter = Reporter;
`,
  );
  write(
    "tsconfig.json",
    JSON.stringify({
      compilerOptions: {
        module: "nodenext",
        moduleResolution: "nodenext",
        target: "es2022",
        strict: true,
        noEmit: true,
        skipLibCheck: false,
        types: ["node"],
      },
      files: ["types.ts"],
    }),
  );
  run("npx", ["tsc", "-p", "tsconfig.json"]);
  console.log("✅ Smoke test passed");
} finally {
  fs.rmSync(dir, { recursive: true, force: true });
}
