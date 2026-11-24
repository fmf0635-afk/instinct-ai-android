import http from "node:http";
import { readFileSync, existsSync } from "node:fs";

function loadEnv() {
  if (!existsSync(".env")) return;
  for (const line of readFileSync(".env", "utf8").split(/\r?\n/)) {
    const m = line.match(/^\s*([A-Za-z_][A-Za-z0-9_]*)\s*=\s*(.*?)\s*$/);
    if (m && !(m[1] in process.env)) process.env[m[1]] = m[2].replace(/^["']|["']$/g, "");
  }
}
loadEnv();

const host = process.env.HOST || "127.0.0.1";
const port = Number(process.env.PORT || 8787);
const apiBase = (process.env.AI_BASE_URL || "https://api.openai.com/v1").replace(/\/$/, "");
const apiKey = process.env.AI_API_KEY || "";
const model = process.env.AI_MODEL || "gpt-4.1-mini";
const accessToken = process.env.APP_ACCESS_TOKEN || "";

function send(res, status, body) {
  res.writeHead(status, {
    "content-type": "application/json; charset=utf-8",
    "cache-control": "no-store",
    "x-content-type-options": "nosniff",
    "referrer-policy": "no-referrer"
  });
  res.end(JSON.stringify(body));
}

async function readBody(req) {
  let raw = "";
  for await (const chunk of req) {
    raw += chunk;
    if (raw.length > 64_000) throw new Error("Request too large");
  }
  return JSON.parse(raw || "{}");
}

const server = http.createServer(async (req, res) => {
  if (req.method === "GET" && req.url === "/health") return send(res, 200, { ok: true, service: "instinct-ai" });
  if (req.method !== "POST" || req.url !== "/chat") return send(res, 404, { error: "Not found" });

  if (accessToken) {
    const supplied = req.headers.authorization || "";
    if (supplied !== `Bearer ${accessToken}`) return send(res, 401, { error: "Unauthorized" });
  }
  if (!apiKey) return send(res, 503, { error: "AI_API_KEY is not configured on the server" });

  try {
    const body = await readBody(req);
    const prompt = typeof body.message === "string" ? body.message.trim() : "";
    if (!prompt || prompt.length > 8000) return send(res, 400, { error: "message is required and must be under 8000 characters" });

    const history = Array.isArray(body.history) ? body.history.slice(-12).filter(x =>
      x && ["user", "assistant"].includes(x.role) && typeof x.content === "string"
    ).map(x => ({ role: x.role, content: x.content.slice(0, 4000) })) : [];

    const upstream = await fetch(`${apiBase}/chat/completions`, {
      method: "POST",
      headers: { "authorization": `Bearer ${apiKey}`, "content-type": "application/json" },
      body: JSON.stringify({
        model,
        messages: [
          { role: "system", content: "You are Instinct AI, a helpful, safe, friendly bilingual personal assistant. Reply in the language used by the user, especially Egyptian Arabic when appropriate. Be honest about actions: never claim to control an app or device unless a connected tool confirms it. Ask before consequential external actions." },
          ...history,
          { role: "user", content: prompt }
        ],
        temperature: 0.7
      }),
      signal: AbortSignal.timeout(55000)
    });

    const result = await upstream.json().catch(() => ({}));
    if (!upstream.ok) {
      const message = result?.error?.message || `AI provider returned HTTP ${upstream.status}`;
      return send(res, 502, { error: message });
    }
    const reply = result?.choices?.[0]?.message?.content;
    if (typeof reply !== "string" || !reply.trim()) return send(res, 502, { error: "AI provider returned an empty response" });
    return send(res, 200, { reply: reply.trim(), model });
  } catch (error) {
    const status = error.message === "Request too large" ? 413 : 400;
    return send(res, status, { error: error.message || "Request failed" });
  }
});

server.listen(port, host, () => {
  console.log(`Instinct AI API listening on http://${host}:${port}`);
  if (!accessToken) console.warn("APP_ACCESS_TOKEN is empty. Set it before exposing this API.");
});
