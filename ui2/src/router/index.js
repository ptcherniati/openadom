import Vue from "vue";
import VueRouter from "vue-router";
import LoginView from "@/views/LoginView.vue";
import ApplicationsView from "@/views/application/ApplicationsView.vue";
import ApplicationCreationView from "@/views/application/ApplicationCreationView.vue";
import ReferencesManagementView from "@/views/references/ReferencesManagementView.vue";
import ReferenceTable from "@/views/references/ReferenceTableView.vue";
import DataSetTableView from "@/views/dataset/DataSetTableView.vue";

Vue.use(VueRouter);

const routes = [
  {
    path: "/",
    redirect: "/login",
  },
  {
    path: "/login",
    name: "Login",
    component: LoginView,
  },
  {
    path: "/applications",
    name: "Applications",
    component: ApplicationsView,
  },
  {
    path: "/applicationCreation",
    name: "Application creation",
    component: ApplicationCreationView,
  },
  {
    path: "/applications/:applicationName/references",
    name: "References management view",
    component: ReferencesManagementView,
    props: true,
  },
  {
    path: "/applications/:applicationName/references/:refId",
    component: ReferenceTable,
    props: true,
  },
  {
    path: "/applications/:applicationName/dataset/:dataSetId",
    component: DataSetTableView,
    props: true,
  },
];

const router = new VueRouter({
  mode: "history",
  base: process.env.BASE_URL,
  routes,
});

export default router;
