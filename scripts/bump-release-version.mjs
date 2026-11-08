import fs from "node:fs";
import { execSync } from "node:child_process";

const SEMVER_RANK = { none: 0, patch: 1, minor: 2, major: 3 };

function readPackageJson() {
  return JSON.parse(fs.readFileSync("package.json", "utf8"));
}

function hasPendingChangesets() {
  if (!fs.existsSync(".changeset")) return false;
  return fs.readdirSync(".changeset").some((file) => file.endsWith(".md") && file !== "README.md");
}

function consumePendingChangesets() {
  if (hasPendingChangesets()) {
    execSync("pnpm exec changeset version", { stdio: "inherit" });
    return;
  }
  console.log("No unreleased changesets found in .changeset; skipping changeset version.");
}

function detectSemverBump(previousVersion, nextVersion) {
  const [prevMaj, prevMin, prevPatch] = previousVersion.split(".").map(Number);
  const [nextMaj, nextMin, nextPatch] = nextVersion.split(".").map(Number);

  if (nextMaj > prevMaj) return "major";
  if (nextMin > prevMin) return "minor";
  if (nextPatch > prevPatch) return "patch";
  return "none";
}

function executeVersionBump(bumpType, baselineVersion) {
  if (baselineVersion) {
    const pkg = readPackageJson();
    fs.writeFileSync(
      "package.json",
      JSON.stringify({ ...pkg, version: baselineVersion }, null, 2) + "\n",
    );
  }
  execSync(`npm version ${bumpType} --no-git-tag-version`, { stdio: "inherit" });
}

function reconcileRequestedBump(requestedBump, changesetBump, baselineVersion) {
  if (requestedBump === "auto") {
    if (changesetBump === "none") {
      console.log("No changesets consumed; bumping patch version.");
      executeVersionBump("patch");
    }
    return;
  }

  const requestedRank = SEMVER_RANK[requestedBump] || 0;
  const changesetRank = SEMVER_RANK[changesetBump] || 0;

  if (requestedRank > changesetRank) {
    console.log(
      `Requested bump "${requestedBump}" requires upgrading beyond changeset bump "${changesetBump}".`,
    );
    executeVersionBump(requestedBump, baselineVersion);
  }
}

function updateChangelogHeading(changelog, finalVersion) {
  return changelog.replace(/^##\s+\d+\.\d+\.\d+/m, `## ${finalVersion}`);
}

function prependChangelogEntry(changelog, finalVersion) {
  const newSection = `## ${finalVersion}\n\n### Patch Changes\n\n- Release v${finalVersion}\n\n`;
  return changelog.replace(/^(# [^\n]*\n+)/, `$1${newSection}`);
}

function updateChangelog(finalVersion, versionAfterChangeset, changesetBump) {
  if (!fs.existsSync("CHANGELOG.md")) return;

  const changelog = fs.readFileSync("CHANGELOG.md", "utf8");

  if (changesetBump !== "none" && finalVersion !== versionAfterChangeset) {
    console.log(`Syncing CHANGELOG.md top heading to ${finalVersion}`);
    fs.writeFileSync("CHANGELOG.md", updateChangelogHeading(changelog, finalVersion), "utf8");
    return;
  }

  if (changesetBump === "none" && !changelog.includes(`## ${finalVersion}`)) {
    console.log(`Adding entry for ${finalVersion} to CHANGELOG.md`);
    fs.writeFileSync("CHANGELOG.md", prependChangelogEntry(changelog, finalVersion), "utf8");
  }
}

function main() {
  const requestedBump = process.argv[2] || "auto";
  const initialVersion = readPackageJson().version;

  consumePendingChangesets();

  const versionAfterChangesets = readPackageJson().version;
  const changesetBump = detectSemverBump(initialVersion, versionAfterChangesets);

  reconcileRequestedBump(requestedBump, changesetBump, initialVersion);

  const finalVersion = readPackageJson().version;
  updateChangelog(finalVersion, versionAfterChangesets, changesetBump);

  console.log(`Candidate version determined: ${finalVersion}`);
}

main();
