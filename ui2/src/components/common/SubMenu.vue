<template>
  <div class="SubMenu">
    <span class="SubMenu-root">{{ root }}</span>
    <div v-for="(path, index) in paths" :key="path.label">
      <span class="SubMenu-path-separator mr-1 ml-1">/</span>
      <span
        @click="index !== paths.length - 1 ? path.clickCb() : ''"
        :class="index !== paths.length - 1 ? 'link' : ''"
        >{{ path.label }}</span
      >
    </div>
  </div>
</template>

<script>
import { Component, Prop, Vue } from "vue-property-decorator";

export class SubMenuPath {
  label;
  clickCb;

  constructor(label, clickCb) {
    this.label = label;
    this.clickCb = clickCb;
  }
}

@Component({
  components: {},
})
export default class SubMenu extends Vue {
  @Prop() root;
  @Prop() paths;
}
</script>

<style lang="scss" scoped>
.SubMenu {
  display: flex;
  height: 40px;
  background-color: $info-transparent;
  align-items: center;
  padding: 0.5rem $container-padding-hor;
  width: calc(100% + 2 * #{$container-padding-hor});
  transform: translateX(-$container-padding-hor);
}

.SubMenu-root {
  color: $dark;
  font-weight: 600;
  font-size: 1.2em;
}
</style>
