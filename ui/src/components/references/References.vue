<template>
  <v-select
    v-model="referenceName"
    :items="referenceTypes"
    item-text="name"
    label="Choisissez une donnée de référence"
  />
</template>

<script>
//import ShowReference from "@/components/references/ShowReference";
//import UploadReference from "@/components/references/UploadReference";
export default {
  name: "References",
  computed: {
    referenceTypes: {
      get() {
        return this.$store.state.referenceType
      }
    },
  },
  data() {
    return {
      referenceName: null
    };
  },
  watch: {
    referenceName() {
      this.$router.push({ name: 'showReference', params: { referenceName: this.referenceName } })
    } 
  },
  components: {
    //ShowReference,
    //UploadReference,
  }
};
</script>

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
