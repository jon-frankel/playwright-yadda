import { createLibraryRegistry } from "playwright-yadda";
import { commonSteps } from "./common.js";
import { sessionSteps } from "./session.js";
import { ruleSteps } from "./rules.js";
import { transactionSteps } from "./transactions.js";

export const registry = createLibraryRegistry(
  {
    common: commonSteps,
    session: sessionSteps,
    rules: ruleSteps,
    transactions: transactionSteps,
  },
  { defaultLibraries: ["common", "session"] },
);
