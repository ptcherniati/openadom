<template>
  <PageView class="with-submenu">
    <SubMenu :root="application.title" :paths="subMenuPaths" />
    <h1 class="title main-title">
      {{ $t("titles.data-types-page", { applicationName: application.title }) }}
    </h1>
    <div>
      <CollapsibleTree
        v-for="data in dataTypes"
        :key="data.id"
        :label="data.label"
        :level="0"
        :onClickLabelCb="(event, label) => openDataTypeCb(event, label)"
        :onUploadCb="(label, file) => uploadDataTypeCsv(label, file)"
        :buttons="buttons"
      />
    </div>
  </PageView>
</template>

<script>
import { Component, Prop, Vue } from "vue-property-decorator";
import PageView from "@/views/common/PageView.vue";
import { ApplicationService } from "@/services/rest/ApplicationService";
import SubMenu, { SubMenuPath } from "@/components/common/SubMenu.vue";
import CollapsibleTree from "@/components/common/CollapsibleTree.vue";
import { ApplicationResult } from "@/model/ApplicationResult";
import { Button } from "@/model/Button";
import { AlertService } from "@/services/AlertService";
import { DataService } from "@/services/rest/DataService";

@Component({
  components: { CollapsibleTree, PageView, SubMenu },
})
export default class DataTypesManagementView extends Vue {
  @Prop() applicationName;

  applicationService = ApplicationService.INSTANCE;
  alertService = AlertService.INSTANCE;
  dataService = DataService.INSTANCE;

  application = new ApplicationResult();
  subMenuPaths = [];
  buttons = [
    new Button(
      this.$t("referencesManagement.consult"),
      "eye",
      (label) => this.consultDataType(label),
      "is-primary"
    ),
    new Button(this.$t("referencesManagement.download"), "download"),
  ];
  dataTypes = [];

  created() {
    this.subMenuPaths = [
      new SubMenuPath(this.$t("dataTypesManagement.data-types").toLowerCase(), () =>
        this.$router.push(`/applications/${this.applicationName}/dataTypes`)
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
      if (this.application.dataTypes) {
        this.dataTypes = Object.values(this.application.dataTypes);
      }
    } catch (error) {
      this.alertService.toastServerError();
    }
  }

  consultDataType(label) {
    const dataType = this.dataTypes.find((dt) => dt.label === label);
    this.$router.push(`/applications/${this.applicationName}/dataTypes/${dataType.id}`);
  }

  openDataTypeCb(event, label) {
    event.stopPropagation();

    console.log("OPEN", label);
  }

  uploadDataTypeCsv(label, file) {
    const dataType = this.dataTypes.find((dt) => dt.label === label);
    this.dataService.addData(this.applicationName, dataType.label, file);
  }
}
</script>
