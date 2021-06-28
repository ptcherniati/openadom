<template>
  <PageView class="with-submenu">
    <SubMenu :root="application.title" :paths="subMenuPaths" />
    <h1 class="title main-title">
      {{ $t("titles.references-page", { applicationName: application.title }) }}
    </h1>
    <div>
      <CollapsibleTree
        v-for="ref in references"
        :key="ref.id"
        :label="ref.label"
        :children="ref.children"
        :level="0"
        :onClickLabelCb="(event, label) => openRefDetails(event, label)"
        :onUploadCb="(label, refFile) => uploadReferenceCsv(label, refFile)"
        :buttons="buttons"
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
import { ReferenceService } from "@/services/rest/ReferenceService";

import PageView from "../common/PageView.vue";
import { ApplicationResult } from "@/model/ApplicationResult";
import SubMenu, { SubMenuPath } from "@/components/common/SubMenu.vue";
import { AlertService } from "@/services/AlertService";
import { Button } from "@/model/Button";

@Component({
  components: { CollapsibleTree, ReferencesDetailsPanel, PageView, SubMenu },
})
export default class ReferencesManagementView extends Vue {
  @Prop() applicationName;

  applicationService = ApplicationService.INSTANCE;
  referenceService = ReferenceService.INSTANCE;
  alertService = AlertService.INSTANCE;

  references = [];
  openPanel = false;
  chosenRef = null;
  application = new ApplicationResult();
  subMenuPaths = [];
  buttons = [
    new Button(
      this.$t("referencesManagement.consult"),
      "eye",
      (label) => this.consultReference(label),
      "is-primary"
    ),
    new Button(this.$t("referencesManagement.download"), "download", (label) =>
      this.downloadReference(label)
    ),
  ];

  created() {
    this.subMenuPaths = [
      new SubMenuPath(
        this.$t("referencesManagement.references").toLowerCase(),
        () => this.$router.push(`/applications/${this.applicationName}/references`),
        () => this.$router.push(`/applications`)
      ),
    ];
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
    this.chosenRef = this.findReferenceByLabel(label);
  }

  consultReference(label) {
    const ref = this.findReferenceByLabel(label);
    if (ref) {
      this.$router.push(`/applications/${this.applicationName}/references/${ref.id}`);
    }
  }

  downloadReference(label) {
    const reference = this.findReferenceByLabel(label);
    if (reference) {
      this.referenceService.getReferenceCsv(this.applicationName, reference.id);
    }
  }

  async uploadReferenceCsv(label, refFile) {
    const reference = this.findReferenceByLabel(label);
    try {
      await this.referenceService.createReference(this.applicationName, reference.id, refFile);
      this.alertService.toastSuccess(this.$t("alert.reference-updated"));
    } catch (error) {
      this.alertService.toastError(this.$t("alert.reference-csv-upload-error"), error);
    }
  }

  findReferenceByLabel(label) {
    return Object.values(this.application.references).find((ref) => ref.label === label);
  }
}
</script>
