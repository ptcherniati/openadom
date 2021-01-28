<template>
  <div class="rights">
    <div @load="init">
      <div class="tree">
        <RightNode 
          v-if="pstd!=null"
          name="root"
          :deep="1"
          :pstd.sync="pstd"
          :dvu.sync="dvu"
          :configuration.sync="init"
        />
      </div>
    </div>
  </div>
</template>

<script>
import RightNode from "@/components/RightNode";
import http from "@/http/http";
import config from "@/config";
import { storage, Storage } from "@/storage";
export default {
  name: "Rights",
  computed: {
    pstdReference() {
      const reference = this.rights.references.filter(
        reference =>
          reference.name == "types_de_donnees_par_themes_de_sites_et_projet"
      )[0];
      this.getReference(reference, "pstd");
      return reference;
    },
    dvuReference() {
      const reference = this.rights.references.filter(
        reference =>
          reference.name == "variables_et_unites_par_types_de_donnees"
      )[0];
      this.getReference(reference, "dvu");
      return reference;
    },
    init() {
      let inited = {
        pstdReference: this.pstdReference,
        dvuReference: this.dvuReference
      };
      this.$emit("pstAndDvuInited", inited);
      return inited;
    },
  },
  props: {
    rights: Object
  },
  data() {
    return {
      pstd_: {},
      dvu: null,
      deep: 0,
      comlums: {},
      visible:false,
    };
  },
  methods: {
    getReference(reference, to) {
      return http
        .loadReference(reference, config.APPLICATION_NAME)
        .then(response => {
          storage.set(Storage.TOKEN_KEY, response.data.token);
          this[to] = response.data;
          if (to == "pstd") {
            this["pstd_"] = this.getPstd(reference.columns, response.data);
          }
          this.$emit("update:" + to, response.data);
        })
        .catch(error => {
          this.pstd = {};
          this.$emit("error_" + to, error);
        });
    },
    getPstd(columns, data) {
      if (Object.keys(columns).length == 0) {
        return {};
      }
      let column = Object.values(columns)[this.deep].name;
      let pstd = {};
      data.forEach(element => {
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
  components: {
    RightNode
  }
};
</script>

<style scoped lang="scss">
.tree-list ul {
  padding-left: 16px;
  margin: 6px 0;
}
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
