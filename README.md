# playwright-yadda

> Dynamic step library loading for Playwright and Yadda BDD test suites.

[![License: MIT](https://img.shields.io/badge/License-MIT-blue.svg)](https://opensource.org/licenses/MIT)

`playwright-yadda` bridges [Yadda](https://github.com/acuminous/yadda) and [@playwright/test](https://playwright.dev/) with **dynamic step library loading**. Instead of compiling all step definitions into a single global regex dictionary, features and scenarios declare their dependencies via annotations (e.g. `@Libraries=auth, catalog, checkout`).

---

## Key Benefits

- **Zero Global Regex Collisions**: Domain step definitions remain strictly isolated. Two domains can use identical step text without conflict.
- **Trace Viewer & HTML Report Fidelity**: Every Gherkin step is executed inside Playwright's `test.step()`, giving you granular execution timelines, screenshots, and traces.
- **Negligible Overhead**: An isolated Yadda runner is instantiated per scenario with only the relevant pre-compiled libraries.
- **Native Async & State Persistence**: Supports modern `async/await` steps and automatically persists scenario context state across steps.

---

## Installation

```bash
pnpm add playwright-yadda
pnpm add -D yadda @types/yadda @playwright/test
```

---

## Quick Start

### 1. Define Step Libraries

Use `createStepLibrary()` to define domain-specific step libraries with English Gherkin keywords (`given`, `when`, `then`, `define`):

```typescript
// steps/storefront.ts
import { createStepLibrary } from "playwright-yadda";
import { expect } from "@playwright/test";

export const storefrontSteps = createStepLibrary();

storefrontSteps.given("a customer is on the storefront", function () {
  this.cart = ["Mechanical Keyboard"];
});

storefrontSteps.when("they remove the item", function () {
  this.cart = [];
});

storefrontSteps.then("the cart count is 0", function () {
  expect(this.cart).toHaveLength(0);
});
```

### 2. Create the Library Registry

```typescript
// steps/registry.ts
import { createLibraryRegistry } from "playwright-yadda";
import { commonSteps } from "./common.js";
import { storefrontSteps } from "./storefront.js";
import { adminSteps } from "./admin.js";

export const registry = createLibraryRegistry({
  common: commonSteps, // Included by default for every scenario
  storefront: storefrontSteps,
  admin: adminSteps,
});
```

### 3. Annotate Features or Scenarios

```gherkin
# features/storefront.feature
@Libraries=storefront
Feature: Storefront Shopping
  As a customer
  I want to manage my cart

  Scenario: Customer clears cart
    Given the test session is active
    And a customer is on the storefront
    When they remove the item
    Then the cart count is 0
```

### 4. Run with Playwright Test

```typescript
// e2e/features.spec.ts
import path from "node:path";
import { fileURLToPath } from "node:url";
import { test } from "@playwright/test";
import { defineFeatureSuite } from "playwright-yadda";
import { registry } from "../steps/registry.js";

const __dirname = path.dirname(fileURLToPath(import.meta.url));

defineFeatureSuite({
  test,
  registry,
  features: path.resolve(__dirname, "../features"),
});
```

Run tests with standard Playwright CLI:

```bash
pnpm playwright test
```

---

## API Reference

### `createLibraryRegistry(libraries, options?)`

Creates a typed registry manager.

- `defaultLibraries`: Library keys always included for every scenario (defaults to `['common']`).
- `inheritance`: `'merge'` (default) or `'override'` (scenario annotations replace feature annotations).

### `createStepLibrary(dictionary?, mode?)`

Creates an English Gherkin-localised step library supporting `given`, `when`, `then`, and `define`.

- `mode`: `'bound'` (default, binds context to `this`) or `'param'` (passes context as 1st argument).

### `defineFeatureSuite(options)`

Discovers feature files and registers them with Playwright's `test` runner.

- `test`: The Playwright `test` instance.
- `registry`: The `LibraryRegistry` or `LibraryRegistryManager`.
- `features`: Directory path, file path, glob pattern, or pre-parsed feature array.
- `createContext`: Optional custom factory function to initialize scenario context.

---

## IDE Support

### WebStorm and other JetBrains IDEs

WebStorm's built-in Cucumber.js support already jumps from a step in a `.feature` file to its `.given()` / `.when()` / `.then()` definition. The companion plugin in [`ide/jetbrains`](ide/jetbrains) adds the way back: **Cmd+B or Find Usages on a step definition lists the feature steps that run it.** It also understands `@Libraries`, so when two libraries define the same step text, navigation in both directions follows the libraries a scenario enables. See its [README](ide/jetbrains/README.md) to build and install it.

---

## License

[MIT](LICENSE)
