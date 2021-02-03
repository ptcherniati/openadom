<template>
  <div>
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
    <v-snackbar
      top
      vertical
      v-model="snackbar.visible"
    >
      {{ snackbar.text }}
      <v-btn
        flat
        @click="snackbar.visible=false"
        class="red--text"
      >
        fermer
        <v-icon color="red">
          mdi-close
        </v-icon>
      </v-btn>
    </v-snackbar>
  </div>
</template>

<script>
import EventBus from '@/eventBus';
export default {
  name: "UploadReference",
  mounted() {
    EventBus.$on("reference:uploaded", event => {
      if (event.id) {
        this.snackbar.text="la donnée de référence a été chargée"
        this.snackbar.visible=true;
      } else {
        this.snackbar.text="la donnée de référence n'a pas pu être chargée"
        this.snackbar.visible=true;
      }
    });
  },
  computed: {
    applicationName: {
      get() {
        return this.$route.params.applicationName;
      }
    },
    referenceName: {
      get() {
        return this.$route.params.referenceName;
      }
    },
  },
  data() {
    return {
      file: null,
      snackbar: {}
    };
  },
  methods: {
    handleFileUpload(event) {/** */
      this.file = this.$refs.file.files[0];
    },
    uploadReference() {
      if (file != null) {
        this.$store.dispatch("uploadReference", {
          applicationName: this.applicationName,
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
</style>
