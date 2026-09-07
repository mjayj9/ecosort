// Actual Firebase/NIM regression. User state UNUSED is an explicit test scenario, not visual proof.
const fs=require('node:fs'),path=require('node:path'),crypto=require('node:crypto'),assert=require('node:assert/strict');
const {execFileSync}=require('node:child_process');
const report={realNim:true,cameraUsed:false,accuracyBenchmark:false,userStateIsTestInput:true,rows:[]};
async function main(){
 const [output,...photos]=process.argv.slice(2);assert.ok(output&&photos.length);
 const cfg=JSON.parse(fs.readFileSync(path.join(__dirname,'../../app/google-services.json'),'utf8').replace(/^\uFEFF/,''));
 const client=cfg.client.find(x=>x.client_info.android_client_info.package_name==='com.aistudio.ecosort.kxmpzq');const key=client.api_key[0].current_key;
 const adb=path.join(process.env.LOCALAPPDATA,'Android/Sdk/platform-tools/adb.exe');const args=['-s',process.env.ECOSORT_DEVICE||'emulator-5556','shell','run-as','com.aistudio.ecosort.kxmpzq'];
 const file=execFileSync(adb,[...args,'ls','shared_prefs'],{encoding:'utf8',windowsHide:true}).trim().split(/\r?\n/).find(x=>x.startsWith('com.google.firebase.appcheck.debug.store.'));assert.ok(file&&/^[-A-Za-z0-9_.+]+$/.test(file));
 const token=execFileSync(adb,[...args,'cat','shared_prefs/'+file],{encoding:'utf8',windowsHide:true}).match(/>([a-f0-9]{8}-[a-f0-9-]{27})</i)?.[1];assert.ok(token);
 const app='projects/'+cfg.project_info.project_number+'/apps/'+client.client_info.mobilesdk_app_id;
 const exchange=await fetch('https://firebaseappcheck.googleapis.com/v1/'+app+':exchangeDebugToken?key='+key,{method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify({debugToken:token})});assert.equal(exchange.status,200);const appCheck=(await exchange.json()).token;
 const signup=await fetch('https://identitytoolkit.googleapis.com/v1/accounts:signUp?key='+key,{method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify({returnSecureToken:true})});assert.equal(signup.status,200);const auth=await signup.json();
 const headers={'Content-Type':'application/json',Authorization:'Bearer '+auth.idToken,'X-Firebase-AppCheck':appCheck};const base='https://us-central1-'+cfg.project_info.project_id+'.cloudfunctions.net/';
 async function call(data){const start=Date.now();const r=await fetch(base+'analyzeImage',{method:'POST',headers,body:JSON.stringify({data}),signal:AbortSignal.timeout(125000)});return {http:r.status,ms:Date.now()-start,body:await r.json()};}
 try{
  for(const photo of photos){
   const answers={purpose:'PREVIEW',useState:'UNUSED'};
   const first=await call({schemaVersion:2,operation:'analyze',requestId:crypto.randomUUID(),image:fs.readFileSync(photo).toString('base64'),answers});
   const row={photo:path.basename(photo),initial:first};report.rows.push(row);
   assert.equal(first.http,200);assert.equal(first.body.result.contaminationScore,null);assert.equal(first.body.result.decision,null);
   const result=first.body.result;
   if(result.questions.some(q=>q.key==='material')){
    const corrected=await call({schemaVersion:2,operation:'resolve',requestId:crypto.randomUUID(),scanId:result.scanId,expectedRevision:result.revision,answers:{...answers,material:'CARTON_ASEPTIC'}});
    row.afterLabelConfirmation=corrected;assert.equal(corrected.http,200);assert.equal(corrected.body.result.status,'PREVIEW');
   }
   console.log(JSON.stringify({photo:row.photo,http:first.http,ms:first.ms,status:result.status,item:result.itemName,afterLabel:row.afterLabelConfirmation?.body.result.status}));
  }
 }finally{
  const r=await fetch(base+'deleteAccount',{method:'POST',headers,body:JSON.stringify({data:{}})});report.cleanup={http:r.status,success:(await r.json()).result?.success};
  fs.writeFileSync(output,JSON.stringify(report,null,2)+'\n');assert.equal(report.cleanup.success,true);
 }
}
main().catch(e=>{console.error(JSON.stringify({failed:true,type:e.name,code:e.code}));process.exitCode=1;});
