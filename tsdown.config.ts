import { defineConfig } from "tsdown";

export default defineConfig({
  entry: ["src/index.ts", "src/reporter.ts"],
  format: ["esm", "cjs"],
  dts: true,
  clean: true,
});
