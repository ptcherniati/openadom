<template>
  <div>
    <PageView>
      <h1 class="title main-title">{{ $t("titles.references-page") }}</h1>
      {{ loggedUser.login }}
    </PageView>
  </div>
</template>

<script>
import { User } from "@/model/User";
import { LoginService } from "@/services/rest/LoginService";
import { Component, Vue } from "vue-property-decorator";
import PageView from "./common/PageView.vue";

@Component({
  components: { PageView },
})
export default class ReferencesView extends Vue {
  loginService = LoginService.INSTANCE;

  loggedUser = new User();

  created() {
    this.init();
  }

  async init() {
    this.loggedUser = await this.loginService.getLoggedUser();
  }
}
</script>
