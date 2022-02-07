import Vue from "vue";
import App from "@/App.vue";
import router from "./router";

import Buefy from "buefy";
import { library } from "@fortawesome/fontawesome-svg-core";
import {
  faCircle,
  faCheck,
  faCheckCircle,
  faSquare,
  faMinusSquare,
  faCheckSquare,
  faAngleLeft,
  faAngleRight,
  faFilter,
  faSearch,
  faSearchPlus,
  faArrowDown,
  faArrowUp,
  faCaretDown,
  faCaretUp,
  faDownload,
  faDraftingCompass,
  faExclamationCircle,
  faEye,
  faEyeSlash,
  faGlobe,
  faPlus,
  faPoll,
  faSignOutAlt,
  faTimes,
  faTrashAlt,
  faUpload,
  faWrench,
  faVial,
  faCaretRight,
  faArrowLeft,
  faSignInAlt,
  faUserPlus,
  faUserAstronaut,
  faKey,
  faChevronUp,
  faChevronDown,
  faCalendarDay,
  faPaperPlane,
  faExternalLinkSquareAlt,
  faCalendar,
  faRedo,
  faStream,
  faSortAmountDown,
  faSortUp,
  faSortDown,
  faArchive,
  faTimesCircle,
  faEdit,
} from "@fortawesome/free-solid-svg-icons";
import { FontAwesomeIcon } from "@fortawesome/vue-fontawesome";
library.add(
  faCircle,
  faEye,
  faEyeSlash,
  faPlus,
  faExclamationCircle,
  faCheck,
  faCheckCircle,
  faSquare,
  faMinusSquare,
  faCheckSquare,
  faSignOutAlt,
  faGlobe,
  faUpload,
  faFilter,
  faSearch,
  faSearchPlus,
  faArrowUp,
  faArrowDown,
  faAngleLeft,
  faAngleRight,
  faWrench,
  faPoll,
  faDraftingCompass,
  faCaretUp,
  faCaretDown,
  faTimes,
  faTrashAlt,
  faDownload,
  faVial,
  faCaretRight,
  faArrowLeft,
  faSignInAlt,
  faUserPlus,
  faUserAstronaut,
  faKey,
  faChevronUp,
  faChevronDown,
  faCalendarDay,
  faPaperPlane,
  faArrowLeft,
  faExternalLinkSquareAlt,
  faCalendar,
  faRedo,
  faStream,
  faSortAmountDown,
  faSortDown,
  faSortUp,
  faArchive,
  faTimesCircle,
  faEdit
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
import { confirmed, required } from "vee-validate/dist/rules";
import { extend } from "vee-validate";
// Ici on surcharge les messages d'erreur de vee-validate.
// Pour plus de règles :  https://logaretm.github.io/vee-validate/guide/rules.html

extend("required", {
  ...required,
  message: i18n.t("validation.invalid-required"),
});

extend("confirmed", {
  ...confirmed,
  message: i18n.t("validation.invalid-confirmed").toString(),
});

extend("validApplicationName", {
  message: i18n.t("validation.invalid-application-name"),
  validate: (value) => {
    return value && value.match("^[a-z]*$") != null;
  },
});

extend("validApplicationNameLength", {
  message: i18n.t("validation.invalid-application-name-length"),
  validate: (value) => {
    return value && value.length >= 4 && value.length <= 20;
  },
});

// extend("dateIsAfter", {
//   message: i18n.t("validation.date-not-after").toString(),
//   validate: (value, { min }: Record<string, any>) => {
//     return isAfter(value, new Date(min))
//   },
//   params: ["min"],
// })

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
