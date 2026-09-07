// Prints file names and finding types only; never prints matching credentials.
const {execFileSync}=require('node:child_process');
const staged=process.argv.includes('--staged');
const files=execFileSync('git',staged?['diff','--cached','--name-only','--diff-filter=ACMR','-z']:['ls-files','-z'],{encoding:'utf8'}).split('\0').filter(Boolean);
const rules=[['Google API key',/AIza[0-9A-Za-z_-]{35}/],['NVIDIA key',/nvapi-[A-Za-z0-9_-]{20,}/],['private key',/-----BEGIN (?:RSA |EC |OPENSSH )?PRIVATE KEY-----/],['GitHub token',/(?:ghp_[A-Za-z0-9]{30,}|github_pat_[A-Za-z0-9_]{40,})/],['OAuth token',/ya29\.[A-Za-z0-9_-]{30,}/]];
const findings=[];
for(const file of files){
 if(file==='app/google-services.json')findings.push({file,type:'local Firebase configuration must remain untracked'});
 const data=execFileSync('git',['show',`${staged?':':'HEAD:'}${file}`],{maxBuffer:32*1024*1024}).toString('utf8');
 for(const [type,regex] of rules)if(regex.test(data))findings.push({file,type});
}
console.log(JSON.stringify({scope:staged?'staged':'HEAD',files:files.length,findings}));
process.exitCode=findings.length?1:0;
