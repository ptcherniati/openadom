import Vue from "vue";
import Router from "vue-router";
import References from "./components/references/References";
import Application from "./components/application/Application";
import Datasets from "./components/Datasets";
import Synthesis from "./components/Synthesis";

Vue.use(Router);

export default new Router({
    mode: "history",
    base: process.env.BASE_URL,
    routes: [{
            path: "/applications",
            name: "home",
            component: Application
        },
        {
            path: "/references",
            name: "references",
            component: References,
        },
        {
            path: "/synthesis",
            name: "synthesis",
            component: Synthesis,
        },
        {
            path: "/datasets",
            name: "datasets",
            component: Datasets,
        },
    ]
});