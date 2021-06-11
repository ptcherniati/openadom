<template>
  <div>
    <CollapsibleTree
      v-for="ref in references"
      :key="ref.id"
      :label="ref.label"
      :children="ref.children"
      :level="0"
    />
  </div>
</template>

<script>
import { Component, Prop, Vue } from "vue-property-decorator";
import { convertReferencesToTrees } from "@/utils/ConversionUtils";
import CollapsibleTree from "@/components/common/CollapsibleTree.vue";

@Component({
  components: { CollapsibleTree },
})
export default class ReferencesManagement extends Vue {
  @Prop() application;

  references = [];

  created() {
    if (!this.application || !this.application.id) {
      return;
    }
    this.references = convertReferencesToTrees(Object.values(this.application.references));
  }
}
</script>
