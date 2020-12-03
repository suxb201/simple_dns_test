<template>
  <a-layout style="height: 100%;">
    <a-layout-header class="header">
      <a-space :size="middle">
        <div class="button-left">
          <a-button @click="startbutton">{{
              serverButtonShow
            }}
          </a-button>
        </div>
        <!--                <a-button>应用 hosts</a-button>-->
        <a-button @click="clearhost">恢复 hosts</a-button>
      </a-space>
    </a-layout-header>
    <a-layout-content class="content">
      <a-table
          :columns="columns"
          :data-source="tableData"
          :scroll="{ y: '460px' }"
          :pagination="false"
      >
        <template #op="{ text }">
          <a-button type="danger" @click="deleteDnsServer(text)">
            删除
          </a-button>
        </template>
      </a-table>
    </a-layout-content>
    <a-layout-footer class="footer">
      <a-space>
        <a-input
            placeholder="dns ip"
            v-model:value="addnewdata.ip"
        ></a-input>
        <a-input
            placeholder="备注"
            v-model:value="addnewdata.describe"
        ></a-input>
        <a-button @click="addDnsServer">添加</a-button>
      </a-space>
    </a-layout-footer>
  </a-layout>
</template>
<style scoped>
.header,
.content,
.footer {
  background-color: rgb(250, 250, 250);
}

.header {
  margin-top: 10px;
}
</style>
<script>
import {
  ref,
  inject,
  reactive,
  onBeforeMount,
  watch,
  getCurrentInstance,
} from "vue"

function getpath(pathname) {
  const path = require("path")
  let templateFilePath = path.join(process.cwd(), "/resources/", pathname)
  if (process.env.NODE_ENV === "development") {
    templateFilePath = path.join(process.cwd(), pathname)
  }
  return templateFilePath
}

export default {
  name: "App",
  setup() {
    const {ctx} = getCurrentInstance()
    const message = inject("message")
    const serverButtonShow = ref("启动")
    const ipcRenderer = require("electron").ipcRenderer
    const startbutton = () => {
      if (serverButtonShow.value === "启动") {
        message.success("冲!")
        ipcRenderer.send("start")
        serverButtonShow.value = "关闭"
      } else {
        serverButtonShow.value = "启动"
        message.warning("停!")
        ipcRenderer.send("stop")
      }
    }
    //表头
    const columns = [
      {dataIndex: "ip", title: "nameserver", width: 80},
      {dataIndex: "describe", title: "备注", width: 150},
      {title: "op", slots: {customRender: "op"}, width: 60},
    ]
    //表格数据
    const tableData = reactive([])
    ipcRenderer.on("message",(event,msg)=>{
      message[msg[0]](msg[1])
    })
    //挂载之前的声明周期 读入配置文件 向tabledata中添加数据
    onBeforeMount(() => {
      // title bar
      const path = require("path")
      const url = require("url")
      const customTitlebar = require("custom-electron-titlebar")
      new customTitlebar.Titlebar({
        backgroundColor: customTitlebar.Color.fromHex("#444"),
        menu: null,
      })
      // load nameservers
      const fs = require("fs")
      const toml = require("@iarna/toml")

      const configpath = getpath("pyexe/config.toml")
      const filestr = fs.readFileSync(configpath, {encoding: "utf-8"})

      const data = toml.parse(filestr)
      // tableData.value = tableData.concat(data.dns.nameserver)
      // tableData.push(...data.dns.nameserver)
      console.log(ctx)
      if (ctx && ctx._ && ctx._.nextTick) {
        ctx._.nextTick(() => {
          tableData.push(...data.dns.nameserver)
        })
      } else {
        tableData.push(...data.dns.nameserver)
      }

    })

    //删除dns view
    const deleteDnsServer = (data) => {
      const index = tableData.findIndex((t) => t.ip === data.ip)
      //删掉 响应式
      tableData.splice(index, 1)
    }

    //添加dns view
    const addnewdata = reactive({ip: "", describe: ""})
    const addDnsServer = () => {
      if (tableData.find((t) => t.ip === addnewdata.ip)) {
        message.warning("加过了 球球了别加了")
        return
      }
      tableData.push({
        ...addnewdata,
      })
    }
    //配置文件响应式 负责重启服务以及更改config
    watch(tableData, () => {
      const fs = require("fs")
      const toml = require("@iarna/toml")

      console.log("---------------tableData changed ------------")

      const configpath = getpath("pyexe/config.toml")

      let filestr = fs.readFileSync(configpath, {encoding: "utf-8"})

      let data = toml.parse(filestr)

      data.dns.nameserver.length = 0
      data.dns.nameserver.push(...tableData)

      filestr = toml.stringify(data)
      //写入
      fs.writeFileSync(configpath, filestr)
      //重启
      if(serverButtonShow.value==="关闭"){
        ipcRenderer.send("stop")

        setTimeout(() => {
          ipcRenderer.send("start")
        }, 1000)
      }
    })
    const clearhost = () => {
      ipcRenderer.send("stop")
      ipcRenderer.send("clear")
      serverButtonShow.value = "启动"
    }
    return {
      startbutton,
      tableData,
      columns,
      deleteDnsServer,
      addDnsServer,
      addnewdata,
      serverButtonShow,
      clearhost
    }
  },
}
</script>
