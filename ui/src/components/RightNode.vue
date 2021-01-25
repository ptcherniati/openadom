<template>
  <div
    class="rightsNode"
    :style="getStyle()"
  >
    <ul
      v-for="(node, key) in pstd_"
      class="tree-list"
      :key="key"
    >
      <span
        @click="visible=!visible"
        class="label"
      >{{ key }}</span>
      <RightNode
        v-if="visible"
        name="root"
        :deep="1"
        :pstd.sync="node"
        :dvu.sync="dvu"
      />
    </ul>
    <!--table>
      <tr v-for="(value,key) in pstd" :key="key">
        <td>{{key}}</td>
        <RightNode :name="key" :deep="deep+1" :pstd="value" :dvu="dvu"/>
      </tr>
    </table-->
  </div>
</template>

<script>
export default {
  name: "RightsNode",
  computed: {
    pstd_() {
      let columns = this.configuration.pstdReference.columns;
      if (Object.keys(columns).length == 0) {
        return {};
      }
      let column = Object.values(columns)[this.deep].name;
      let pstd = {};
      this.pstd.forEach(element => {
        let value = element.refValues[column];
        if (pstd[value] == null) {
          pstd[value] = [element];
        } else {
          pstd[value].push(element);
        }
      });
      return pstd;
    }
  },
  props: {
    deep: Number,
    dvu: Array,
    pstd: Array,
    configuration: Object
  },
  data() {
    return {
      visible: false
    };
  },
  methods: {
    getStyle() {
      let marge = this.deep * 5;
      return "margin: " + marge + "px 10px;";
    }
  },
  components: {
  }
};
</script>

<!-- Add "scoped" attribute to limit CSS to this component only -->
<style scoped lang="scss">
.references > table > tr > td {
  border: solid 1px red;
}
h3 {
  margin: 40px 0 0;
}
ul {
  list-style-type: none;
  padding: 0;
}
li {
  display: inline-block;
  margin: 0 10px;
}
a {
  color: #42b983;
}
</style>
