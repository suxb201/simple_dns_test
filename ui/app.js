const {app, BrowserWindow} = require('electron')

function createWindow() {
    // 创建浏览器窗口
    win = new BrowserWindow({
        width: 800, height: 600, webPreferences: {
            nodeIntegration: true
        }
    })
    let url
    if(process.env.NODE_ENV === 'development'){
        url='http://localhost:3000'
    }else{
        const path = require('path')
        url=`file://${(path.join(__dirname,"dist",'index.html'))}`
    }
    // 然后加载应用的 index.html。
    win.loadURL(url)
    win.webContents.openDevTools()
    app.on('will-quit', () => {
        win.webContents.send("exit")
    })

}

app.on('ready', createWindow)


