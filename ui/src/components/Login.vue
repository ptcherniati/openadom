<template>
  <v-form class="login">
    <v-container>
      <v-text-field
        label="Login"
        v-model="user.login"
        required
        name="Login"
      />
      <v-text-field
        label="Password"
        v-model="user.password"
        required
        name="Password"
      />
      <v-btn
        @click="login"
        value="Log on"
      >
        Se connecter
      </v-btn>
    </v-container>
  </v-form>
</template>

<script>
export default {
  name: "Login",
  created: function() {
    this.user = this.$store.state.user;
  },
  computed: {},
  data() {
    return {};
  },
  methods: {
    login() {
      this.$store.dispatch("loadUser", this.user);
    }
  }
};
</script>

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
