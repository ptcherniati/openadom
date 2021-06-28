<template>
  <div class="menu-view-container">
    <b-navbar class="menu-view" v-if="open">
      <template #start>
        <b-navbar-item href="https://www.inrae.fr/">
          <img class="logo_blanc" src="@/assets/logo-inrae_blanc.svg" />
          <img class="logo_vert" src="@/assets/Logo-INRAE.svg" />
        </b-navbar-item>
        <img class="logo_rep" src="@/assets/Rep-FR-logo.svg" />
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
    <FontAwesomeIcon
      @click="open = !open"
      :icon="open ? 'caret-up' : 'caret-down'"
      class="clickable mr-3 menu-view-collapsible-icon"
    />
  </div>
</template>

<script>
import { Component, Vue } from "vue-property-decorator";

import { LoginService } from "@/services/rest/LoginService";
import { UserPreferencesService } from "@/services/UserPreferencesService";

import { Locales } from "@/utils/LocaleUtils.js";
import { FontAwesomeIcon } from "@fortawesome/vue-fontawesome";

@Component({
  components: { FontAwesomeIcon },
})
export default class MenuView extends Vue {
  loginService = LoginService.INSTANCE;
  userPreferencesService = UserPreferencesService.INSTANCE;

  locales = Locales;
  chosenLocale = "";
  open = false;

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
    margin: 0.7rem;
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

    .logo_vert {
      display: none;
    }

    &.router-link-exact-active {
      color: white;
      font-size: 20px;
    }

    &:hover {
      color: $primary;
      .logo_vert {
        display: block;
      }
      .logo_blanc {
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

.menu-view-container {
  line-height: 0;
}

.menu-view-collapsible-icon {
  width: 100%;
  background-color: $primary-slightly-transparent;
  height: 30px;
  opacity: 0.8;

  &:hover {
    opacity: 1;
  }

  path {
    fill: white;
  }
}
</style>
