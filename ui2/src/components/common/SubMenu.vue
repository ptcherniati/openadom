<template>
  <div class="SubMenu">
    <span class="SubMenu-root">{{ root }}</span>
    <div v-for="path in paths" :key="path.label">
      <span class="SubMenu-path-separator mr-1 ml-1">/</span>
      <span @click="path.clickCb" class="link"> {{ path.label }}</span>
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

  created() {
    console.log(this.paths);
  }
}
</script>

<style lang="scss" scoped>
.SubMenu {
  display: flex;
  height: 40px;
  background-color: $info-transparent;
}

.SubMenu-root {
  color: $dark;
  font-weight: 600;
  font-size: 1.2em;
}
</style>
