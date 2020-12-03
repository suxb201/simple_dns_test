const {app, BrowserWindow, ipcMain, Menu} = require("electron")
const path = require("path")
const hostpath = path.join(process.env.windir, 'System32', 'drivers', 'etc')
const fs = require('fs')

function createWindow() {
    // 创建浏览器窗口
    win = new BrowserWindow({
        width: 500,
        height: 700,
        frame: false,
        resizable: false, //禁止改变主窗口尺寸
        webPreferences: {
            enableRemoteModule: true,
            nodeIntegration: true, // 继承 node
        },
        titleBarStyle: "hidden", // add this line
    })

    let url
    if (process.env.NODE_ENV === "development") {
        url = "http://localhost:3000"
    } else {
        const path = require("path")
        url = `file://${path.join(__dirname, "dist", "index.html")}`
    }
    // 然后加载应用的 index.html。
    win.loadURL(url)
    if (process.env.NODE_ENV === "development") {
        win.webContents.openDevTools()
    }

    let pyProc
    let interval
    ipcMain.on("start", () => {
        console.log("start!")
        if (!pyProc || pyProc.killed) {
            try {
                if (!fs.existsSync(path.join(hostpath, 'hosts.bak')) && fs.existsSync(path.join(hostpath, 'hosts'))) {
                    fs.copyFileSync(path.join(hostpath, 'hosts'), path.join(hostpath, 'hosts.bak'))
                    console.log("备份完成")
                }
            } catch (e) {
                win.webContents.send("message", ["warning", "没有权限" + path.join(hostpath, 'hosts')])
            }

            pyProc = require("./subprocess.js").proc()
            if (interval) clearInterval(interval)
            interval = setInterval(() => {
                try {
                    if (fs.existsSync(path.join(process.cwd(), "hosts"))) {
                        fs.copyFileSync(path.join(process.cwd(), "hosts"), path.join(hostpath, 'hosts'))
                        console.log("写入hosts")
                    }
                } catch (e) {
                    win.webContents.send("message", ["warning", "没有权限" + path.join(hostpath, 'hosts')])
                }

            }, 10 * 1000);
        }
        if (pyProc != null) {
            console.log("child process success")
        }
    })
    const stop = () => {
        console.log("start stoping")
        if (interval) clearInterval(interval)
        interval = null
        if (pyProc) {
            pyProc.kill()
            pyProc = null
        }
        console.log("stoping finish")
    }
    ipcMain.on("stop", stop)
    ipcMain.on("clear", () => {
        try {
            fs.copyFileSync(path.join(hostpath, 'hosts.bak'), path.join(hostpath, 'hosts'))
            fs.rmdirSync(path.join(hostpath, 'hosts.bak'))
        } catch (e) {
            win.webContents.send("message", ["warning", "没有权限" + path.join(hostpath, 'hosts')])
        }
    })
    app.on("will-quit", stop)
}

app.on("ready", createWindow)
