const $=selector=>document.querySelector(selector);
const $$=selector=>[...document.querySelectorAll(selector)];

const actions={
  'media.volumeup':{title:'Громче +',type:'media',key:'volumeup'},'media.volumedown':{title:'Тише −',type:'media',key:'volumedown'},'media.mute':{title:'Без звука',type:'media',key:'mute'},'media.playpause':{title:'Пуск / пауза',type:'media',key:'playpause'},'media.previous':{title:'Предыдущий',type:'media',key:'previous'},'media.next':{title:'Следующий',type:'media',key:'next'},'media.stop':{title:'Стоп',type:'media',key:'stop'},
  'key.enter':{title:'Enter',type:'key',key:'enter'},'key.backspace':{title:'⌫ Стереть',type:'key',key:'backspace'},'key.tab':{title:'Tab',type:'key',key:'tab'},'key.escape':{title:'Esc',type:'key',key:'escape'},'key.space':{title:'Пробел',type:'key',key:'space'},'key.delete':{title:'Delete',type:'key',key:'delete'},'key.left':{title:'←',type:'key',key:'left'},'key.up':{title:'↑',type:'key',key:'up'},'key.down':{title:'↓',type:'key',key:'down'},'key.right':{title:'→',type:'key',key:'right'},'key.f5':{title:'F5',type:'key',key:'f5'},'key.f11':{title:'Полный экран',type:'key',key:'f11'},
  'shortcut.copy':{title:'Копировать',type:'shortcut',key:'copy'},'shortcut.paste':{title:'Вставить',type:'shortcut',key:'paste'},'shortcut.cut':{title:'Вырезать',type:'shortcut',key:'cut'},'shortcut.selectall':{title:'Выделить всё',type:'shortcut',key:'selectall'},'shortcut.undo':{title:'Отменить',type:'shortcut',key:'undo'},'shortcut.redo':{title:'Повторить',type:'shortcut',key:'redo'},'shortcut.save':{title:'Сохранить',type:'shortcut',key:'save'},'shortcut.find':{title:'Найти',type:'shortcut',key:'find'},'shortcut.back':{title:'Назад',type:'shortcut',key:'back'},'shortcut.forward':{title:'Вперёд',type:'shortcut',key:'forward'},'shortcut.newtab':{title:'Новая вкладка',type:'shortcut',key:'newtab'},'shortcut.closetab':{title:'Закрыть вкладку',type:'shortcut',key:'closetab'},'shortcut.desktop':{title:'Рабочий стол',type:'shortcut',key:'desktop'},'shortcut.switchwindow':{title:'Сменить окно',type:'shortcut',key:'switchwindow'}
};

const state={ws:null,pending:[],connected:false,connecting:false,pcName:'',installPrompt:null,moveX:0,moveY:0,moveBusy:false};
const prefs={
  get(key,fallback=''){const value=localStorage.getItem('clickmate.'+key);return value===null?fallback:value},
  set(key,value){localStorage.setItem('clickmate.'+key,String(value))},
  remove(key){localStorage.removeItem('clickmate.'+key)}
};

function toast(message){const el=$('#toast');el.textContent=message;el.classList.add('show');clearTimeout(toast.timer);toast.timer=setTimeout(()=>el.classList.remove('show'),2400)}
function setStatus(kind,text){const button=$('#connectionButton');button.classList.toggle('online',kind==='online');button.classList.toggle('connecting',kind==='connecting');button.querySelector('span').textContent=text}
function privateAddress(value){const p=value.split('.').map(Number);return p.length===4&&p.every((n,i)=>Number.isInteger(n)&&n>=0&&n<=255)&&(p[0]===10||(p[0]===192&&p[1]===168)||(p[0]===172&&p[1]>=16&&p[1]<=31)||(p[0]===169&&p[1]===254)||p[0]===127)}
function createProof(token,challenge){return ClickMateCrypto.createProof(token,challenge)}

