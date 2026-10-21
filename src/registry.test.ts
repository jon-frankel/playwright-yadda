import { describe, expect, it } from "vitest";
import Yadda from "yadda";
import {
  createLibraryRegistry,
  LibraryRegistryManager,
  resolveLibraries,
  UnknownLibraryError,
} from "./registry.js";

describe("registry", () => {
  const commonLib = new Yadda.Library();
  const authLib = new Yadda.Library();
  const catalogLib = new Yadda.Library();
  const checkoutLib = new Yadda.Library();

  const registryMap = {
    common: commonLib,
    auth: authLib,
    catalog: catalogLib,
    checkout: checkoutLib,
  };

  it("takes the keys from the libraries, not from defaultLibraries", () => {
    // Inferring the key type from defaultLibraries as well narrowed it to "wp-common" and rejected the
    // other keys at compile time (vitest doesn't typecheck, `pnpm typecheck` does).
    const registry = createLibraryRegistry(
      { "wp-common": commonLib, "wp-auth": authLib },
      { defaultLibraries: ["wp-common"] },
    );
    expect(registry.getAvailableKeys()).toEqual(["wp-common", "wp-auth"]);

    const manager = new LibraryRegistryManager(
      { "wp-common": commonLib, "wp-auth": authLib },
      { defaultLibraries: ["wp-common"] },
    );
    expect(manager.has("wp-auth")).toBe(true);
    expect(
      resolveLibraries({ "wp-common": commonLib, "wp-auth": authLib }, undefined, undefined, {
        defaultLibraries: ["wp-common"],
      }),
    ).toEqual([commonLib]);
  });

  it("should create a registry and retrieve available keys", () => {
    const registry = createLibraryRegistry(registryMap);
    expect(registry.getAvailableKeys()).toEqual(["common", "auth", "catalog", "checkout"]);
    expect(registry.has("auth")).toBe(true);
    expect(registry.has("AUTH")).toBe(true);
    expect(registry.get("auth")).toBe(authLib);
    expect(registry.get("unknown")).toBeUndefined();
  });

  it("should resolve default libraries when no annotations are provided", () => {
    const registry = createLibraryRegistry(registryMap);
    const resolved = registry.resolve({}, {});
    expect(resolved).toEqual([commonLib]);
  });

  it("should resolve libraries based on @Libraries annotations", () => {
    const registry = createLibraryRegistry(registryMap);
    const resolved = registry.resolve({ libraries: "auth" }, { libraries: "catalog, checkout" });
    expect(resolved).toEqual([commonLib, authLib, catalogLib, checkoutLib]);
  });

  it("should throw UnknownLibraryError when unknown library is requested", () => {
    const registry = createLibraryRegistry(registryMap);
    expect(() => registry.resolve({ libraries: "billing" }, {})).toThrow(UnknownLibraryError);

    let caughtError: UnknownLibraryError | undefined;
    try {
      registry.resolve({ libraries: "billing" }, {});
    } catch (err) {
      caughtError = err as UnknownLibraryError;
    }

    expect(caughtError).toBeInstanceOf(UnknownLibraryError);
    expect(caughtError?.requestedLibrary).toBe("billing");
    expect(caughtError?.availableLibraries).toEqual(["common", "auth", "catalog", "checkout"]);
    expect(caughtError?.message).toContain('Unknown step library "billing"');
  });

  it("should support standalone resolveLibraries helper function", () => {
    const resolved = resolveLibraries(registryMap, { libraries: "auth" }, {});
    expect(resolved).toEqual([commonLib, authLib]);
  });

  it("should support custom default libraries via options", () => {
    const registry = createLibraryRegistry(registryMap, {
      defaultLibraries: ["common", "auth"],
    });
    const resolved = registry.resolve({}, {});
    expect(resolved).toEqual([commonLib, authLib]);
  });
});
