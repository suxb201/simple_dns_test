const path = require('path')
let script = path.join(__dirname, 'pyexe', 'main.exe')
let scriptconfig = path.join(__dirname, 'pyexe', 'config.toml')
let pyProc = require('child_process').execFile(script,["-c",scriptconfig])
module.exports.proc=pyProc