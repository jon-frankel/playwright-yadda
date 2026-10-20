import http from "node:http";
import fs from "node:fs";
import path from "node:path";
import { fileURLToPath } from "node:url";

const __dirname = path.dirname(fileURLToPath(import.meta.url));
const publicDir = path.resolve(__dirname, "../public");
const port = process.env.PORT ? parseInt(process.env.PORT, 10) : 3000;

const server = http.createServer((req, res) => {
  const reqUrl = new URL(req.url || "/", `http://127.0.0.1:${port}`);
  const reqPath = reqUrl.pathname === "/" ? "/index.html" : reqUrl.pathname;
  const filePath = path.join(publicDir, reqPath);

  if (fs.existsSync(filePath) && fs.statSync(filePath).isFile()) {
    const ext = path.extname(filePath);
    const contentTypes = {
      ".html": "text/html",
      ".js": "text/javascript",
      ".css": "text/css",
    };
    res.writeHead(200, { "Content-Type": contentTypes[ext] || "text/plain" });
    fs.createReadStream(filePath).pipe(res);
  } else {
    res.writeHead(404, { "Content-Type": "text/plain" });
    res.end("Not Found");
  }
});

server.listen(port, "127.0.0.1", () => {
  console.log(`WordPress WASM server listening at http://127.0.0.1:${port}/`);
});
