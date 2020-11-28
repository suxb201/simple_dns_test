let button = document.querySelector("#startserver")
let text = document.querySelector("#logoutput")
let{ipcRenderer}=require('electron')
let pyProc

ipcRenderer.on('exit',()=>{
    pyProc.kill()
    console.log('exit')
})
button.addEventListener('click', () => {
    text.textContent = "草草草!"
    alert("草")
    pyProc = require("./subprocess.js").proc
    if (pyProc != null) {
        console.log('child process success')
    }
    console.log(pyProc)
    pyProc.stdout.on('data', (data) => {
        console.log(`stdout: ${data}`);
    });
    pyProc.stderr.on('data', (data) => {
        console.error(`stderr: ${data}`);
    });
    pyProc.on('close', (code) => {
        console.log(`子进程退出，退出码 ${code}`);
    });

})
