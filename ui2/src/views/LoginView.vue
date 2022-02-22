<template>
  <PageView class="LoginView" :hasMenu="false">
    <h1 class="title main-title">{{ $t("titles.login-page") }}</h1>
    <div class="card LoginView-card">
      <b-tabs v-model="selectedTab" type="is-boxed" :animated="false">
        <b-tab-item :label="$t('login.signin')" icon="sign-in-alt" tabindex="0">
          <SignIn />
        </b-tab-item>
        <b-tab-item
          :label="$t('login.register')"
          icon="user-plus"
          @keypress.enter="changeTabToSignIn"
          tabindex="0"
        >
          <Register @userRegistered="changeTabToSignIn" />
        </b-tab-item>
      </b-tabs>
    </div>
  </PageView>
</template>

<script>
import Register from "@/components/login/Register.vue";
import SignIn from "@/components/login/Signin.vue";
import { Component, Vue } from "vue-property-decorator";
import PageView from "./common/PageView.vue";

@Component({
  components: { PageView, SignIn, Register },
})
export default class LoginView extends Vue {
  selectedTab = 0;

  changeTabToSignIn() {
    this.selectedTab = 0;
  }
}
</script>

<style lang="scss">
.LoginView {
  display: flex;
  flex-direction: column;
  align-items: center;

  .PageView-container {
    display: flex;
    flex-direction: column;
    align-items: center;
  }

  .LoginView-card {
    width: 70%;
  }
}
</style>
