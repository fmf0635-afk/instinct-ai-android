import http from 'node:http';
import { readFile } from 'node:fs/promises';
import { extname, join, normalize } from 'node:path';
import { fileURLToPath } from 'node:url';

const root = fileURLToPath(new URL('../', import.meta.url)).replace(/\/$/, '');
const env = {};
try { for (const line of (await readFile(join(root, '.env'), 'utf8')).split(/\r?\n/)) { const m=line.match(/^\s*([A-Z0-9_]+)\s*=\s*(.*)\s*$/); if(m && !m[1].startsWith('#')) env[m[1]]=m[2].replace(/^['"]|['"]$/g,''); } } catch {}
const cfg = { port:Number(process.env.PORT||env.PORT||8787), host:process.env.HOST||env.HOST||'127.0.0.1', key:process.env.AI_API_KEY||env.AI_API_KEY, accessToken:process.env.APP_ACCESS_TOKEN||env.APP_ACCESS_TOKEN||'', base:(process.env.AI_BASE_URL||env.AI_BASE_URL||'https://api.openai.com/v1').replace(/\/$/,''), model:process.env.AI_MODEL||env.AI_MODEL||'gpt-4.1-mini' };
const mime={'.html':'text/html; charset=utf-8','.css':'text/css','.js':'text/javascript','.json':'application/json','.webmanifest':'application/manifest+json','.png':'image/png','.svg':'image/svg+xml'};
const server=http.createServer(async(req,res)=>{
  res.setHeader('X-Content-Type-Options','nosniff'); res.setHeader('Referrer-Policy','strict-origin-when-cross-origin');
  if(req.method==='POST' && req.url==='/api/chat'){
    if(cfg.accessToken && req.headers.authorization !== `Bearer ${cfg.accessToken}`){res.writeHead(401,{'Content-Type':'application/json'});return res.end(JSON.stringify({error:'Unauthorized. Check your Instinct AI access token.'}));}
    if(!cfg.key || cfg.key==='replace_me'){res.writeHead(503,{'Content-Type':'application/json'});return res.end(JSON.stringify({error:'AI is not configured. Add AI_API_KEY to your local .env file.'}));}
    try {
      let raw=''; for await (const chunk of req) { raw+=chunk; if(raw.length>100_000) throw new Error('Request too large'); }
      const body=JSON.parse(raw); if(!Array.isArray(body.messages)||body.messages.length>30) throw new Error('Invalid messages');
      const messages=body.messages.map(m=>({role:['user','assistant'].includes(m.role)?m.role:'user',content:String(m.content||'').slice(0,12000)}));
      const upstream=await fetch(`${cfg.base}/chat/completions`,{method:'POST',headers:{'Authorization':`Bearer ${cfg.key}`,'Content-Type':'application/json'},body:JSON.stringify({model:cfg.model,messages:[{role:'system',content:'You are Instinct AI, a helpful bilingual personal assistant. Reply in the language the user uses. Do not claim to have accessed apps, devices, accounts, or performed actions unless a connected tool actually confirms it. Ask for explicit confirmation before consequential external actions.'},...messages],temperature:0.6})});
      const data=await upstream.json(); if(!upstream.ok) throw new Error(data.error?.message||`AI provider error (${upstream.status})`);
      res.writeHead(200,{'Content-Type':'application/json'}); return res.end(JSON.stringify({reply:data.choices?.[0]?.message?.content||'No response returned.'}));
    } catch(e){res.writeHead(400,{'Content-Type':'application/json'});return res.end(JSON.stringify({error:e.message||'Chat request failed'}));}
  }
  if(req.method!=='GET' && req.method!=='HEAD'){res.writeHead(405);return res.end('Method not allowed');}
  const requested=req.url==='/'?'/index.html':decodeURIComponent(req.url.split('?')[0]);
  const path=normalize(join(root,requested)); if(path!==root && !path.startsWith(root + '/')){res.writeHead(403);return res.end('Forbidden');}
  try{const data=await readFile(path);res.writeHead(200,{'Content-Type':mime[extname(path)]||'application/octet-stream'});res.end(req.method==='HEAD'?undefined:data);}catch{res.writeHead(404);res.end('Not found');}
});
server.listen(cfg.port,cfg.host,()=>console.log(`Instinct AI running at http://${cfg.host}:${cfg.port}`));
