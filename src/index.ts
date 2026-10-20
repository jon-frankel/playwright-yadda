/**
 * playwright-yadda
 * Dynamic step library loading for Playwright and Yadda BDD
 */

export const VERSION = "0.0.1";

export interface PlaywrightYaddaOptions {
  commonLibraryName?: string;
  defaultLibraries?: string[];
}

export function createPlaywrightYadda(options: PlaywrightYaddaOptions = {}) {
  const commonLibraryName = options.commonLibraryName ?? "common";
  const defaultLibraries = options.defaultLibraries ?? [commonLibraryName];

  return {
    commonLibraryName,
    defaultLibraries,
  };
}
