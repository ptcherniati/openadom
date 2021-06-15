<template>
  <PageView>
    <SubMenu :root="application.title" :paths="subMenuPaths" />
    <h1 class="title main-title">{{ application.title }}</h1>
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
        :closeCb="(newVal) => (openPanel = newVal)"
      />
    </div>
  </PageView>
</template>

<script>
import { Component, Prop, Vue } from "vue-property-decorator";
import { convertReferencesToTrees } from "@/utils/ConversionUtils";
import CollapsibleTree from "@/components/common/CollapsibleTree.vue";
import ReferencesDetailsPanel from "@/components/references/ReferencesDetailsPanel.vue";
import { ApplicationService } from "@/services/rest/ApplicationService";
import PageView from "../common/PageView.vue";
import { ApplicationResult } from "@/model/ApplicationResult";
import SubMenu, { SubMenuPath } from "@/components/common/SubMenu.vue";

@Component({
  components: { CollapsibleTree, ReferencesDetailsPanel, PageView, SubMenu },
})
export default class ReferencesManagementView extends Vue {
  @Prop() applicationName;

  applicationService = ApplicationService.INSTANCE;

  references = [];
  openPanel = false;
  chosenRef = null;
  application = new ApplicationResult();
  subMenuPaths = [
    new SubMenuPath("references", () =>
      this.$router.push(`/applications/${this.applicationName}/references`)
    ),
  ];

  created() {
    this.init();
  }

  async init() {
    try {
      this.application = await this.applicationService.getApplication(this.applicationName);
      if (!this.application || !this.application.id) {
        return;
      }
      this.references = convertReferencesToTrees(Object.values(this.application.references));
    } catch (error) {
      this.alertService.toastServerError();
    }
  }

  openRefDetails(event, label) {
    event.stopPropagation();
    this.openPanel = this.chosenRef && this.chosenRef.label === label ? !this.openPanel : true;
    this.chosenRef = Object.values(this.application.references).find((ref) => ref.label === label);
  }
}
</script>
