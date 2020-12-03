const path = require("path");
const getpath = require("./getpath.js");

let script = getpath(path.join("pyexe", "main", "main.exe"));
let scriptconfig = getpath(path.join("pyexe", "config.toml"));


function newprocess(){
    let pyProc = require("child_process").execFile(script, ["-c", scriptconfig]);

    pyProc.stdout.on("data", (data) => {
        console.log(`stdout: ${data}`);
    });
    pyProc.stderr.on("data", (data) => {
        console.log(`${data}`);
    });
    pyProc.on("close", (code) => {
        console.log(`child_process exit，code= ${code}`);
    });
    return pyProc
}
module.exports.proc = newprocess;
