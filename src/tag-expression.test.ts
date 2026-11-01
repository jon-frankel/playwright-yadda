import { describe, expect, it } from "vitest";
import { parseTagExpression } from "./tag-expression.js";

const m = (expr: string, ...tags: string[]) => parseTagExpression(expr)(tags);

describe("tag expressions", () => {
  it("matches a single tag, case-insensitively", () => {
    expect(m("@smoke", "@smoke")).toBe(true);
    expect(m("@Smoke", "@smoke")).toBe(true);
    expect(m("@smoke", "@slow")).toBe(false);
    expect(m("@smoke")).toBe(false);
  });

  it("supports and, or and not", () => {
    expect(m("@a and @b", "@a", "@b")).toBe(true);
    expect(m("@a and @b", "@a")).toBe(false);
    expect(m("@a or @b", "@b")).toBe(true);
    expect(m("@a or @b", "@c")).toBe(false);
    expect(m("not @a", "@b")).toBe(true);
    expect(m("not @a", "@a")).toBe(false);
    expect(m("@mobile and not @slow", "@mobile")).toBe(true);
    expect(m("@mobile and not @slow", "@mobile", "@slow")).toBe(false);
    expect(m("NOT @a AND @b", "@b")).toBe(true);
  });

  it("gives not precedence over and, and and over or", () => {
    expect(m("@a or @b and @c", "@a")).toBe(true);
    expect(m("@a or @b and @c", "@b")).toBe(false);
    expect(m("not @a and @b", "@b")).toBe(true);
    expect(m("not not @a", "@a")).toBe(true);
  });

  it("supports parentheses", () => {
    expect(m("(@a or @b) and @c", "@a")).toBe(false);
    expect(m("(@a or @b) and @c", "@a", "@c")).toBe(true);
    expect(m("not (@a or @b)", "@c")).toBe(true);
    expect(m("not(@a)", "@a")).toBe(false);
  });

  it("reads @name=value as @name:value", () => {
    expect(m("@retries=2", "@retries:2")).toBe(true);
    expect(m("@retries:2", "@retries:2")).toBe(true);
  });

  it("rejects bad expressions", () => {
    for (const bad of [
      "",
      "@a and",
      "and @a",
      "(@a",
      "@a)",
      "@a @b",
      "smoke",
      "@a or or @b",
      "@",
    ]) {
      expect(() => parseTagExpression(bad)).toThrow(/tag expression/i);
    }
  });
});
