<template>
  <v-app>
    <!--v-navigation-drawer app>nav</v-navigation-drawer-->
    <v-toolbar app>
      <v-toolbar-title class="headline text-uppercase">
        <router-link
          to="/applications"
          v-if="!user.init"
        >
          <v-btn
            color="blue"
            flat
            round
          >
            Application
          </v-btn>
        </router-link>
        <router-link
          to="/references"
          v-if="configuration!=null"
        >
          <v-btn
            color="blue"
            flat
            round
          >
            References
          </v-btn>
        </router-link>
        <router-link
          to="/datasets"
          v-if="configuration!=null"
        >
          <v-btn
            color="blue"
            flat
            round
          >
            Datasets
          </v-btn>
        </router-link>
        <router-link
          to="/synthesis"
          v-if="configuration!=null"
        >
          <v-btn
            color="blue"
            flat
            round
          >
            Synthesis
          </v-btn>
        </router-link>
      </v-toolbar-title>
      <v-spacer />
      <v-tooltip
        bottom
        v-if="user!=null && !user.init"
      >
        <template v-slot:activator="{ on }">
          <v-btn
            @click="logout"
            color="primary"
            flat
            v-on="on"
          >
            {{ user.login }}
            <v-icon
              left
              x-large
              color="error"
            >
              mdi-logout
            </v-icon>
          </v-btn>
        </template>
        <span>Se déconnecter</span>
      </v-tooltip>
    </v-toolbar>

    <v-content transition="slide-x-transition">
      <Login v-if="user.init" />
      <router-view />
    </v-content>
    <v-footer
      height="auto"
      color="primary lighten-1"
    >
      <v-layout
        justify-center
        row
        wrap
      >
        <v-flex
          primary
          lighten-2
          py-3
          text-xs-center
          white--text
          xs12
        >
          &copy;2018 —
          <strong>Vuetify</strong>
        </v-flex>
      </v-layout>
    </v-footer>
  </v-app>
</template>

<script>
import router from "./router";
import Login from "@/components/Login";
import '@mdi/font/css/materialdesignicons.css'// Ensure you are using css-

export default {
  created() {},
  name: "App",
  components: {
    Login
  },
  computed: {
    menus: {
      get() {
        return router.options.routes;
      }
    },
    user: {
      get() {
        return this.$store.state.user;
      }
    },
    configuration: {
      get() {
        return this.$store.state.configuration;
      }
    }
  },
  methods :{
    logout(){
      this.$store.dispatch('logOut')
    }
  }
};
</script>
