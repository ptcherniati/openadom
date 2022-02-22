<template>
  <div class="PageView" role="main">
    <MenuView v-if="hasMenu" />
    <div :class="`PageView-container ${hasMenu ? '' : 'noMenu'}`">
      <slot></slot>
    </div>
  </div>
</template>

<script>
import { LoginService } from "@/services/rest/LoginService";
import { Component, Prop, Vue } from "vue-property-decorator";
import MenuView from "./MenuView.vue";

@Component({
  components: { MenuView },
})
export default class PageView extends Vue {
  @Prop({ default: true }) hasMenu;

  loginService = LoginService.INSTANCE;

  created() {
    const authenticatedUser = this.loginService.getAuthenticatedUser();
    if (!authenticatedUser?.id) {
      this.$router.push("/login").catch(() => {});
    }
  }
}
</script>

<style lang="scss" scoped>
.PageView {
  height: 100%;
  &.with-submenu {
    .PageView-container {
      padding-top: 0rem;
    }
  }
}

.PageView-container {
  width: 100%;
  height: calc(100% - #{$menu-height});
  padding: $container-padding-vert $container-padding-hor;
  position: relative;

  &.noMenu {
    height: 100%;
  }
}
</style>
