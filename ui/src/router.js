import Vue from "vue";
import Router from "vue-router";
import References from "./components/references/References";
import Reference from "./components/references/Reference";
import ShowReference from "./components/references/ShowReference";
import UploadReference from "./components/references/UploadReference";
import ApplicationChoice from "./components/application/ApplicationChoice";
import Application from "./components/application/Application";
import Datasets from "./components/Datasets";
import Synthesis from "./components/Synthesis";

Vue.use(Router);

export default new Router({
    mode: "history",
    base: process.env.BASE_URL,
    routes: [
        {
            path: "/applications",
            name: "home",
            component: ApplicationChoice
        },
        {
            path: "/application/:applicationName",
            component: Application,
            children: [
                {
                    path: "datasets",
                    name: "applicationDatasets",
                    component: Datasets,
                },
                {
                    path: "references",
                    name: "applicationReferences",
                    component: References,
                },
                {
                    path: "reference/:referenceName",
                    name: "reference",
                    component: Reference,
                    children: [
                        {
                            path: "show",
                            name: "showReference",
                            component: ShowReference,
                        },
                        {
                            path: "upload",
                            name: "uploadReference",
                            component: UploadReference,
                        }
                    ]
                }
            ]
        },
        {
            path: "/synthesis",
            name: "synthesis",
            component: Synthesis,
        }
    ]
});