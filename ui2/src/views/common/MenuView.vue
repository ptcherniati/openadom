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
      <b-navbar-item href="https://www.inrae.fr/">
        <img class="logo" src="../../assets/logo-inrae_blanc.svg" />
        <img class="logo" src="../../assets/Logo-INRAE.svg" />
      </b-navbar-item>
      <img class="logo_rep" src="../../assets/Rep-FR-logo.svg" />
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

  .logo_rep {
    margin: 2.4%;
    max-height: 4.5rem;
  }

  .navbar-item {
    flex: 1 1 auto;
    font-weight: bold;
    font-size: 18px;
    color: $light-text;
    height: 100%;
    padding-left: 10px;
    padding-right: 10px;
    justify-content: center;

    .logo:last-child {
      display: none;
    }

    &.router-link-exact-active {
      color: white;
      font-size: 20px;
    }

    &:hover {
      color: $primary;
      .logo:last-child {
        display: block;
      }
      .logo:first-child {
        display: none;
      }
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
