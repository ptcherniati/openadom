import Vue from "vue";
import VueRouter from "vue-router";
import LoginView from "@/views/LoginView.vue";
import HelpView from "@/views/documentation/HelpView.vue";
import ApplicationsView from "@/views/application/ApplicationsView.vue";
import ApplicationCreationView from "@/views/application/ApplicationCreationView.vue";
import ReferencesManagementView from "@/views/references/ReferencesManagementView.vue";
import ReferenceTable from "@/views/references/ReferenceTableView.vue";
import AuthorizationReferencesManagement from "@/views/authorizations/AuthorizationReferencesManagement.vue";
import ReferencesAuthorizationInfoView from "@/views/authorizations/ReferencesAuthorizationInfoView.vue";
import DataTypeTableView from "@/views/datatype/DataTypeTableView.vue";
import DataTypesManagementView from "@/views/datatype/DataTypesManagementView.vue";
import DataTypesRepositoryView from "@/views/datatype/DataTypesRepositoryView.vue";
import DataTypeAuthorizationsView from "@/views/authorizations/DataTypeAuthorizationsView.vue";
import DataTypeAuthorizationInfoView from "@/views/authorizations/DataTypeAuthorizationInfoView.vue";
import AuthorizationManagementView from "@/views/authorizations/AuthorizationManagementView.vue";
import DataTypeAuthorizationsRightsRequestView from "@/views/authorizations/DataTypeAuthorizationsRightsRequestView.vue";
import RequestAuthorizationManagementView from "@/views/authorizations/RequestAuthorizationManagementView.vue";
import AuthorizationManagementForApplicationCreatorView from "@/views/authorizations/AuthorizationManagementForApplicationCreatorView.vue";

Vue.use(VueRouter);

const routes = [
  {
    path: "/",
    redirect: "/login",
  },
  {
    path: "/help",
    name: "help",
    component: HelpView,
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
    path: "/authorizationsManagementForApplicationCreator",
    name: "Authorizations application creation",
    component: AuthorizationManagementForApplicationCreatorView,
  },
  {
    path: "/authorizationsManagement",
    name: "Authorizations mangement view",
    component: AuthorizationManagementView,
  },
  {
    path: "/applications/:applicationName/references",
    name: "References management view",
    component: ReferencesManagementView,
    props: true,
  },
  {
    path: "/applications/:applicationName/references/authorizations",
    name: "Authorization references management view",
    component: AuthorizationReferencesManagement,
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
    path: "/applications/:applicationName/dataTypesRepository/:dataTypeId",
    component: DataTypesRepositoryView,
    props: true,
  },
  {
    path: "/applications/:applicationName/dataTypes/:dataTypeId",
    component: DataTypeTableView,
    props: true,
  },
  {
    path: "/applications/:applicationName/authorizations",
    component: DataTypeAuthorizationsView,
    props: true,
  },
  {
    path: "/applications/:applicationName/authorizations/:authorizationId",
    component: DataTypeAuthorizationInfoView,
    props: true,
  },
  {
    path: "/applications/:applicationName/authorizationsRequest/:authorizationId",
    component: DataTypeAuthorizationsRightsRequestView,
    props: true,
  },
  {
    path: "/applications/:applicationName/authorizationsRequest",
    component: RequestAuthorizationManagementView,
    props: true,
  },
  {
    path: "/applications/:applicationName/references/authorizations/:authorizationId",
    component: ReferencesAuthorizationInfoView,
    props: true,
  },
];

const router = new VueRouter({
  mode: "history",
  base: process.env.BASE_URL,
  routes,
});

export default router;