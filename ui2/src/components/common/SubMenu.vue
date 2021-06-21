<template>
  <div class="SubMenu">
    <FontAwesomeIcon
      icon="arrow-left"
      @click="goBack()"
      class="clickable mr-4 SubMenu-back-button"
    />
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
import { FontAwesomeIcon } from "@fortawesome/vue-fontawesome";

export class SubMenuPath {
  label;
  clickCb;
  goBackCb;

  constructor(label, clickCb, goBackCb) {
    this.label = label;
    this.clickCb = clickCb;
    this.goBackCb = goBackCb;
  }
}

@Component({
  components: { FontAwesomeIcon },
})
export default class SubMenu extends Vue {
  @Prop() root;
  @Prop() paths;

  goBack() {
    this.paths[this.paths.length - 1].goBackCb();
  }
}
</script>

<style lang="scss" scoped>
$subMenu-height: 40px;
$subMenu-padding-vert: 0.5rem;

.SubMenu {
  display: flex;
  height: $subMenu-height;
  background-color: $info-transparent;
  align-items: center;
  padding: $subMenu-padding-vert $container-padding-hor;
  width: calc(100% + 2 * #{$container-padding-hor});
  transform: translateX(-$container-padding-hor);
}

.SubMenu-root {
  color: $dark;
  font-weight: 600;
  font-size: 1.2em;
}

.SubMenu-back-button {
  border-radius: 50%;
  background-color: rgba(255, 255, 255, 0.7);
  padding: 0.25rem;
  height: calc(#{$subMenu-height} - 2 * #{$subMenu-padding-vert});
  width: calc(#{$subMenu-height} - 2 * #{$subMenu-padding-vert});

  &:hover {
    color: $primary;
    background-color: rgba(255, 255, 255, 1);
  }
}
</style>
