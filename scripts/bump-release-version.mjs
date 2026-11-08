import fs from "node:fs";
import { execSync } from "node:child_process";

const requested = process.argv[2] || "auto";
const pkgBefore = JSON.parse(fs.readFileSync("package.json", "utf8"));
const oldVersion = pkgBefore.version;

// Check if any pending changeset markdown files exist in .changeset
const changesetFiles = fs.existsSync(".changeset")
  ? fs.readdirSync(".changeset").filter((f) => f.endsWith(".md") && f !== "README.md")
  : [];

if (changesetFiles.length > 0) {
  execSync("pnpm exec changeset version", { stdio: "inherit" });
} else {
  console.log("No unreleased changesets found in .changeset; skipping changeset version.");
}

const pkgAfter = JSON.parse(fs.readFileSync("package.json", "utf8"));
let currentVersion = pkgAfter.version;

const [oldMaj, oldMin, oldPatch] = oldVersion.split(".").map(Number);
const [newMaj, newMin, newPatch] = currentVersion.split(".").map(Number);

let changesetBump = "none";
if (newMaj > oldMaj) changesetBump = "major";
else if (newMin > oldMin) changesetBump = "minor";
else if (newPatch > oldPatch) changesetBump = "patch";

const semverOrder = { patch: 1, minor: 2, major: 3 };

if (requested === "auto") {
  if (changesetBump === "none") {
    console.log("No changesets consumed; bumping patch version.");
    execSync("npm version patch --no-git-tag-version", { stdio: "inherit" });
  }
} else if ((semverOrder[requested] || 0) > (semverOrder[changesetBump] || 0)) {
  console.log(
    `Requested bump "${requested}" requires upgrading beyond changeset bump "${changesetBump}".`,
  );
  // Reset version to oldVersion so npm version increments from baseline
  fs.writeFileSync(
    "package.json",
    JSON.stringify({ ...pkgAfter, version: oldVersion }, null, 2) + "\n",
  );
  execSync(`npm version ${requested} --no-git-tag-version`, { stdio: "inherit" });
}

const finalVersion = JSON.parse(fs.readFileSync("package.json", "utf8")).version;

if (fs.existsSync("CHANGELOG.md")) {
  const changelog = fs.readFileSync("CHANGELOG.md", "utf8");
  if (changesetBump !== "none" && finalVersion !== currentVersion) {
    console.log(`Syncing CHANGELOG.md top heading to ${finalVersion}`);
    const updated = changelog.replace(/^##\s+\d+\.\d+\.\d+/m, `## ${finalVersion}`);
    fs.writeFileSync("CHANGELOG.md", updated, "utf8");
  } else if (changesetBump === "none" && !changelog.includes(`## ${finalVersion}`)) {
    console.log(`Adding entry for ${finalVersion} to CHANGELOG.md`);
    const newSection = `## ${finalVersion}\n\n### Patch Changes\n\n- Release v${finalVersion}\n\n`;
    fs.writeFileSync("CHANGELOG.md", changelog.replace(/^(# [^\n]*\n+)/, "$1" + newSection));
  }
}

console.log(`Candidate version determined: ${finalVersion}`);
