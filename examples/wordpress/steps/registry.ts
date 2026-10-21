import { createLibraryRegistry } from "playwright-yadda";
import { commonSteps } from "./common.js";
import { authSteps } from "./auth.js";
import { postSteps } from "./posts.js";

// The key type is spelled out because 0.0.9 infers it from defaultLibraries too, and then rejects the
// other keys. Fixed after 0.0.9, so this can go once the example moves to a newer release.
export const registry = createLibraryRegistry<"wp-common" | "wp-auth" | "wp-posts">(
  {
    "wp-common": commonSteps,
    "wp-auth": authSteps,
    "wp-posts": postSteps,
  },
  {
    defaultLibraries: ["wp-common"],
  },
);
