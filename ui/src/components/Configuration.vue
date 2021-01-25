<template>
  <div class="configuration">
    <button @click="activeDataset()">
      Datasets
    </button>
    <button @click="activeReferences()">
      References
    </button>
    <button @click="activeRights()">
      Rights
    </button>
    <References
      v-if="selectedPanel=='references'"
      :references="configuration.references"
    />
    <Datasets
      v-if="selectedPanel=='datasets'"
      :datasets="configuration.datasets"
    />
    <Rights
      v-if="selectedPanel=='rights'"
      :rights="configuration"
    />
  </div>
</template>

<script>
import References from "@/components/references/References";
import Datasets from "@/components/Datasets";
import Rights from "@/components/Rights";
export default {
  name: "Configuration",
  computed: {},
  props: {
    //configuration: Configuration,
  },
  data(){
    return {
      selectedPanel: "rights"
    };
  },
  methods: {
    activeDataset(){
      this.selectedPanel="datasets"
    },
    activeReferences(){
      this.selectedPanel="references"
    },
    activeRights(){
      this.selectedPanel="rights"
    }
  },
  components: {
    References,
    Datasets,
    Rights,
  }
};
</script>

<!-- Add "scoped" attribute to limit CSS to this component only -->
<style scoped lang="scss">
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
