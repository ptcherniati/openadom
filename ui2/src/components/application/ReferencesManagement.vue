<template>
  <div>
    <div>
      <b-button @click="setOpenSite" :icon-left="openSite ? 'caret-up' : 'caret-down'" /> Site
      <div v-if="openSite">Parcelle</div>
    </div>
    <div>Unités</div>
  </div>
</template>

<script>
import { Component, Prop, Vue } from "vue-property-decorator";
@Component({
  components: {},
})
export default class ReferencesManagement extends Vue {
  @Prop() application;

  openSite = false;

  setOpenSite() {
    this.openSite = !this.openSite;
  }
}
</script>
