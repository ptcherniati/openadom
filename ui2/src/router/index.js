import Vue from "vue";
import VueRouter from "vue-router";
import LoginView from "@/views/LoginView.vue";
import ApplicationsView from "@/views/application/ApplicationsView.vue";
import ApplicationCreationView from "@/views/application/ApplicationCreationView.vue";
import ReferencesManagementView from "@/views/references/ReferencesManagementView.vue";
import ReferenceTable from "@/views/references/ReferenceTableView.vue";
import DataTypeTableView from "@/views/datatype/DataTypeTableView.vue";
import DataTypesManagementView from "@/views/datatype/DataTypesManagementView.vue";
import DataTypeAuthorizationsView from "@/views/authorizations/DataTypeAuthorizationsView.vue";

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
    path: "/applications/:applicationName/dataTypes",
    component: DataTypesManagementView,
    props: true,
  },
  {
    path: "/applications/:applicationName/dataTypes/:dataTypeId",
    component: DataTypeTableView,
    props: true,
  },
  {
    path: "/applications/acbb/dataTypes/:dataTypeId/authorizations",
    component: DataTypeAuthorizationsView,
    props: true,
  },
];

const router = new VueRouter({
  mode: "history",
  base: process.env.BASE_URL,
  routes,
});

export default router;
