import { createPlaywrightYadda, VERSION } from "playwright-yadda";

console.log(`Running example with playwright-yadda v${VERSION}`);
const instance = createPlaywrightYadda({
  commonLibraryName: "common",
  defaultLibraries: ["common", "auth"],
});

console.log("Playwright-Yadda initialized with:", instance);
