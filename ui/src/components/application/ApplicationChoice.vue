<template>
  <v-container my-2>
    <v-layout
      v-if="applications.length>0"
      wrap
      text-md-center
    >
      <v-flex
        md8
        offset-md2
        d-flex
        cyan
        lighten-5
      >
        <v-overflow-btn
          @input="setApplication"
          segmented
          editable
          name="references"
          :items="applications"
          item-text="name"
          label="Choisissez une application"
        />
      </v-flex>
      <v-flex
        md1
        offset-md1
        fluid
        v-if="applicationName"
      >
        <v-tooltip bottom>
          <template v-slot:activator="{ on }">
            <v-btn
              tag="a"
              :href="url"
              color="primary"
              flat
              v-on="on"
            >
              {{ applicationName }}
              <v-icon
                right
                x-large
                color="success"
              >
                mdi-upload
              </v-icon>
            </v-btn>
          </template>
          <span>Télécharger lme fichier de configuration de {{ applicationName }}</span>
        </v-tooltip>
      </v-flex>
    </v-layout>
    <v-layout
      v-else
      wrap
      align-center
    >
      <v-flex
        xs12
        sm6
        d-flex
      >
        Aucune application n'est disponible pour vos droits
      </v-flex>
    </v-layout>
  </v-container>
</template>

<script>
import Configuration from "@/components/Configuration";
import EventBus from "@/eventBus";
import config from "@/config";
export default {
  name: "ApplicationChoice",
  mounted() {
  },
  computed: {
    applications: {
      get() {
        return this.$store.state.applications;
      }
    },
    application: {
      get() {
        // eslint-disable-next-line vue/no-side-effects-in-computed-properties
        this.url = config.API_URL+"files/"+(this.$store.state.application==null?'':this.$store.state.application.configFile)
        return this.$store.state.application;
      },
      set(application){
        this.setApplication(application.name);
      }
    },
    url: {
      get() {
        return config.API_URL+"files/"+(this.$store.state.application==null?'':this.$store.state.application.configFile)
      },
      set(url){

      }
    },
    applicationName: {
      get() {
        return this.$store.state.applicationName;
      },
      set(applicationName){
        this.setApplication(applicationName);
      }
    }
  },
  props: {},
  data() {
    return {
      sheet:null,
    };
  },
  methods: {
    setApplication(application) {
      this.$store.dispatch("loadApplicationConfiguration", application);
    },
  },
  components: {
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
