# WordPress in WASM Playwright + Yadda Example

This example demonstrates running and testing a **complete WordPress instance running client-side inside WebAssembly (WASM)** in the browser, automated using `playwright-yadda` with dynamic step library resolution.

## How it works

1. **WordPress in Browser WASM**:
   - Uses [WordPress Playground](https://playground.wordpress.net/), which compiles PHP 8.x and SQLite to WebAssembly running inside browser Service Workers.
   - Embeds the Playground inside a standalone [`public/index.html`](./public/index.html) page.
   - Runs **100% ephemerally** in browser memory — no MySQL, no Docker containers, no persistent data cleanup needed.
2. **Dynamic Step Libraries**:
   - `wp-common`: Navigates to the local wrapper and attaches to the WordPress WASM execution frame.
   - `wp-auth`: Navigates to `/wp-login.php`, fills credentials, submits, and verifies `#wpadminbar`.
   - `wp-posts`: Navigates to `/wp-admin/edit.php`, edits the post title via Quick Edit, and asserts the updated post title.
   - Feature files selectively declare `@Libraries=wp-common, wp-auth, wp-posts` so each scenario only pulls the relevant step definitions.

## Running Tests

From this directory:

```bash
# 1. Install dependencies
pnpm install

# 2. Run the Playwright test suite
pnpm test
```

To run the local static server interactively and open the browser:

```bash
pnpm run serve
# Visit http://127.0.0.1:3000 in your browser
```
