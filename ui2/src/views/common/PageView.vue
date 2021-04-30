<template>
  <div class="container PageView-container">
    <MenuView v-if="!noMenu" />
    <slot></slot>
  </div>
</template>

<script>
import { LoginService, LOGGED_OUT } from "@/services/LoginService";
import { Component, Prop, Vue } from "vue-property-decorator";
import MenuView from "./MenuView.vue";

@Component({
  components: { MenuView },
})
export default class PageView extends Vue {
  @Prop({ default: false }) noMenu;

  loginService = LoginService.INSTANCE;

  created() {
    const loggedUser = this.loginService.getLoggedUser();
    if (!loggedUser || !loggedUser.id) {
      this.$router.push("/login").catch(() => {});
    }

    this.loginService.on(LOGGED_OUT, () => {
      this.$router.push("/login").catch(() => {});
    });
  }
}
</script>

<style lang="scss" scoped>
.PageView-container {
  width: 100%;
}
</style>
