<template>
  <div>
    <CollapsibleTree
      v-for="ref in references"
      :key="ref.id"
      :label="ref.label"
      :children="ref.children"
      :level="0"
      :withDownload="true"
      :onClickLabelCb="(event, label) => openRefDetails(event, label)"
    />
    <ReferencesDetailsPanel
      :leftAlign="false"
      :open="openPanel"
      :reference="chosenRef"
      @openStateChanged="(newVal) => (openPanel = newVal)"
    />
  </div>
</template>

<script>
import { Component, Prop, Vue } from "vue-property-decorator";
import { convertReferencesToTrees } from "@/utils/ConversionUtils";
import CollapsibleTree from "@/components/common/CollapsibleTree.vue";
import ReferencesDetailsPanel from "./ReferencesDetailsPanel.vue";

@Component({
  components: { CollapsibleTree, ReferencesDetailsPanel },
})
export default class ReferencesManagement extends Vue {
  @Prop() application;

  references = [];
  openPanel = false;
  chosenRef = null;

  created() {
    if (!this.application || !this.application.id) {
      return;
    }
    this.references = convertReferencesToTrees(Object.values(this.application.references));
  }

  openRefDetails(event, label) {
    event.stopPropagation();
    this.openPanel = this.chosenRef && this.chosenRef.label === label ? !this.openPanel : true;
    this.chosenRef = Object.values(this.application.references).find((ref) => ref.label === label);
  }
}
</script>
