<template>
  <div :class="`SidePanel ${leftAlign ? 'left-align' : 'right-align'} ${innerOpen ? 'open' : ''}`">
    <h1 class="title main-title">{{ title }}</h1>
    <b-button class="SidePanel-close-button" icon-left="times" @click="innerOpen = false" />
    <slot></slot>
  </div>
</template>

<script>
import { Component, Prop, Vue, Watch } from "vue-property-decorator";

@Component({
  components: {},
})
export default class SidePanel extends Vue {
  @Prop({ default: false }) leftAlign;
  @Prop({ default: false }) open;
  @Prop({ default: "" }) title;
  @Prop() closeCb;

  innerOpen = false;

  created() {
    this.innerOpen = this.open;
  }

  @Watch("open")
  onExternalOpenStateChanged(newVal) {
    this.innerOpen = newVal;
  }

  @Watch("innerOpen")
  onInnerOpenStateChanged(newVal) {
    this.closeCb(newVal);
  }
}
</script>

<style lang="scss" scoped>
.SidePanel {
  background-color: $light;
  z-index: 1;
  position: absolute;
  height: auto;
  top: 0;
  width: 100%;
  padding: $container-padding-vert 2.5rem;
  transition: transform 250ms;

  &.right-align {
    right: 0;
    transform: translateX(100%);
    &.open {
      transform: translateX(0);
    }
  }

  &.left-align {
    left: 0;
    transform: translateX(-100%);
    &.open {
      transform: translateX(0);
    }
  }
}

.SidePanel-close-button {
  position: absolute;
  top: 0;
  right: 0;
}
</style>
