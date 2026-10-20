import { addUser, authenticate, createToken, login } from "./npm-authn.mjs";

/**
 * @returns {[string, string, string, string]}
 */
function getArgs() {
  const args = process.argv.slice(2);
  const [command, username, password, domain] = args;
  if (!command || !username || !password) {
    throw new Error("Username and password required");
  }
  const base = domain ?? "http://localhost:4873";
  return [command, username, password, base];
}

/**
 * @type {Record<string, function(string, string, string): Promise<string>>}
 */
const commands = {
  addUser,
  "add-user": addUser,
  "user:add": addUser,
  login,
  authenticate,
  createToken,
  "create-token": createToken,
  "token:create": createToken,
};

export async function cli() {
  const [command, ...args] = getArgs();
  const fn = commands[command];
  if (!fn) {
    throw new Error(`Unknown command: ${command}`);
  }
  try {
    return await fn(...args);
  } catch (e) {
    console.error({ errorMessage: e.message, errorData: e.response?.data });
    throw e;
  }
}
