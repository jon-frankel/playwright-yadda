import { describe, expect, it } from "vitest";
import { createPlaywrightYadda, VERSION } from "./index.js";

describe("playwright-yadda", () => {
  it("should have correct version defined", () => {
    expect(VERSION).toBe("0.0.1");
  });

  it("should initialize default options", () => {
    const instance = createPlaywrightYadda();
    expect(instance.commonLibraryName).toBe("common");
    expect(instance.defaultLibraries).toEqual(["common"]);
  });

  it("should accept custom options", () => {
    const instance = createPlaywrightYadda({
      commonLibraryName: "base",
      defaultLibraries: ["base", "auth"],
    });
    expect(instance.commonLibraryName).toBe("base");
    expect(instance.defaultLibraries).toEqual(["base", "auth"]);
  });
});
