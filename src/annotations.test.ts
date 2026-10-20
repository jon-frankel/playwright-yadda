import { describe, expect, it } from "vitest";
import {
  extractLibraryNames,
  getAnnotationValue,
  isScenarioOnly,
  isScenarioPending,
  resolveLibraryNames,
} from "./annotations.js";

describe("annotations", () => {
  describe("getAnnotationValue", () => {
    it("should find values case-insensitively", () => {
      expect(getAnnotationValue({ Libraries: "auth, catalog" }, "libraries")).toBe("auth, catalog");
      expect(getAnnotationValue({ libraries: "auth" }, "LIBRARIES")).toBe("auth");
      expect(getAnnotationValue({ Pending: true }, "pending")).toBe(true);
    });

    it("should return undefined for missing annotations", () => {
      expect(getAnnotationValue(undefined, "libraries")).toBeUndefined();
      expect(getAnnotationValue({}, "libraries")).toBeUndefined();
    });
  });

  describe("extractLibraryNames", () => {
    it("should parse comma-separated library strings", () => {
      expect(extractLibraryNames({ libraries: "auth, catalog, checkout" })).toEqual([
        "auth",
        "catalog",
        "checkout",
      ]);
    });

    it("should trim whitespace and lowercase names", () => {
      expect(extractLibraryNames({ libraries: "  Auth , CATALOG  , Checkout  " })).toEqual([
        "auth",
        "catalog",
        "checkout",
      ]);
    });

    it("should ignore empty values", () => {
      expect(extractLibraryNames({ libraries: "auth,, ,catalog" })).toEqual(["auth", "catalog"]);
    });

    it("should return empty array for non-string or missing values", () => {
      expect(extractLibraryNames({})).toEqual([]);
      expect(extractLibraryNames({ libraries: true })).toEqual([]);
      expect(extractLibraryNames(undefined)).toEqual([]);
    });
  });

  describe("resolveLibraryNames", () => {
    it("should include default libraries even when no annotations exist", () => {
      expect(resolveLibraryNames({}, {})).toEqual(["common"]);
    });

    it("should support custom default libraries", () => {
      expect(resolveLibraryNames({}, {}, { defaultLibraries: ["base", "setup"] })).toEqual([
        "base",
        "setup",
      ]);
    });

    it("should merge feature and scenario libraries by default", () => {
      const feature = { libraries: "auth, catalog" };
      const scenario = { libraries: "checkout" };
      expect(resolveLibraryNames(feature, scenario)).toEqual([
        "common",
        "auth",
        "catalog",
        "checkout",
      ]);
    });

    it("should deduplicate library names", () => {
      const feature = { libraries: "common, auth" };
      const scenario = { libraries: "auth, checkout, common" };
      expect(resolveLibraryNames(feature, scenario)).toEqual(["common", "auth", "checkout"]);
    });

    it("should override feature libraries when inheritance is 'override'", () => {
      const feature = { libraries: "auth, catalog" };
      const scenario = { libraries: "admin" };
      expect(resolveLibraryNames(feature, scenario, { inheritance: "override" })).toEqual([
        "common",
        "admin",
      ]);
    });

    it("should keep feature libraries when scenario has no libraries in 'override' mode", () => {
      const feature = { libraries: "auth, catalog" };
      const scenario = {};
      expect(resolveLibraryNames(feature, scenario, { inheritance: "override" })).toEqual([
        "common",
        "auth",
        "catalog",
      ]);
    });
  });

  describe("isScenarioPending", () => {
    it("should detect @pending or @skip on feature or scenario", () => {
      expect(isScenarioPending({ pending: true }, {})).toBe(true);
      expect(isScenarioPending({}, { skip: true })).toBe(true);
      expect(isScenarioPending({ Pending: "reason" }, {})).toBe(true);
      expect(isScenarioPending({}, {})).toBe(false);
      expect(isScenarioPending({ pending: false }, { skip: false })).toBe(false);
    });
  });

  describe("isScenarioOnly", () => {
    it("should detect @only on feature or scenario", () => {
      expect(isScenarioOnly({ only: true }, {})).toBe(true);
      expect(isScenarioOnly({}, { Only: "focus" })).toBe(true);
      expect(isScenarioOnly({}, {})).toBe(false);
    });
  });
});
