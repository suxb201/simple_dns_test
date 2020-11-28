<template>
  <a-layout style="height: 100%">
    <a-layout-header class="header">
      <a-space :size="middle">
        <a-button @click="startbutton">启动</a-button>
        <a-button>应用hosts</a-button>
        <a-button>恢复hosts</a-button>
      </a-space>

    </a-layout-header>
    <a-layout-content>
      <a-table :columns="columns"
               :data-source="tableData"
               :scroll="{ y: 480 }"
               :pagination="false">
        <template #op="{text}">
          <a-button type="danger" @click="deleteDnsServer(text)">
            删除
          </a-button>

        </template>
      </a-table>
    </a-layout-content>
    <a-layout-footer>

      <a-space>
        <a-input placeholder='dns ip' v-model:value="addnewdata.nameserver"></a-input>
        <a-input placeholder='备注' v-model:value="addnewdata.ps"></a-input>
        <a-button @click="addDnsServer">添加</a-button>
      </a-space>
    </a-layout-footer>
  </a-layout>
</template>
<style scoped>
.header {
  background-color: aliceblue;
}
</style>
<script>
import {inject, reactive, onBeforeMount, watch} from "vue"

function getpath(pathname){
  const path=require("path")
  let templateFilePath = path.join(process.cwd(), '/resources/',pathname)
  if (process.env.NODE_ENV === 'development') {
    templateFilePath = path.join(process.cwd(), pathname)
  }
  return templateFilePath
}
export default {
  name: 'App',
  setup() {
    const message = inject("message")

    const startbutton = () => {
      message.success("冲!")
    }
    //表头
    const columns = [{dataIndex: 'nameserver', title: "ip"},
      {dataIndex: 'ps',title:"备注"},
      {title: 'op', slots: {customRender: 'op'}}]
    //表格数据
    const tableData = reactive([
      {
        nameserver: "1.1.1.1",
        ps:'阿巴阿巴',
      },
      {
        nameserver: "1.1.1.2.3",
        ps: '阿巴阿巴',
      },
    ])

    //挂载之前的声明周期 读入配置文件 向tabledata中添加数据
    onBeforeMount(() => {

    })

    //删除dns view
    const deleteDnsServer = (data) => {
      const index = tableData.findIndex(t => t.nameserver === data.nameserver)
      //删掉 响应式
      tableData.splice(index, 1)
    }

    //添加dns view
    const addnewdata=reactive({nameserver:"",ps:""})
    const addDnsServer=()=>{
      if(tableData.find(t => t.nameserver === addnewdata.nameserver)){
        message.warning("加过了 球球了别加了")
        return
      }
      tableData.push({
       ...addnewdata
      })
    }
    //配置文件响应式 负责重启服务以及更改config
    watch(tableData, () => {
      //当tabledata有任何形式的更改时 都可以在这里进行回调
      console.log("tabledata changed")
      const fs=require("fs")
      console.log(fs)
      const configpath=getpath("pyexe/config.toml")
      console.log(configpath)
      const filestr=fs.readFileSync(configpath,{encoding:'utf-8' })
      console.log(filestr)

      //写入
      fs.writeFileSync(configpath,filestr)

    })

    return {startbutton, tableData, columns, deleteDnsServer,addDnsServer,addnewdata}
  }
}
</script>
