<template>
  <div id="app">
    <router-view />
  </div>
</template>

<script>
import { Component, Vue } from "vue-property-decorator";

@Component
export default class App extends Vue {}
</script>

<style lang="scss">
#app {
  font-family: $font-family;
  -webkit-font-smoothing: antialiased;
  -moz-osx-font-smoothing: grayscale;
  color: $text-default-color;
  display: flex;
  flex-direction: column;
  height: 100%;
}
div.media-content span {
  white-space: pre-line;
  word-break: break-all;
}
</style>
