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
  const forceAll =
    args.includes("--all") ||
    args.includes("-a") ||
    args.includes("--force-bump") ||
    args.includes("-b");
  const skipBuild = args.includes("--skip-build");
  const skipExamples = args.includes("--skip-examples");
  const registryFlagIndex = args.indexOf("--registry");
  const customRegistry = registryFlagIndex !== -1 ? args[registryFlagIndex + 1] : undefined;
  return { isDryRun, forceAll, skipBuild, skipExamples, customRegistry };
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
    try {
      execSync(`pnpm add "${packageName}@${packageVersion}"`, {
        cwd: app.path,
        stdio: "inherit",
      });
    } catch {
      execSync("pnpm install", { cwd: app.path, stdio: "inherit" });
    }
  } else {
    execSync("pnpm install", { cwd: app.path, stdio: "inherit" });
  }
}

function syncExampleApps(packageName, packageVersion, isDryRun) {
  const exampleApps = getExampleAppDirectories();
  if (exampleApps.length === 0) return;

  console.log("\n📥 Updating example applications with new versions from Verdaccio:");
  for (const app of exampleApps) {
    console.log(`  • Updating ${app.name} (${app.path})...`);
    if (isDryRun) {
      console.log(`    [Dry Run] Would update ${app.name}.`);
      continue;
    }
    try {
      updateExampleApp(app, packageName, packageVersion);
      console.log(`    ✅ ${app.name} updated successfully.`);
    } catch (error) {
      console.warn(`    ⚠️ Failed to update ${app.name}: ${error.message}`);
    }
  }
}

async function main() {
  const { isDryRun, forceAll, skipBuild, skipExamples, customRegistry } = parseCommandLineArgs();
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

  let needsPublish = false;
  let needsBump = false;

  if (!isAlreadyPublished) {
    needsPublish = true;
    console.log(`📦 ${packageName}: ${packageVersion} (version not yet in registry)`);
  } else if (forceAll) {
    needsPublish = true;
    needsBump = true;
    console.log(`📦 ${packageName}: ${packageVersion} (forced update --all)`);
  } else {
    console.log(`📦 ${packageName}: ${packageVersion} is already published.`);
    const answer = forceAll;
    if (!answer) {
      console.log("   Pass --all / -a to force bump patch version and publish.");
    }
  }

  if (needsBump) {
    let nextVersion = incrementPatchVersion(packageVersion);
    while (publishedVersions.includes(nextVersion)) {
      nextVersion = incrementPatchVersion(nextVersion);
    }
    console.log(`  ➔ Bumping version: ${packageVersion} ➔ ${nextVersion}`);
    if (!isDryRun) {
      writeUpdatedPackageVersion(pkgJsonPath, nextVersion);
    }
    packageVersion = nextVersion;
  }

  if (!needsPublish) {
    if (!skipExamples) {
      syncExampleApps(packageName, packageVersion, isDryRun);
    }
    console.log("\nDone!");
    return;
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

  if (!skipExamples) {
    syncExampleApps(packageName, packageVersion, isDryRun);
  }

  console.log(`\n🎉 Successfully published ${packageName}@${packageVersion}!`);
}

main().catch((err) => {
  console.error(err);
  process.exit(1);
});
