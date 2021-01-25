<template>
  <v-expansion-panel
    inset
    id="uploadApplicationPanel"
  >
    <v-expansion-panel-content>
      <template v-slot:header>
        <v-btn>Enregistrer une nouvelle application</v-btn>
      </template>
      <v-card>
        <v-form
          title="Enregistrer une nouvelle application"
          ref="form"
          v-model="valid"
          lazy-validation
        >
          <v-card>
            <v-card-title
              primary-title
              class="pink lighten-5"
            >
              <div>
                <v-layout tag="h3">
                  Enregistrer une nouvelle configuration d'application
                </v-layout>
                <v-text-field
                  v-model="applicationName"
                  :rules="nameRules"
                  label="Nom de l'application"
                  required
                />
                <v-text-field
                  label="Choisissez-un fichier"
                  v-model="fileToUploadName"
                  :rules="fileRules"
                  append-icon="mdi-upload"
                  @click="pickFile"
                  required
                >
                  <v-icon>mdi-upload</v-icon>
                </v-text-field>
                <input
                  style="display:none"
                  type="file"
                  id="applicationfileUpload"
                  ref="file"
                  @change="handleFileUpload()"
                >
                <v-btn
                  :disabled="!valid"
                  color="success"
                  @click="validate"
                >
                  Enregistrer une nouvelle application
                </v-btn>
              </div>
            </v-card-title>
          </v-card>
        </v-form>
      </v-card>
      <v-snackbar
        top
        vertical
        v-model="snackbar.visible"
      >
        {{ snackbar.text }}
        <v-btn
          flat
          @click="snackbar.visible = false"
          class="red--text"
        >
          fermer
          <v-icon color="red">
            mdi-close
          </v-icon>
        </v-btn>
      </v-snackbar>
    </v-expansion-panel-content>
  </v-expansion-panel>
</template>

<script>
//import { storage, Storage } from "@/storage";
import store from "@/store";
import EventBus from "@/eventBus";
export default {
  name: "UploadApplication",
  mounted() {
    EventBus.$on("application:loaded", event => {
      if (event.id) {
        this.snackbar.text = "la configuration a été chargée";
        this.snackbar.visible = true;
        store.dispatch("loadApplicationConfiguration", event.id);
      } else {
        let message = "la configuration n'a pas pu être chargée.";
        if (event.status == 302) {
          message += " La configuration ";
        } else if (event.status == 401) {
          message += " Vous n'êtes pas autorisés à charger une application";
        } else if (event.status == 403) {
          message += " Vous n'avez pas les droits pour charger une application";
        } else if (event.status == 404) {
          message = "Le service est indisponible pour le moment.";
        }
        this.snackbar.text = message;
        this.snackbar.visible = true;
      }
    });
  },
  computed: {
    application: {
      get() {
        return store.state.application;
      }
    }
  },
  props: {},
  data() {
    return {
      snackbar: {
        visible: false,
        text: ""
      },
      valid: false /**/,
      fileName: null,
      fileToUploadName: null,
      file: null,
      applicationName: null /* */,
      nameRules: [
        /* */
        v => !!v || "Le nom de l'application est requis",
        v =>
          (v && v.length >= 4 && v.length <= 20) ||
          "le nom doit être compris en 4 et 20 caractères.",
        v =>
          (v && v.match("^[a-zA-Z][a-zA-Z0-9_]*$") != null) ||
          "le nom commence par une letrre et ne comporte que des lettres et des chiffre ou _."
      ],
      fileRules: [
        /* */
        v => !!v || "Le fichier est requis"
      ]
    };
  },
  methods: {
    handleFileUpload() {
      /** */
      this.file = this.$refs.file.files[0];
      this.fileToUploadName = this.file.name;
    },
    validate() {
      /** */
      if (this.$refs.form.validate() && this.file != null) {
        store.dispatch("loadApplication", {
          applicationName: this.applicationName,
          file: this.file
        });
        this.resetValidation();
        this.valid = false;
      }
    },
    resetValidation() {
      /** */
      this.file = null;
      this.fileToUploadName = null;
      this.$refs.form.resetValidation();
    },
    pickFile() {
      this.$refs.file.click();
    }
  },
  components: {}
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
