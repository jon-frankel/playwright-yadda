// Regenerates src/main/resources/yadda/vocabularies.json: the words each of yadda's localisations gives
// Feature, Rule, Scenario, Background, Examples and the step keywords, as the regex sources it matches
// them with. The plugin reads its keywords from this file, so rerun it after upgrading yadda:
//
//   node ide/jetbrains/scripts/generate-vocabularies.mjs          # write the file
//   node ide/jetbrains/scripts/generate-vocabularies.mjs --check  # fail if the file is out of date
import { createRequire } from "node:module";
import { readFileSync, writeFileSync } from "node:fs";
import { dirname, resolve } from "node:path";
import { fileURLToPath } from "node:url";

const here = dirname(fileURLToPath(import.meta.url));
const require = createRequire(resolve(here, "../../../package.json"));
const Yadda = require("yadda");
const target = resolve(here, "../src/main/resources/yadda/vocabularies.json");

const KEYS = ["feature", "rule", "scenario", "background", "examples", "given", "when", "then"];

// A language whose words are not a valid regex can't be used to parse anything: building a parser for it
// throws. Yadda 3.2's Dutch scenario word is one (`[Gg|eval`), so it is left out.
function usable(words) {
  for (const key of KEYS) {
    if (words[key] === null) continue;
    try {
      new RegExp(words[key]);
    } catch (e) {
      console.warn(`${words.name}: left out, its ${key} word is not a regex (${e.message})`);
      return false;
    }
  }
  return true;
}

const languages = Object.entries(Yadda.localisation)
  // `default` is English again and `Language` is the constructor.
  .filter(([name, value]) => value?.is_language && name !== "default")
  .map(([name, language]) => {
    const words = { name };
    for (const key of KEYS) words[key] = language.supports(key) ? language.translate(key) : null;
    return words;
  })
  .filter(usable);

const json = JSON.stringify(languages, null, 2) + "\n";

if (process.argv.includes("--check")) {
  let current = "";
  try {
    current = readFileSync(target, "utf8");
  } catch {}
  if (current !== json) {
    console.error(
      "vocabularies.json is out of date: run node ide/jetbrains/scripts/generate-vocabularies.mjs",
    );
    process.exit(1);
  }
  console.log(`vocabularies.json is up to date (${languages.length} languages)`);
} else {
  writeFileSync(target, json);
  console.log(`vocabularies.json: ${languages.length} languages`);
}
