const { contextBridge, ipcRenderer } = require('electron');
contextBridge.exposeInMainWorld('instinctDesktop',Object.freeze({
  launchApp:(appId)=>ipcRenderer.invoke('instinct:launch-app',appId),
  openUrl:(url)=>ipcRenderer.invoke('instinct:open-url',url)
}));
