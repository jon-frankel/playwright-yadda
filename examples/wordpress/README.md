# Native WordPress in WebAssembly (WASM) Example

This example demonstrates running and testing a **complete WordPress instance running natively in WebAssembly (WASM)** via Node.js, automated using `playwright-yadda` with dynamic step library resolution.

## How it works

1. **Native WordPress in WASM**:
   - Uses the official `@wp-playground/cli` package to run WordPress (PHP 8.3 + SQLite compiled to WASM) directly inside Node.js.
   - **No iframes**: WordPress is served natively on `http://127.0.0.1:3000`. Direct endpoints like `/wp-login.php` and `/wp-admin/` respond directly with WordPress HTML.
   - **100% Ephemeral**: SQLite database and files live in memory/temp storage and disappear after testing.
2. **Dynamic Step Libraries**:
   - `wp-common`: Ensures WordPress is reachable on `http://127.0.0.1:3000/`.
   - `wp-auth`: Navigates directly to `/wp-login.php`, enters credentials (`admin` / `password`), and confirms `#wpadminbar`.
   - `wp-posts`: Navigates to `/wp-admin/edit.php`, edits the post title via Quick Edit, and asserts the updated title.
   - Scenario declares `@Libraries=wp-common, wp-auth, wp-posts`.

## Running Tests

From this directory:

```bash
# 1. Install dependencies
pnpm install

# 2. Run the Playwright test suite
pnpm test
```

To start the native WordPress WASM server for interactive browser use:

```bash
pnpm run serve
# Open http://127.0.0.1:3000/wp-login.php in your browser
```
