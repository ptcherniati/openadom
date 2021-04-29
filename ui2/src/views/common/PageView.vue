<template>
  <div class="container PageView-container">
    <slot></slot>
  </div>
</template>

<script>
import { Component, Vue } from "vue-property-decorator";

@Component
export default class PageView extends Vue {}
</script>

<style lang="scss" scoped>
.PageView-container {
  width: 100%;
}
</style>
