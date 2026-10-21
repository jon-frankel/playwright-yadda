import { createLibraryRegistry } from "playwright-yadda";
import { commonSteps } from "./common.js";
import { authSteps } from "./auth.js";
import { postSteps } from "./posts.js";

export const registry = createLibraryRegistry(
  {
    "wp-common": commonSteps,
    "wp-auth": authSteps,
    "wp-posts": postSteps,
  },
  {
    defaultLibraries: ["wp-common"],
  },
);
