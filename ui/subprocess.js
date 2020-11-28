const path = require('path')
let script = path.join(__dirname, 'pyexe', 'main.exe')
let scriptconfig = path.join(__dirname, 'pyexe', 'config.toml')
let pyProc = require('child_process').execFile(script,["-c",scriptconfig])
// let pyProcx = require('child_process').spawn( "python",[path.join(__dirname, 'text.py'),"-c","aaa"])
//
// pyProcx.stdout.on('data', (data) => {
//     console.log(`stdout: ${data}`);
// });
// pyProcx.stderr.on('data', (data) => {
//     console.error(`stderr: ${data}`);
// });
module.exports.proc=pyProc