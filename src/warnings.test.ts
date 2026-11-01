import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { REPORTER_FLAG, resetWarningsForTests, warnOnce } from "./warnings.js";

const flags = globalThis as Record<symbol, unknown>;

beforeEach(() => resetWarningsForTests());
afterEach(() => {
  vi.restoreAllMocks();
  vi.unstubAllEnvs();
  delete flags[REPORTER_FLAG];
});

describe("warnOnce", () => {
  it("prints a message once, however often it is asked to", () => {
    const warn = vi.spyOn(console, "warn").mockImplementation(() => {});
    warnOnce("a problem");
    warnOnce("a problem");
    warnOnce("another problem");
    expect(warn.mock.calls.map((call) => call[0])).toEqual([
      "playwright-yadda: a problem",
      "playwright-yadda: another problem",
    ]);
  });

  it("stays quiet in a Playwright worker, because the main process has printed it", () => {
    const warn = vi.spyOn(console, "warn").mockImplementation(() => {});
    vi.stubEnv("TEST_WORKER_INDEX", "0");
    warnOnce("a problem");
    expect(warn).not.toHaveBeenCalled();
  });

  it("stays quiet when the yadda reporter is in use, which prints it for the scenarios that run", () => {
    const warn = vi.spyOn(console, "warn").mockImplementation(() => {});
    flags[REPORTER_FLAG] = true;
    warnOnce("a problem");
    expect(warn).not.toHaveBeenCalled();
  });

  it("still prints a warning no scenario carries, although the yadda reporter is in use", () => {
    const warn = vi.spyOn(console, "warn").mockImplementation(() => {});
    flags[REPORTER_FLAG] = true;
    warnOnce("a problem", false);
    warnOnce("a problem", false);
    expect(warn).toHaveBeenCalledOnce();
  });

  it("still prints a warning that was held back for the reporter, once nothing carries it", () => {
    const warn = vi.spyOn(console, "warn").mockImplementation(() => {});
    flags[REPORTER_FLAG] = true;
    warnOnce("a problem");
    warnOnce("a problem", false);
    expect(warn).toHaveBeenCalledOnce();
  });
});

describe("warnOnce with a Playwright older than 1.42", () => {
  // Such a Playwright takes no annotations when a test is declared, so the reporter cannot be
  // handed the warning, and the loader has to print it whichever reporter is in use
  afterEach(() => {
    vi.doUnmock("node:module");
    vi.resetModules();
  });

  const loadWith = async (version: string) => {
    vi.resetModules();
    vi.doMock("node:module", () => ({ createRequire: () => () => ({ version }) }));
    return import("./warnings.js");
  };

  it("does not support test details, so the loader prints with the reporter in use", async () => {
    const warn = vi.spyOn(console, "warn").mockImplementation(() => {});
    const warnings = await loadWith("1.41.2");
    expect(warnings.supportsTestDetails()).toBe(false);
    flags[REPORTER_FLAG] = true;
    warnings.warnOnce("a problem");
    expect(warn).toHaveBeenCalledOnce();
  });

  it("supports them from 1.42, and then leaves the printing to the reporter", async () => {
    const warn = vi.spyOn(console, "warn").mockImplementation(() => {});
    const warnings = await loadWith("1.42.0");
    expect(warnings.supportsTestDetails()).toBe(true);
    flags[REPORTER_FLAG] = true;
    warnings.warnOnce("a problem");
    expect(warn).not.toHaveBeenCalled();
  });
});
