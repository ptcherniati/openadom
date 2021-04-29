<template>
  <b-navbar class="menu-view">
    <template #start>
      <b-navbar-item tag="router-link" :to="{ path: '/applications' }">
        {{ $t("menu.applications") }}
      </b-navbar-item>
      <b-navbar-item tag="router-link" :to="{ path: '/references' }">
        {{ $t("menu.references") }}
      </b-navbar-item>
    </template>

    <template #end>
      <b-navbar-item tag="div">
        <div class="buttons">
          <b-button type="is-info" @click="logout" icon-right="sign-out-alt">{{
            $t("menu.logout")
          }}</b-button>
        </div>
      </b-navbar-item>
    </template>
  </b-navbar>
</template>

<script>
import { User } from "@/model/User";
import { LoginService } from "@/services/LoginService";
import { Component, Vue } from "vue-property-decorator";

@Component({
  components: {},
})
export default class MenuView extends Vue {
  loginService = LoginService.INSTANCE;

  loggedUser = new User();

  created() {
    this.init();
  }

  async init() {
    this.loggedUser = await this.loginService.getLoggedUser();
  }

  logout() {
    this.loginService.logout();
  }
}
</script>

<style lang="scss" scoped>
.menu-view {
  background-color: $primary;
  height: $menu-height;

  .navbar-item {
    flex: 1 1 auto;
    font-weight: bold;
    font-size: 18px;
    color: $light-text;
    height: 100%;
    padding-left: 10px;
    padding-right: 10px;
    justify-content: center;

    &.router-link-exact-active {
      color: white;
      font-size: 20px;
    }

    &:hover {
      color: $primary;
    }
  }

  .navbar-menu {
    flex: 1 1 auto;
    justify-content: flex-end;

    div.navbar-start {
      justify-content: space-around;
      margin: 0;
    }
  }
}
</style>
