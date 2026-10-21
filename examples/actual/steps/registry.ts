import { createLibraryRegistry } from "playwright-yadda";
import { commonSteps } from "./common.js";
import { ruleSteps } from "./rules.js";
import { transactionSteps } from "./transactions.js";

export const registry = createLibraryRegistry(
  {
    common: commonSteps,
    rules: ruleSteps,
    transactions: transactionSteps,
  },
  { defaultLibraries: ["common"] },
);
