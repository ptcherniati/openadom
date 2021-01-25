import Vue from "vue";
import Vuetify from 'vuetify'
import App from "./App.vue";
import router from "./router";
import store from "./store";
import dateFormat from "@/filter/dateFormat.filter.js";
import directive from "@/directive";
import 'vuetify/dist/vuetify.min.css'
import MultiFiltersPlugin from './plugins/MultiFilters.js'

Vue.config.productionTip = false;
Vue.use(Vuetify)
Vue.use(Vuetify, {
    iconfont: 'mdi' // 'md' || 'mdi' || 'fa' || 'fa4'
})
Vue.use(MultiFiltersPlugin);
directive.forEach(directive => Vue.use(directive));
Vue.config.devtools = true
new Vue({
    router,
    store,
    filters: {
        dateFormat
    },
    render: h => h(App)
}).$mount("#app");