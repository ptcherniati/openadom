<template>
  <v-container
    v-if="referenceType!=null"
    class="references"
    fluid
    grid-list-xl
  >
    <!--v-tabs 
      v-model="tabChoice"
      color="purple lighten-5"
      slider-color="red"
      centered grow light

      >
      <v-tab ripple > Afficher les données de référence </v-tab>
      <v-tab ripple > uploader une donnée de référence </v-tab>
      <v-tab ripple > Faire autre chose </v-tab>
      <v-tab-item-->
    <ShowReference />
    <!--/v-tab-item>
      <v-tab-item>
        <UploadReference />
      </v-tab-item>
      <v-tab-item>
        To be continued
      </v-tab-item>
    </v-tabs-->
  </v-container>
</template>

<script>
import ShowReference from "@/components/references/ShowReference";
//import UploadReference from "@/components/references/UploadReference";
export default {
  name: "References",
  computed: {
    referenceType :{
      get(){
        return this.$store.state.referenceType
      }
    },
  },
  props: {},
  data() {
    return {
      tabChoice : 0
    };
  },
  methods: {
  },
  components: {
    ShowReference,
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