function socketUrl(address){const local=location.hostname&&privateAddress(location.hostname)&&location.port==='48732';const host=local?location.host:`${address}:48732`;if(location.protocol==='https:'&&!local)throw new Error('Для опубликованной PWA потребуется защищённый мост к ПК');return `${location.protocol==='https:'?'wss':'ws'}://${host}/ws`}
function openSocket(address){return new Promise((resolve,reject)=>{let ws;try{ws=new WebSocket(socketUrl(address))}catch(error){reject(error);return}state.ws=ws;ws.onopen=()=>resolve(ws);ws.onerror=()=>reject(new Error('ПК не отвечает'));ws.onmessage=event=>{const item=state.pending.shift();if(item){try{item.resolve(JSON.parse(event.data))}catch(error){item.reject(error)}}};ws.onclose=()=>{const wasConnected=state.connected;state.connected=false;state.connecting=false;state.pending.splice(0).forEach(item=>item.reject(new Error('Связь прервана')));setStatus('offline','Не подключено');if(wasConnected)toast('Связь с ПК прервана')}})}
function request(payload){return new Promise((resolve,reject)=>{if(!state.ws||state.ws.readyState!==WebSocket.OPEN){reject(new Error('Нет подключения'));return}state.pending.push({resolve,reject});state.ws.send(JSON.stringify(payload))})}
async function connectPc(address,pin=null,token=null){
  if(state.connecting)return;state.connecting=true;setStatus('connecting','Подключение…');
  try{
    await openSocket(address);let response;
    if(token){const hello=await request({type:'resume'});if(!hello.ok||!hello.challenge)throw new Error('ПК нужно обновить');response=await request({type:'resume_proof',proof:await createProof(token,hello.challenge)})}
    else response=await request({type:'pair',pin});
    if(!response.ok)throw new Error(response.error||'Подключение отклонено');
    if(response.protocol<4)throw new Error('Обновите ClickMate на ПК');
    if(!token&&typeof response.token==='string'&&response.token.length>=40)prefs.set('token',response.token);
    prefs.set('address',address);prefs.set('pcName',response.name||'ПК');state.pcName=response.name||'ПК';state.connected=true;state.connecting=false;setStatus('online',state.pcName);$('#pairDialog').close();$('#pairError').textContent='';toast('Подключено к '+state.pcName)
  }catch(error){state.connecting=false;state.connected=false;if(token)prefs.remove('token');if(state.ws)state.ws.close();setStatus('offline','Не подключено');throw error}
}
function disconnect(){if(state.ws)state.ws.close();state.ws=null;state.connected=false;setStatus('offline','Не подключено')}
async function command(payload,silent=false){if(!state.connected){openPairDialog();if(!silent)toast('Сначала подключите ПК');return false}try{const response=await request(payload);if(!response.ok)throw new Error(response.error||'Команда не выполнена');return true}catch(error){if(!silent)toast(error.message);return false}}
function actionCommand(id){const action=actions[id];return action?{type:action.type,[action.type==='click'?'button':'key']:action.key}:null}

function showView(id){$$('.view').forEach(view=>view.classList.toggle('active',view.dataset.view===id));$$('[data-nav]').forEach(button=>button.classList.toggle('active',button.dataset.nav===id));prefs.set('view',id);window.scrollTo(0,0)}
$$('[data-nav]').forEach(button=>button.addEventListener('click',()=>showView(button.dataset.nav)));
$$('[data-command]').forEach(button=>button.addEventListener('click',()=>command(JSON.parse(button.dataset.command))));
$$('[data-action]').forEach(button=>button.addEventListener('click',()=>command(actionCommand(button.dataset.action))));
$('[data-double]').addEventListener('click',async()=>{await command({type:'click',button:'left'});await command({type:'click',button:'left'})});

function fillGrid(target,ids){const root=$(target);ids.forEach(id=>{const button=document.createElement('button');button.textContent=actions[id].title;button.addEventListener('click',()=>command(actionCommand(id)));root.append(button)})}
fillGrid('#keyboardGrid',['key.escape','key.tab','key.backspace','key.enter','key.space','key.delete','key.left','key.up','key.down','key.right','shortcut.copy','shortcut.paste','shortcut.cut','shortcut.selectall','shortcut.undo','shortcut.redo','shortcut.save','shortcut.find']);
fillGrid('#mediaGrid',['media.previous','media.next','media.volumedown','media.volumeup','media.mute','media.stop','key.left','key.right','key.f11','key.escape']);
$('#sendText').addEventListener('click',async()=>{const input=$('#textInput'),value=input.value;if(value&&await command({type:'text',text:value}))input.value=''});

