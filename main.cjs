const { app, BrowserWindow, ipcMain, shell } = require('electron');
const path = require('node:path');
const { spawn } = require('node:child_process');
const { pathToFileURL } = require('node:url');

const SAFE_APPS = {
  calculator: { win: 'calc.exe', mac: 'Calculator', linux: 'gnome-calculator' },
  notepad: { win: 'notepad.exe', mac: 'TextEdit', linux: 'gedit' }
};
function createWindow(){
  const win=new BrowserWindow({width:1440,height:920,minWidth:900,minHeight:650,backgroundColor:'#0d0f16',webPreferences:{preload:path.join(__dirname,'preload.cjs'),contextIsolation:true,nodeIntegration:false,sandbox:true}});
  win.loadURL('http://127.0.0.1:8787');
}
app.whenReady().then(createWindow);
app.on('window-all-closed',()=>{if(process.platform!=='darwin')app.quit()});

// Only explicitly allowlisted apps are launchable. No arbitrary shell commands.
ipcMain.handle('instinct:launch-app',async(_event,appId)=>{
  if(typeof appId!=='string'||!Object.hasOwn(SAFE_APPS,appId)) return {ok:false,error:'App is not allowlisted'};
  const command=SAFE_APPS[appId][process.platform]; if(!command) return {ok:false,error:'This app shortcut is not configured for this operating system'};
  try { const child=spawn(command,[],{detached:true,stdio:'ignore',shell:false}); child.unref(); return {ok:true,app:appId}; }
  catch(e){return {ok:false,error:e.message};}
});
ipcMain.handle('instinct:open-url',async(_event,url)=>{
  try { const parsed=new URL(url); if(!['https:','http:'].includes(parsed.protocol)) throw new Error('Only web URLs are allowed'); await shell.openExternal(parsed.href); return {ok:true}; }
  catch(e){return {ok:false,error:e.message};}
});
