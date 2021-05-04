import Vue from "vue";
import VueRouter from "vue-router";
import LoginView from "@/views/LoginView.vue";
import ApplicationsView from "@/views/ApplicationsView.vue";
import ReferencesView from "@/views/ReferencesView.vue";

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
    path: "/references",
    name: "References",
    component: ReferencesView,
  },
];

const router = new VueRouter({
  mode: "history",
  base: process.env.BASE_URL,
  routes,
});

export default router;