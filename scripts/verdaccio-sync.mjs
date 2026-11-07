import fs from "node:fs";
import path from "node:path";
import { execSync } from "node:child_process";
import { fileURLToPath } from "node:url";

const __dirname = path.dirname(fileURLToPath(import.meta.url));
const rootDir = path.resolve(__dirname, "..");
const examplesDir = path.join(rootDir, "examples");

function parseCommandLineArgs() {
  const args = process.argv.slice(2);
  const isDryRun = args.includes("--dry-run") || args.includes("-n");
  // Bumping is the default now. --all, -a, --force-bump and -b are accepted and mean nothing.
  // With --no-bump, the version in package.json is published if not already present in the registry.
  // If that version is already published, the script exits with an error instead of bumping.
  const noBump = args.includes("--no-bump");
  const skipBuild = args.includes("--skip-build");
  const skipExamples = args.includes("--skip-examples");
  const registryFlagIndex = args.indexOf("--registry");
  const customRegistry = registryFlagIndex !== -1 ? args[registryFlagIndex + 1] : undefined;
  return { isDryRun, noBump, skipBuild, skipExamples, customRegistry };
}

function loadEnvConfiguration(customRegistry) {
  const envPath = path.join(rootDir, ".env");
  const defaultEnvPath = path.join(rootDir, ".env.default");

  const config = {
    registryUrl: "http://localhost:4873",
    authToken: "",
  };

  const readEnv = (filePath) => {
    if (!fs.existsSync(filePath)) return;
    const lines = fs.readFileSync(filePath, "utf8").split("\n");
    for (const line of lines) {
      const trimmed = line.trim();
      if (!trimmed || trimmed.startsWith("#")) continue;
      const [key, ...rest] = trimmed.split("=");
      const value = rest.join("=").replace(/^['"]|['"]$/g, "");
      if (key === "NPM_REGISTRY") config.registryUrl = value;
      if (key === "NPM_TOKEN") config.authToken = value;
    }
  };

  readEnv(defaultEnvPath);
  readEnv(envPath);

  if (process.env.NPM_TOKEN && !config.authToken) {
    config.authToken = process.env.NPM_TOKEN;
  }
  if (customRegistry) {
    config.registryUrl = customRegistry;
  }

  return config;
}

async function verifyVerdaccioHealth(registryUrl) {
  try {
    const response = await fetch(registryUrl, { signal: AbortSignal.timeout(3000) });
    if (!response.ok && response.status !== 404) {
      throw new Error(`Registry responded with HTTP ${response.status}`);
    }
  } catch {
    console.error(`\n❌ Verdaccio is not accessible at ${registryUrl}`);
    console.error("   Run 'pnpm run verdaccio:start' to start the registry container.\n");
    process.exit(1);
  }
}

async function fetchRegistryPackageData(registryUrl, packageName, authToken) {
  const headers = {};
  if (authToken) {
    headers.Authorization = `Bearer ${authToken}`;
  }

  const packageUrl = `${registryUrl}/${encodeURIComponent(packageName)}`;
  try {
    const response = await fetch(packageUrl, { headers, signal: AbortSignal.timeout(5000) });
    if (response.status === 404) {
      return null;
    }
    if (!response.ok) {
      throw new Error(`HTTP ${response.status} from ${packageUrl}`);
    }
    return await response.json();
  } catch (error) {
    console.warn(`⚠️ Could not query registry data for ${packageName}: ${error.message}`);
    return null;
  }
}

function incrementPatchVersion(version) {
  const parts = version.split(".");
  const patch = parseInt(parts[2] || "0", 10) + 1;
  return `${parts[0] || "0"}.${parts[1] || "0"}.${patch}`;
}

function writeUpdatedPackageVersion(pkgJsonPath, newVersion) {
  const content = fs.readFileSync(pkgJsonPath, "utf8");
  const updatedContent = content.replace(/"version":\s*"[^"]+"/, `"version": "${newVersion}"`);
  fs.writeFileSync(pkgJsonPath, updatedContent, "utf8");
}

function getExampleAppDirectories() {
  if (!fs.existsSync(examplesDir)) return [];
  const entries = fs.readdirSync(examplesDir, { withFileTypes: true });
  const appDirs = [];

  for (const entry of entries) {
    if (!entry.isDirectory()) continue;
    const examplePath = path.join(examplesDir, entry.name);
    const pkgJsonPath = path.join(examplePath, "package.json");
    if (fs.existsSync(pkgJsonPath)) {
      appDirs.push({ name: entry.name, path: examplePath });
    }
  }

  return appDirs;
}

function updateExampleApp(app, packageName, packageVersion) {
  const pkgJsonPath = path.join(app.path, "package.json");
  const pkg = JSON.parse(fs.readFileSync(pkgJsonPath, "utf8"));
  const deps = { ...pkg.dependencies, ...pkg.devDependencies };

  if (deps[packageName]) {
    // Pins the exact version in package.json, the lockfile and the release-age exclusions
    execSync(`pnpm add "${packageName}@${packageVersion}"`, { cwd: app.path, stdio: "inherit" });
    const installed = path.join(app.path, "node_modules", packageName, "package.json");
    const installedVersion = fs.existsSync(installed)
      ? JSON.parse(fs.readFileSync(installed, "utf8")).version
      : undefined;
    if (installedVersion !== packageVersion) {
      throw new Error(`node_modules has ${installedVersion ?? "nothing"}, not ${packageVersion}`);
    }
  } else {
    execSync("pnpm install", { cwd: app.path, stdio: "inherit" });
  }
}

/** @returns the names of the examples that could not be updated */
function syncExampleApps(packageName, packageVersion, isDryRun) {
  const exampleApps = getExampleAppDirectories();
  if (exampleApps.length === 0) return [];

  const failed = [];
  console.log("\n📥 Updating example applications with new versions from Verdaccio:");
  for (const app of exampleApps) {
    console.log(`  • Updating ${app.name} (${app.path})...`);
    if (isDryRun) {
      console.log(`    [Dry Run] Would update ${app.name}.`);
      continue;
    }
    try {
      updateExampleApp(app, packageName, packageVersion);
      console.log(`    ✅ ${app.name} is on ${packageVersion}.`);
    } catch (error) {
      failed.push(app.name);
      console.error(`    ❌ Failed to update ${app.name}: ${error.message}`);
    }
  }
  return failed;
}

async function main() {
  const { isDryRun, noBump, skipBuild, skipExamples, customRegistry } = parseCommandLineArgs();
  console.log("🚀 Starting Verdaccio package sync workflow...");

  const { registryUrl, authToken } = loadEnvConfiguration(customRegistry);
  if (!authToken) {
    console.error("\n❌ NPM_TOKEN is not configured in .env or environment.");
    console.error("   Run 'pnpm run verdaccio:create-token' to create an auth token.\n");
    process.exit(1);
  }

  await verifyVerdaccioHealth(registryUrl);
  console.log(`✅ Connected to Verdaccio registry: ${registryUrl}`);

  const pkgJsonPath = path.join(rootDir, "package.json");
  const pkgJson = JSON.parse(fs.readFileSync(pkgJsonPath, "utf8"));
  const packageName = pkgJson.name;
  let packageVersion = pkgJson.version;

  const registryData = await fetchRegistryPackageData(registryUrl, packageName, authToken);
  const publishedVersions = Object.keys(registryData?.versions || {});
  const isAlreadyPublished = publishedVersions.includes(packageVersion);

  // By default, every run bumps the patch version past anything already in the registry.
  // With --no-bump, publish the current package.json version if free; fail if already published.
  if (noBump && isAlreadyPublished) {
    console.error(
      `\n❌ Version ${packageVersion} is already published to registry ${registryUrl}.`,
    );
    console.error(`   Exiting because --no-bump was requested.\n`);
    process.exit(1);
  }

  const needsBump = !noBump;
  if (needsBump) {
    let nextVersion = incrementPatchVersion(packageVersion);
    while (publishedVersions.includes(nextVersion)) {
      nextVersion = incrementPatchVersion(nextVersion);
    }
    console.log(`📦 ${packageName}: bumping ${packageVersion} ➔ ${nextVersion}`);
    if (!isDryRun) {
      writeUpdatedPackageVersion(pkgJsonPath, nextVersion);
    }
    packageVersion = nextVersion;
  } else {
    console.log(`📦 ${packageName}: ${packageVersion} (not yet in the registry)`);
  }

  if (isDryRun) {
    console.log(
      `\n🔍 [Dry Run] Would build, publish ${packageName}@${packageVersion}, and update examples.`,
    );
    return;
  }

  if (!skipBuild) {
    console.log("\n🔨 Building package...");
    execSync("pnpm run build", { cwd: rootDir, stdio: "inherit" });
  }

  const urlObj = new URL(registryUrl);
  const registryHost = `//${urlObj.host}`;
  console.log(`\n📤 Publishing ${packageName}@${packageVersion} to Verdaccio:`);
  const publishCommand = [
    `pnpm publish`,
    `--no-git-checks`,
    `--access public`,
    `--registry "${registryUrl}"`,
  ].join(" ");

  const authEnvironment = { [`npm_config_${registryHost}/:_authToken`]: authToken };
  execSync(publishCommand, {
    cwd: rootDir,
    stdio: "inherit",
    env: { ...process.env, ...authEnvironment },
  });

  const failed = skipExamples ? [] : syncExampleApps(packageName, packageVersion, isDryRun);

  console.log(`\n🎉 Published ${packageName}@${packageVersion}.`);
  if (failed.length > 0) {
    console.error(`\n❌ These examples are not on ${packageVersion}: ${failed.join(", ")}`);
    process.exit(1);
  }
}

main().catch((err) => {
  console.error(err);
  process.exit(1);
});
