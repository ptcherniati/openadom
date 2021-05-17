import Vue from "vue";
import App from "@/App.vue";
import router from "./router";

import Buefy from "buefy";
import { library } from "@fortawesome/fontawesome-svg-core";
import {
  faCheck,
  faExclamationCircle,
  faEye,
  faEyeSlash,
  faGlobe,
  faPlus,
  faSignOutAlt,
  faUpload,
} from "@fortawesome/free-solid-svg-icons";
import { FontAwesomeIcon } from "@fortawesome/vue-fontawesome";
library.add(
  faEye,
  faEyeSlash,
  faPlus,
  faExclamationCircle,
  faCheck,
  faSignOutAlt,
  faGlobe,
  faUpload
);
Vue.component("vue-fontawesome", FontAwesomeIcon);

import "@/style/global.scss";

// Translation
import { UserPreferencesService } from "./services/UserPreferencesService";
import VueI18n from "vue-i18n";
import i18n_en from "@/locales/en.json";
import i18n_fr from "@/locales/fr.json";

Vue.use(VueI18n);
const userPreferencesService = UserPreferencesService.INSTANCE;
export const i18n = new VueI18n({
  locale: userPreferencesService.getUserPrefLocale(),
  messages: {
    en: i18n_en,
    fr: i18n_fr,
  },
});

// Validation
import "vee-validate";
import { required } from "vee-validate/dist/rules";
import { extend } from "vee-validate";
// Ici on surcharge les messages d'erreur de vee-validate.
// Pour plus de règles :  https://logaretm.github.io/vee-validate/guide/rules.html

extend("required", {
  ...required,
  message: i18n.t("validation.invalid-required"),
});

// Buefy
Vue.use(Buefy, {
  defaultIconComponent: "vue-fontawesome",
  defaultIconPack: "fas",
});

Vue.config.productionTip = false;

const app = new Vue({
  router,
  i18n,
  render: (h) => h(App),
}).$mount("#app");

export default app;
