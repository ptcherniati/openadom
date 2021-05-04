<template>
  <div>
    <MenuView v-if="hasMenu" />
    <div class="container PageView-container">
      <slot></slot>
    </div>
  </div>
</template>

<script>
import { LoginService } from "@/services/LoginService";
import { Component, Prop, Vue } from "vue-property-decorator";
import MenuView from "./MenuView.vue";

@Component({
  components: { MenuView },
})
export default class PageView extends Vue {
  @Prop({ default: true }) hasMenu;

  loginService = LoginService.INSTANCE;

  created() {
    const loggedUser = this.loginService.getLoggedUser();
    if (!loggedUser || !loggedUser.id) {
      this.$router.push("/login").catch(() => {});
    }
  }
}
</script>

<style lang="scss" scoped>
.PageView-container {
  width: 100%;
}
</style>
