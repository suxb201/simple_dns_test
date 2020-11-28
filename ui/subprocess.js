const path = require('path')
let script = path.join(__dirname, 'pyexe', "main",'main.exe')
let scriptconfig = path.join(__dirname, 'pyexe', 'config.toml')
let pyProc = require('child_process').execFile(script,["-c",scriptconfig])
// let pyProcx = require('child_process').spawn( "python",[path.join(__dirname, 'text.py'),"-c","aaa"])
//
pyProc.stdout.on('data', (data) => {
    console.log(`stdout: ${data}`);
});
pyProc.stderr.on('data', (data) => {
    console.error(`stderr: ${data}`);
});
module.exports.proc=pyProc