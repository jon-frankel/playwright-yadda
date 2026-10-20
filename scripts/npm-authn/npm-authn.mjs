import axios from "axios";

/**
 * @param {any[]} args
 */
function debug(...args) {
  if (process.env.DEBUG === "1") {
    console.log("DEBUG", ...args);
  }
}

function basicAuthHeader(username, password) {
  return `Basic ${Buffer.from(`${username}:${password}`).toString("base64")}`;
}

/**
 * @param {string} username
 * @param {string} password
 * @param {string} domain
 * @returns {Promise<string>}
 */
export async function addUser(username, password, domain) {
  const url = `${domain}/-/user/org.couchdb.user:${username}`;
  const data = { name: username, password };

  const response = await axios.put(url, data, {
    headers: {
      "Content-Type": "application/json",
      Accept: "*/*",
      "Accept-Encoding": "gzip",
    },
    validateStatus: (status) => status < 500,
  });

  const { ok, token: sessionToken } = response.data;
  if (!ok && response.status !== 409) {
    throw new Error(`Failed to add user: ${JSON.stringify(response.data)}`);
  }

  debug("addUser", { ok, sessionToken });
  return sessionToken || "";
}

/**
 * @param {string} username
 * @param {string} password
 * @param {string} domain
 * @returns {Promise<string>}
 */
export async function login(username, password, domain) {
  const userId = `org.couchdb.user:${username}`;
  const profileUrl = `${domain}/-/user/${userId}`;
  const headers = {
    "Content-Type": "application/json",
    Accept: "*/*",
    Authorization: basicAuthHeader(username, password),
  };

  const response = await axios.get(profileUrl, {
    headers,
    validateStatus: (status) => status < 500,
  });

  if (response.status !== 200) {
    throw new Error(`Failed to login: ${JSON.stringify(response.data)}`);
  }

  return basicAuthHeader(username, password);
}

/**
 * @param {string} username
 * @param {string} password
 * @param {string} domain
 * @returns {Promise<string>}
 */
export async function authenticate(username, password, domain) {
  try {
    return await login(username, password, domain);
  } catch {
    return await addUser(username, password, domain);
  }
}

/**
 * @param {string} username
 * @param {string} password
 * @param {string} domain
 * @returns {Promise<string>}
 */
export async function createToken(username, password, domain) {
  const url = `${domain}/-/npm/v1/tokens`;
  const basic = basicAuthHeader(username, password);

  // Try creating token with basic auth
  try {
    const response = await axios.post(
      url,
      {
        password,
        readonly: false,
        cidr_whitelist: [],
      },
      {
        headers: {
          "Content-Type": "application/json",
          Accept: "*/*",
          Authorization: basic,
        },
      },
    );

    if (response.data?.token) {
      debug({ response: response.data });
      return response.data.token;
    }
  } catch (err) {
    debug("Basic auth token creation failed, attempting to register user first:", err.message);
  }

  // Ensure user is created if not already present
  await addUser(username, password, domain);

  const response = await axios.post(
    url,
    {
      password,
      readonly: false,
      cidr_whitelist: [],
    },
    {
      headers: {
        "Content-Type": "application/json",
        Accept: "*/*",
        Authorization: basic,
      },
    },
  );

  const { token } = response.data;
  if (!token) {
    throw new Error("Failed to create token");
  }

  debug({ response: response.data });
  return token;
}
