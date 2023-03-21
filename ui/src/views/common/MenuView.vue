<template>
  <div class="menu-view-container" role="navigation">
    <b-navbar class="menu-view" v-if="open" role="menubar" :aria-label="$t('menu.aria-nav-bar')">
      <template #start>
        <b-navbar-item href="https://www.inrae.fr/">
          <img
            class="logo_blanc"
            src="@/assets/logo-inrae_blanc.svg"
            alt="Accès page de l’institut national de recherche pour l’agriculture, l’alimentation et l’environnement"
          />
          <img
            class="logo_vert"
            src="@/assets/Logo-INRAE.svg"
            alt="Accès page de l’institut national de recherche pour l’agriculture, l’alimentation et l’environnement"
          />
        </b-navbar-item>
        <b-navbar-item tag="router-link" :to="{ path: '/applications' }">
          {{ $t("menu.applications") }}
        </b-navbar-item>
      </template>

      <template #end>
        <img
          class="logo_anaee"
          src="@/assets/logo-AnaEE-france.png"
          alt="Logo de l'Infrastructure de recherche nationale AnaEE France (Analyses et Expérimentations pour les Ecosystèmes)"
        />
        <img
          class="logo_rep"
          src="@/assets/Rep-FR-logo.svg"
          alt="Logo de la République Francçaise"
        />
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

        <b-navbar-item tag="div" class="MenuView-user">
          <b-dropdown position="is-bottom-left" append-to-body aria-role="menu">
            <template #trigger>
              <a class="navbar-item" role="button">
                <b-icon icon="user-astronaut" class="mr-1" />
                <span>{{ currentUser.login }}</span>
                <b-icon icon="caret-down" class="ml-2" />
              </a>
            </template>

            <b-dropdown-item
              @click="logout()"
              @keypress.enter="logout()"
              tabindex="0"
              aria-role="menuitem"
            >
              <b-icon icon="sign-out-alt" />
              {{ $t("menu.logout") }}
            </b-dropdown-item>
            <b-dropdown-item
              v-if="currentUser.superadmin"
              @click="showApplicationRightManagement()"
              @keypress.enter="showApplicationRightManagement()"
              tabindex="0"
              aria-role="menuitem"
            >
              <b-icon icon="wrench" />
              {{ $t("menu.authorizations") }}
            </b-dropdown-item>
            <b-dropdown-item
              v-if="!currentUser.superadmin && currentUser.authorizedForApplicationCreation"
              @click="showApplicationRightManagementForApplicationCreator()"
              @keypress.enter="showApplicationRightManagementForApplicationCreator()"
              tabindex="0"
              aria-role="menuitem"
            >
              <b-icon icon="wrench" />
              {{ $t("menu.authorizations") }}
            </b-dropdown-item>
          </b-dropdown>
        </b-navbar-item>
      </template>
    </b-navbar>

    <FontAwesomeIcon
      @click="open = !open"
      :icon="open ? 'chevron-up' : 'chevron-down'"
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
  currentUser = null;

  created() {
    this.chosenLocale = this.userPreferencesService.getUserPrefLocale();
    this.currentUser = this.loginService.getAuthenticatedUser();
  }

  logout() {
    this.loginService.logout();
  }

  showApplicationRightManagement() {
    this.$router.push("/authorizationsManagement");
  }

  showApplicationRightManagementForApplicationCreator() {
    this.$router.push("/authorizationsManagementForApplicationCreator");
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

  .logo_anaee {
    margin: 0.7rem;
    max-height: 4.5rem;
  }
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

  .MenuView-user.navbar-item {
    .navbar-item {
      color: white;
      &:hover {
        color: $primary;
      }
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