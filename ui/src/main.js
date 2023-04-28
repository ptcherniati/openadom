import Vue from "vue";
import App from "@/App.vue";
import router from "./router";

import Buefy from "buefy";
import { library } from "@fortawesome/fontawesome-svg-core";
import {
  faLink,
  faAngleLeft,
  faAngleRight,
  faArchive,
  faArrowDown,
  faArrowLeft,
  faArrowUp,
  faCalendar,
  faCalendarDay,
  faCaretDown,
  faCaretRight,
  faCaretUp,
  faCheck,
  faCheckCircle,
  faCheckSquare,
  faChevronDown,
  faChevronUp,
  faCircle,
  faDownload,
  faDraftingCompass,
  faEdit,
  faEllipsisH,
  faExclamationCircle,
  faExternalLinkSquareAlt,
  faEye,
  faEyeSlash,
  faFile,
  faFilter,
  faGlobe,
  faInfo,
  faKey,
  faMinusSquare,
  faPaperPlane,
  faPenSquare,
  faPlus,
  faMinus,
  faPoll,
  faQuestion,
  faQuestionCircle,
  faRedo,
  faClock,
  faSearch,
  faSearchPlus,
  faSignInAlt,
  faSignOutAlt,
  faSortAmountDown,
  faSortDown,
  faSortUp,
  faSquare,
  faStream,
  faTimes,
  faTimesCircle,
  faTrashAlt,
  faUpload,
  faUserAstronaut,
  faUserPlus,
  faUsersCog,
  faVial,
  faWrench,
} from "@fortawesome/free-solid-svg-icons";
import {
  faCalendar as farCalendar,
  faCalendarDays as farCalendarDays,
  faCheckSquare as farCheckSquare,
  faFloppyDisk as farFloppyDisk,
  faMinusSquare as farMinusSquare,
  faSquare as farSquare,
} from "@fortawesome/free-regular-svg-icons";
import { FontAwesomeIcon } from "@fortawesome/vue-fontawesome";
import "@/style/global.scss";

// Translation
import { UserPreferencesService } from "./services/UserPreferencesService";
import VueI18n from "vue-i18n";
import i18n_en from "@/locales/en.json";
import i18n_fr from "@/locales/fr.json";
// Validation
import "vee-validate";
import { confirmed, required } from "vee-validate/dist/rules";
import { extend } from "vee-validate";

library.add(farFloppyDisk, farCalendarDays, farCalendar, farSquare, farMinusSquare, farCheckSquare);
library.add(
  faUsersCog,
  faLink,
  faCircle,
  faEye,
  faEyeSlash,
  faFile,
  faPlus,
  faMinus,
  faExclamationCircle,
  faQuestionCircle,
  faCheck,
  faCheckCircle,
  faPenSquare,
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
  faUsersCog,
  faClock,
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
  faEdit,
  faInfo,
  faQuestion,
  faEllipsisH
);
Vue.component("vue-fontawesome", FontAwesomeIcon);

Vue.use(VueI18n);
const userPreferencesService = UserPreferencesService.INSTANCE;
export const i18n = new VueI18n({
  locale: userPreferencesService.getUserPrefLocale(),
  messages: {
    en: i18n_en,
    fr: i18n_fr,
  },
});

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
    return value && value.length >= 3 && value.length <= 20;
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
