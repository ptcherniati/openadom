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
  faPlus,
} from "@fortawesome/free-solid-svg-icons";
import { FontAwesomeIcon } from "@fortawesome/vue-fontawesome";
library.add(faEye, faEyeSlash, faPlus, faExclamationCircle, faCheck);
Vue.component("vue-fontawesome", FontAwesomeIcon);

import "@/style/global.scss";

// Validation
import "vee-validate";
import "@/services/validation/vee-validation-rules";

// Translation
import i18n from "@/i18n";

Vue.use(Buefy, {
  defaultIconComponent: "vue-fontawesome",
  defaultIconPack: "fas",
});

Vue.config.productionTip = false;

new Vue({
  router,
  i18n,
  render: (h) => h(App),
}).$mount("#app");
