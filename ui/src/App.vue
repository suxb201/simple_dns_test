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
    <a-layout-footer>Footer</a-layout-footer>
  </a-layout>
</template>
<style scoped>
.header {
  background-color: aliceblue;
}
</style>
<script>
import {inject, reactive} from "vue"

export default {
  name: 'App',
  setup() {
    const message = inject("message")

    const startbutton = () => {
      message.success("冲!")
    }
    //表头
    const columns = [{dataIndex: 'nameserver', title: "ip"},
      {dataIndex: '备注'},
      {title: 'op', slots: {customRender: 'op'}}]
    //表格数据
    const tableData = reactive([
      {
        nameserver: "1.1.1.1",
        备注: '阿巴阿巴',
      },
      {
        nameserver: "1.1.1.2.3",
        备注: '阿巴阿巴',
      },
    ])
    const deleteDnsServer=(data)=>{
      const index=tableData.findIndex(t=>t.nameserver===data.nameserver)
      //删掉 响应式
      tableData.splice(index,1)

    }

    return {startbutton, tableData, columns,deleteDnsServer}
  }
}
</script>
