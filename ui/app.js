const { app, BrowserWindow, ipcMain, Menu } = require("electron")
const path = require("path")

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
    win.webContents.openDevTools()

    let pyProc
    ipcMain.on("start", () => {
        console.log("启动!")
        if (!pyProc || pyProc.killed) {
            pyProc = require("./subprocess.js").proc
        }
        if (pyProc != null) {
            console.log("child process success")
        }
    })
    const stop = () => {
        if (pyProc) {
            pyProc.kill()
        }
    }
    ipcMain.on("stop", stop)
    app.on("will-quit", stop)
}

app.on("ready", createWindow)
