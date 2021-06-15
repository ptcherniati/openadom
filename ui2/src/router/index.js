import Vue from "vue";
import VueRouter from "vue-router";
import LoginView from "@/views/LoginView.vue";
import ApplicationsView from "@/views/application/ApplicationsView.vue";
import ApplicationCreationView from "@/views/application/ApplicationCreationView.vue";
import ApplicationDetailsView from "@/views/application/ApplicationDetailsView.vue";

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
    path: "/application/:applicationName/:tabIndex",
    name: "Application view",
    component: ApplicationDetailsView,
    props: true,
  },
];

const router = new VueRouter({
  mode: "history",
  base: process.env.BASE_URL,
  routes,
});

export default router;