const pad=$('#touchpad'),pointers=new Map();let startX=0,startY=0,lastX=0,lastY=0,downAt=0,lastTapAt=0,lastTapX=0,lastTapY=0,moved=false,dragging=false,lastMultiY=0;
function flushMove(){if(state.moveBusy||(!state.moveX&&!state.moveY))return;const x=Math.max(-1000,Math.min(1000,Math.round(state.moveX))),y=Math.max(-1000,Math.min(1000,Math.round(state.moveY)));state.moveX-=x;state.moveY-=y;state.moveBusy=true;command({type:'move',x,y},true).finally(()=>{state.moveBusy=false;flushMove()})}
pad.addEventListener('pointerdown',event=>{if(!state.connected){openPairDialog();return}pad.setPointerCapture(event.pointerId);pointers.set(event.pointerId,{x:event.clientX,y:event.clientY});if(pointers.size===1){startX=lastX=event.clientX;startY=lastY=event.clientY;downAt=Date.now();moved=false;dragging=downAt-lastTapAt<360&&Math.abs(startX-lastTapX)+Math.abs(startY-lastTapY)<48;if(dragging){lastTapAt=0;command({type:'button',button:'left',state:'down'},true)}}else{lastTapAt=0;dragging=false;command({type:'button',button:'left',state:'up'},true);lastMultiY=[...pointers.values()].reduce((sum,p)=>sum+p.y,0)/pointers.size}});
pad.addEventListener('pointermove',event=>{if(!pointers.has(event.pointerId))return;pointers.set(event.pointerId,{x:event.clientX,y:event.clientY});if(pointers.size>1){const average=[...pointers.values()].reduce((sum,p)=>sum+p.y,0)/pointers.size;const delta=average-lastMultiY;lastMultiY=average;const reverse=prefs.get('reverse','false')==='true';if(Math.abs(delta)>1)command({type:'scroll',amount:Math.max(-1200,Math.min(1200,Math.round((reverse?delta:-delta)*18)))},true)}else{const speed=Number(prefs.get('sensitivity','1'));state.moveX+=(event.clientX-lastX)*speed;state.moveY+=(event.clientY-lastY)*speed;lastX=event.clientX;lastY=event.clientY;if(Math.abs(lastX-startX)+Math.abs(lastY-startY)>7)moved=true;flushMove()}});
function endPointer(event){if(!pointers.has(event.pointerId))return;pointers.delete(event.pointerId);if(pointers.size)return;if(dragging){dragging=false;command({type:'button',button:'left',state:'up'},true)}else if(event.type==='pointerup'&&!moved){const held=Date.now()-downAt;if(held>500){lastTapAt=0;command({type:'click',button:'right'})}else if(held<300){lastTapAt=Date.now();lastTapX=event.clientX;lastTapY=event.clientY;command({type:'click',button:'left'})}}else lastTapAt=0}
pad.addEventListener('pointerup',endPointer);pad.addEventListener('pointercancel',endPointer);pad.addEventListener('contextmenu',event=>event.preventDefault());

function openPairDialog(){const address=prefs.get('address',privateAddress(location.hostname)?location.hostname:'');$('#pcAddress').value=address;$('#pinInput').value='';$('#pairError').textContent='';$('#pairDialog').showModal();setTimeout(()=>$('#pinInput').focus(),100)}
$('#connectionButton').addEventListener('click',()=>{if(state.connected){if(confirm('Отключиться от '+state.pcName+'?'))disconnect()}else openPairDialog()});
$('#pairForm').addEventListener('submit',async event=>{event.preventDefault();const address=$('#pcAddress').value.trim(),pin=$('#pinInput').value.trim();if(!privateAddress(address)){ $('#pairError').textContent='Введите локальный IPv4-адрес компьютера';return}if(!/^\d{4}$/.test(pin)){ $('#pairError').textContent='Введите четыре цифры с экрана ПК';return}const submit=$('#pairSubmit');submit.disabled=true;$('#pairError').textContent='';try{await connectPc(address,pin)}catch(error){$('#pairError').textContent=error.message}finally{submit.disabled=false}});

