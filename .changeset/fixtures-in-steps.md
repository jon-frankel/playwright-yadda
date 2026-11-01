---
"playwright-yadda": minor
---

Steps can use Playwright fixtures. `createStepLibrary(dictionary, "fixtures")` passes the scenario's fixtures as a step's first argument, and `createBdd(test)` types them from your `test.extend({...})`. Each scenario's test function destructures only `page`, `context` and the fixtures its matching steps ask for, so other fixtures are not set up. `createContext` now also receives those fixtures.
