<template>
  <b-navbar class="menu-view">
    <template #start>
      <b-navbar-item tag="router-link" :to="{ path: '/applications' }">
        {{ $t("menu.applications") }}
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
      <b-navbar-item tag="div">
        <b-field>
          <b-select
            v-model="chosenLocale"
            :placeholder="$t('menu.language')"
            icon="globe"
            @input="setUserPrefLocale"
          >
            <option :value="locales.FRENCH">{{ $t("menu.french") }}</option>
            <option :value="locales.ENGLISH">{{ $t("menu.english") }}</option>
          </b-select>
        </b-field>
      </b-navbar-item>
    </template>
  </b-navbar>
</template>

<script>
import { Component, Vue } from "vue-property-decorator";

import { LoginService } from "@/services/rest/LoginService";
import { UserPreferencesService } from "@/services/UserPreferencesService";

import { Locales } from "@/utils/LocaleUtils.js";

@Component({
  components: {},
})
export default class MenuView extends Vue {
  loginService = LoginService.INSTANCE;
  userPreferencesService = UserPreferencesService.INSTANCE;

  locales = Locales;
  chosenLocale = "";

  created() {
    this.chosenLocale = this.userPreferencesService.getUserPrefLocale();
  }

  logout() {
    this.loginService.logout();
  }

  setUserPrefLocale() {
    this.userPreferencesService.setUserPrefLocale(this.chosenLocale);
  }
}
</script>

<style lang="scss" scoped>
.menu-view {
  background-color: $primary;
  height: $menu-height;
  width: 100%;

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
