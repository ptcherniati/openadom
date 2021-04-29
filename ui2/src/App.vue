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
}
</style>
