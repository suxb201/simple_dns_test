function getpath(pathname) {
    const path = require("path")
    console.log(process.cwd())
    console.log(process.env.NODE_ENV)
    let templateFilePath = path.join(process.cwd(), "/resources/", pathname)
    if (process.env.NODE_ENV === "development") {
        templateFilePath = path.join(__dirname, pathname)
    }
    return templateFilePath
}
module.exports = getpath
