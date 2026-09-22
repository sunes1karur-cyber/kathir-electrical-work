(function(){
  if(!window.AndroidUpdater)return;
  window.Capacitor={
    getPlatform:function(){return'android'},
    Plugins:{AppUpdater:{downloadAndInstall:async function(info){
      var value=window.AndroidUpdater.downloadAndInstall(info.url||'',info.version||'',info.sha256||'');
      try{return JSON.parse(value)}catch(_error){return{started:true}}
    }}}
  };
})();
