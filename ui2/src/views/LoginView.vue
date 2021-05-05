<template>
  <PageView class="LoginView" :hasMenu="false">
    <h1 class="title main-title">{{ $t("titles.login-page") }}</h1>
    <div class="card LoginView-card">
      <b-tabs type="is-boxed">
        <b-tab-item :label="$t('login.signin')">
          <SignIn />
        </b-tab-item>
      </b-tabs>
    </div>
  </PageView>
</template>

<script>
import SignIn from "@/components/login/Signin.vue";
import { Component, Vue } from "vue-property-decorator";
import PageView from "./common/PageView.vue";

@Component({
  components: { PageView, SignIn },
})
export default class LoginView extends Vue {}
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
