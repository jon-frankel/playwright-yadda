export * from "./cli.mjs";

import { cli } from "./cli.mjs";

void (async () => {
  const result = await cli();
  console.log(result);
})();
