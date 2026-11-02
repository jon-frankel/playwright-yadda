import { createLibraryRegistry } from "playwright-yadda";
import { commonSteps } from "./common.js";
import { sessionSteps } from "./session.js";
import { ruleSteps } from "./rules.js";
import { accountSteps } from "./accounts.js";
import { transactionSteps } from "./transactions.js";
import { mobileSteps } from "./mobile.js";
import { budgetingSteps } from "./budget.js";
import { scheduleSteps } from "./schedules.js";
import { inheritanceASteps, inheritanceBSteps } from "./inheritance.js";

export const registry = createLibraryRegistry(
  {
    common: commonSteps,
    session: sessionSteps,
    rules: ruleSteps,
    transactions: transactionSteps,
    accounts: accountSteps,
    mobile: mobileSteps,
    budgeting: budgetingSteps,
    schedules: scheduleSteps,
    inheritanceA: inheritanceASteps,
    inheritanceB: inheritanceBSteps,
  },
  { defaultLibraries: ["common", "session"] },
);
