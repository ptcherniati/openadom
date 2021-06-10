<template>
  <div>
    <div>
      <FontAwesomeIcon
        @click="openSite = !openSite"
        :icon="openSite ? 'caret-up' : 'caret-down'"
        class="clickable"
      />
      Site
      <div v-if="openSite">Parcelle</div>
    </div>
    <div>Unités</div>
  </div>
</template>

<script>
import { Component, Prop, Vue } from "vue-property-decorator";
import { FontAwesomeIcon } from "@fortawesome/vue-fontawesome";
import { convertReferencesToTrees } from "@/utils/ConversionUtils";

@Component({
  components: { FontAwesomeIcon },
})
export default class ReferencesManagement extends Vue {
  @Prop() application;

  openSite = false;
  references = [];

  created() {
    if (!this.application || !this.application.id) {
      return;
    }
    this.references = convertReferencesToTrees(Object.values(this.application.references));
  }
}
</script>
