<template>
  <div class="application">
    <v-container v-if="user==null||!user.init">
      <v-flex
        v-if="application==null"
        md8
        offset-md2
      >
        <v-card
          class="title"
          color="green lighten-5"
        >
          <p class="text-md-left pl-md-60">
            Bienvenue dans l'application des SOERE.
          </p>
          <p class="text-md-left">
            Vous pouvez choisir une application existante,
          </p>
        </v-card>
      </v-flex>
      <ApplicationChoice />
      <v-flex
        v-if="application==null"
        md8
        offset-md2
      >
        <v-card
          class="title"
          color="green lighten-5"
        >
          <p class="text-md-left">
            ou bien charger la configuration d'une nouvelle application
          </p>
        </v-card>
      </v-flex>
      <v-flex
        v-if="application!=null"
        md8
        offset-md2
      >
        <v-card
          class="title"
          color="green lighten-5"
        >
          <p class="text-md-left pl-md-60">
            Bienvenue dans l'application de {{ application.name }}.
          </p>
          <p class="text-md-left">
            Vous pouvez afficher ou modifier les données de
            <code>{{ application.name }}</code>
            en utilisant les différents menus ci-dessus,
          </p>
          <p class="text-md-left">
            ou bien charger la configuration d'une nouvelle application
          </p>
        </v-card>
      </v-flex>
      <v-flex
        md8
        offset-md2
        v-if="!user||!user.init"
      >
        <UploadApplication />
      </v-flex>
    </v-container>
  </div>
</template>

<script>
import Configuration from "@/components/Configuration";
import ApplicationChoice from "@/components/application/ApplicationChoice";
import UploadApplication from "@/components/application/UploadApplication";
import EventBus from "@/eventBus";
export default {
  name: "Application",
  mounted() {
  },
  computed: {
    application: {
      get() {
        return this.$store.state.application;
      }
    },
    user: {
      get() {
        return this.$store.state.user;
      }
    },
  },
  props: {},
  data() {
    return {
    };
  },
  methods: {
  },
  components: {
    ApplicationChoice,
    UploadApplication,
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
