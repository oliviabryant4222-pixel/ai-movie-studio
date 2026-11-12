package studio.realframe;

/** Offline-capable phone-hosted web interface for the native local AI API.
 * The page itself loads no CDN, JavaScript library, font or third-party asset.
 * Cloud links are opt-in user clicks only.
 */
public final class LocalApiWebPage {
    private LocalApiWebPage() {}
    public static String html() {
        return """
<!doctype html>
<html lang="en"><head><meta charset="utf-8">
<meta name="viewport" content="width=device-width,initial-scale=1">
<title>RealFrame — Local Native AI Studio</title>
<style>
:root{font-family:system-ui,-apple-system,Arial;background:#090f1d;color:#eff5ff}
*{box-sizing:border-box}body{margin:0 auto;max-width:880px;padding:20px}
h1{font-size:25px;margin:0}small,.hint{color:#a6b9d1}
.badge{background:#26473f;color:#80f0c8;border-radius:20px;padding:6px 12px;font-weight:700;font-size:12px}
.panel{background:#192940;border:1px solid #2c3b56;padding:19px;border-radius:19px;margin:16px 0}
label{display:block;margin:11px 0 6px;font-size:13px;color:#b5c8e0}
input,textarea,select{background:#293c54;color:#fafaff;border:1px solid #445770;border-radius:10px;
 padding:12px;width:100%;font:inherit}textarea{min-height:94px}
button,.link{background:#7361cd;color:white;border:0;padding:12px 17px;
 border-radius:11px;cursor:pointer;font-weight:700;margin:7px 6px 7px 0}
a{color:#a5eced}.secondary{background:#256d69}.danger{background:#874f55}
.row{display:flex;gap:10px;flex-wrap:wrap}.row>*{flex:1}
pre{white-space:pre-wrap;word-break:break-word;background:#101c2d;padding:13px;border-radius:11px;max-height:300px;overflow:auto}
#media a{display:block;padding:7px 0}#media img{max-width:100%;border-radius:13px;margin:8px 0}
</style></head><body>
<div class="row" style="align-items:center"><div><h1>✦ REALFRAME LOCAL AI</h1>
<small>Native engine runs on your Android phone — with or without Internet</small></div>
<div><span class="badge">NO CLOUD UPLOAD</span></div></div>
<section class="panel">
<h2>🔑 Connect securely</h2>
<p class="hint">Copy the access token from RealFrame → Local API settings.
You do not need a Nightmare app. LAN sharing is only available after you explicitly enable it in RealFrame.</p>
<label>Device-local API token</label><input id="token" type="password" autocomplete="off" placeholder="Paste local API token">
<button class="secondary" onclick="health()">Check local model status</button>
<pre id="health">Local native API: enter your token and check status.</pre>
</section>
<section class="panel">
<h2>✨ Generate on the phone</h2>
<p class="hint">This mode uses native phone CPU inference; being connected to the Internet does not make it cloud generation.
You must import compatible licensed model files in the Android app first.</p>
<div class="row"><div><label>Output</label><select id="kind"><option value="image">Image (SD checkpoint)</option><option value="video">Video (experimental Wan 3-model pack)</option></select></div>
<div><label>Resolution</label><select id="size"><option value="256">256 × 256</option><option value="320">320 × 320</option><option value="384">384 × 384</option></select></div></div>
<label>Prompt</label><textarea id="prompt" placeholder="Photorealistic fictional adult walking through a sunlit garden">A cinematic portrait of a fictional adult, natural light, realistic details.</textarea>
<label>Negative prompt</label><input id="negative" value="watermark, blur, deformed hands">
<div class="row">
<div><label>Steps</label><select id="steps"><option value="4">4</option><option value="8">8</option><option value="12">12</option></select></div>
<div><label>Seed</label><input id="seed" inputmode="numeric" value="13901"></div>
<div><label>Image count / Video frames</label><select id="count"><option value="1">1 image / 5 video frames</option><option value="2">2 images / 9 video frames</option><option value="3">3 images / 17 video frames</option><option value="4">4 images / 33 video frames</option></select></div>
</div>
<button onclick="create()">▶ Generate locally</button>
<button class="danger" onclick="cancel()">Cancel active render</button>
<pre id="result">No active job.</pre>
<div id="media"></div>
</section>
<section class="panel"><h2>☁ Online generation (optional)</h2>
<p class="hint">These links open external hosted model providers. Unlike local generation, online use sends prompts/media to a third party. Free GPU quotas or login may apply.</p>
<a href="https://huggingface.co/spaces/Tongyi-MAI/Z-Image-Turbo" target="_blank" rel="noopener noreferrer">Online image generator ↗</a><br>
<a href="https://huggingface.co/spaces/Wan-AI/Wan2.1" target="_blank" rel="noopener noreferrer">Online AI video generator ↗</a>
</section>
<p class="hint">Privacy: the API secret remains in this page's memory and is never saved to localStorage.
When sharing over Wi-Fi, HTTP is not encrypted. Use only trusted private networks. Native video inference has not been validated on all Android phones and may run out of memory.</p>
<script>
const byId=id=>document.getElementById(id);
const token=()=>byId('token').value.trim();
let activeId='',poller=null;
async function api(path,method='GET',data=null){
  if(!token())throw Error('Paste the device API token first.');
  const options={method,headers:{'X-RealFrame-Token':token()},cache:'no-store'};
  if(data!==null){options.headers['Content-Type']='application/json';options.body=JSON.stringify(data);}
  const r=await fetch(path,options);
  const response=await r.json().catch(()=>({error:'Invalid HTTP reply'}));
  if(!r.ok)throw Error(response.error||('HTTP '+r.status));
  return response;
}
async function health(){
  try{byId('health').textContent=JSON.stringify(await api('/v1/status'),null,2);}
  catch(e){byId('health').textContent='Error: '+e.message;}
}
function renderJob(job){
  byId('result').textContent=JSON.stringify(job,null,2);
  if(job.download_paths?.length){
     const container=byId('media');container.replaceChildren();
     job.download_paths.forEach((path,i)=>{
       const button=document.createElement('button');
       button.textContent='⬇ Save output '+(i+1);
       button.onclick=()=>getMedia(path,job.kind);
       container.append(button);
     });
  }
}
async function getMedia(path,kind){
  try{
    const r=await fetch(path,{headers:{'X-RealFrame-Token':token()},cache:'no-store'});
    if(!r.ok)throw Error('Media download HTTP '+r.status);
    const blob=await r.blob();const url=URL.createObjectURL(blob);
    const a=document.createElement('a');a.href=url;
    a.download='realframe_local_'+Date.now()+(kind==='video'?'.mp4':'.png');
    document.body.append(a);a.click();a.remove();
    setTimeout(()=>URL.revokeObjectURL(url),10000);
  }catch(e){alert('Download failed: '+e.message);}
}
async function poll(){
  if(!activeId)return;
  try {
     const job=await api('/v1/jobs/'+activeId);
     renderJob(job);
     if(['completed','failed','cancelled'].includes(job.state)){
         clearInterval(poller);poller=null;
     }
  }catch(e){byId('result').textContent='Polling error: '+e.message;}
}
async function create(){
  try{
    if(poller){clearInterval(poller);poller=null;}
    const video=byId('kind').value==='video';
    const sizes=Number(byId('size').value);
    const index=Number(byId('count').value);
    const body={
      prompt:byId('prompt').value,negative_prompt:byId('negative').value,
      width:sizes,height:sizes,steps:Number(byId('steps').value),
      seed:Number(byId('seed').value)
    };
    body[video?'frames':'n']=video?[5,9,17,33][index-1]:index;
    byId('result').textContent='Submitting native '+(video?'video':'image')+' job...';
    const job=await api(video?'/v1/videos/generations':'/v1/images/generations','POST',body);
    activeId=job.id;renderJob(job);poll();poller=setInterval(poll,2500);
  }catch(e){byId('result').textContent='Error: '+e.message;}
}
async function cancel(){
  if(!activeId)return;
  try{renderJob(await api('/v1/jobs/'+activeId+'/cancel','POST',{}));}
  catch(e){byId('result').textContent='Cancel: '+e.message;}
}
</script></body></html>
""";
    }
}
