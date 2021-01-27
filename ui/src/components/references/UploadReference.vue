<template>
  <div>
    <v-layout
      v-if="referenceType!=null"
      wrap
      align-center
    >
      <v-flex
        md8
        offset-md2
        fluid
      >
        <v-select 
          v-model="referenceName"
          solo
          name="references"
          :items="referenceType"
          item-text="name"
          label="Choisissez une donnée de référence"
        />
      </v-flex>
      <v-btn
        flat
        class="body-2"
      >
        {{ referenceName }}
      </v-btn>
    </v-layout>
    <v-layout md6>
      <v-card v-if="referenceName!=null">
        <v-card-title
          primary-title
          class="pink lighten-5"
        >
          <div>
            <h3 class="headline mb-0">
              Charger la donnée de référence  "{{ referenceName }}"
            </h3>
            <input
              type="file"
              id="file"
              ref="file"
              @change="handleFileUpload()"
            >
            <v-btn
              v-show="file!=null && referenceName!=null"
              color="success"
              @click="uploadReference"
            >
              Enregistrer {{ referenceName }}
            </v-btn>
          </div>
        </v-card-title>
      </v-card>
    </v-layout>
  </div>
</template>

<script>
import EventBus from '@/eventBus';
export default {
  name: "UploadReferences",
  mounted() {
    EventBus.$on("reference:uploaded", event => {
      if (event.id) {
        this.snackbar.text="la donnée de référence a été chargée"
        this.snackbar.visible=true;
        this.setReference(event.id)
      }else {
        this.snackbar.text="la donnée de référence n'a pas pu être chargée"
        this.snackbar.visible=true;
      }
    });
  },
  computed: {
    referenceType :{
      get(){
        return this.$store.state.referenceType==null?[]:this.$store.state.referenceType
      }
    },
    application: {
      get() {
        return this.$store.state.application;
      }
    },
    referenceName: {
      get() {
        return this.$store.state.referenceName;
      },
      set(referenceName){
        this.$store.state.referenceName = referenceName;
      }
    },
  },
  props: {},
  data() {
    return {
      valid: false,/**/
      file: null,
      snackbar:{}
    };
  },
  methods: {
    setReference(referenceName) {
      this.$store.dispatch("loadReference", {
        referenceName: referenceName,
        referenceDescription: this.references[referenceName],
      });
    },
    handleFileUpload(event) {/** */
      this.file = this.$refs.file.files[0];
    },
    uploadReference(){
      if (file != null) {
        this.$store.dispatch("uploadReference", {
          referenceName: this.referenceName,
          file: this.file
        });
        this.resetValidation();
      }
    },
    resetValidation() {/** */
      this.file = null;
    }
  },
  components: {
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
