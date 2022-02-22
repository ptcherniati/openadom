<template>
  <div class="modal-overlay">
    <h1 class="title main-title">{{ title }}</h1>
    <slot></slot>
  </div>
</template>

<script>
import { Component, Prop, Vue, Watch } from "vue-property-decorator";

@Component({
  components: {},
})
export default class ModalCard extends Vue {
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
.ModalCard {
  background-color: white;
  z-index: 1;
  position: absolute;
  height: auto;
  top: 0;
  width: 100%;
  padding: $container-padding-vert 2.5rem;
  transition: transform 250ms;
}

.ModalCard-close-button {
  position: absolute;
  top: 0;
  right: 0;
}
.animation-content.modal-content {
  max-width: 1400px;
}
</style>
