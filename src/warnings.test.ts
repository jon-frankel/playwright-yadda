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
});