function loadTiles(){try{return JSON.parse(prefs.get('tiles','[]'))}catch{return[]}}
function saveTiles(tiles){prefs.set('tiles',JSON.stringify(tiles));renderTiles()}
function renderTiles(){const tiles=loadTiles(),grid=$('#customGrid');grid.replaceChildren();$('#customEmpty').hidden=tiles.length>0;tiles.forEach((tile,index)=>{const button=document.createElement('button');button.className='custom-tile '+tile.color;button.dataset.span=tile.span;button.textContent=tile.label;button.addEventListener('click',()=>command(actionCommand(tile.action)));const remove=document.createElement('span');remove.className='remove';remove.textContent='×';remove.setAttribute('role','button');remove.setAttribute('aria-label','Удалить '+tile.label);remove.addEventListener('click',event=>{event.stopPropagation();const next=loadTiles();next.splice(index,1);saveTiles(next)});button.append(remove);grid.append(button)})}
const actionSelect=$('#tileAction');Object.entries(actions).forEach(([id,action])=>{const option=document.createElement('option');option.value=id;option.textContent=action.title;actionSelect.append(option)});actionSelect.addEventListener('change',()=>{$('#tileLabel').value=actions[actionSelect.value].title});
function openTileDialog(){actionSelect.value=Object.keys(actions)[0];$('#tileLabel').value=actions[actionSelect.value].title;$('#tileDialog').showModal()}
$('#addTile').addEventListener('click',openTileDialog);$('#addFirstTile').addEventListener('click',openTileDialog);$('#tileForm').addEventListener('submit',event=>{event.preventDefault();const tiles=loadTiles();if(tiles.length>=24){toast('Можно добавить до 24 кнопок');return}tiles.push({action:actionSelect.value,label:$('#tileLabel').value.trim()||actions[actionSelect.value].title,span:Number($('#tileSpan').value),color:$('#tileColor').value});saveTiles(tiles);$('#tileDialog').close()});

const sensitivity=$('#sensitivity'),reverse=$('#reverseScroll'),dark=$('#darkTheme');sensitivity.value=prefs.get('sensitivity','1');reverse.checked=prefs.get('reverse','false')==='true';dark.checked=prefs.get('dark',matchMedia('(prefers-color-scheme:dark)').matches?'true':'false')==='true';
function applyTheme(){document.documentElement.classList.toggle('dark',dark.checked);prefs.set('dark',dark.checked);document.querySelector('meta[name="theme-color"]').content=dark.checked?'#081724':'#071b2c'}applyTheme();
function showSensitivity(){ $('#sensitivityValue').textContent=Number(sensitivity.value).toFixed(1).replace('.',',')+'×'}showSensitivity();sensitivity.addEventListener('input',()=>{prefs.set('sensitivity',sensitivity.value);showSensitivity()});reverse.addEventListener('change',()=>prefs.set('reverse',reverse.checked));dark.addEventListener('change',applyTheme);
$('#forgetButton').addEventListener('click',()=>{if(confirm('Забыть сохранённый компьютер и ключ доверия?')){disconnect();['token','address','pcName'].forEach(key=>prefs.remove(key));toast('Компьютер забыт')}});
window.addEventListener('beforeinstallprompt',event=>{event.preventDefault();state.installPrompt=event;$('#installButton').hidden=false});$('#installButton').addEventListener('click',async()=>{if(state.installPrompt){state.installPrompt.prompt();await state.installPrompt.userChoice;state.installPrompt=null;$('#installButton').hidden=true}});
if('serviceWorker'in navigator&&isSecureContext)navigator.serviceWorker.register('/sw.js').catch(()=>{});
document.addEventListener('visibilitychange',()=>{if(document.visibilityState==='visible'&&!state.connected&&!state.connecting){const token=prefs.get('token'),address=prefs.get('address');if(token&&address)connectPc(address,null,token).catch(error=>toast(error.message))}});
setInterval(()=>{if(state.connected)command({type:'ping'},true)},4000);
showView(prefs.get('view','mouse'));renderTiles();
const savedToken=prefs.get('token'),savedAddress=prefs.get('address');if(savedToken&&savedAddress)connectPc(savedAddress,null,savedToken).catch(error=>toast(error.message));
