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
    <div v-if="errorsMessages.length">
      <div v-for="msg in errorsMessages" v-bind:key="msg">
        <b-message
          :title="$t('message.data-type-config-error')"
          type="is-danger"
          has-icon
          :aria-close-label="$t('message.close')"
          class="mt-4"
        >
          <span v-html="msg" />
        </b-message>
      </div>
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
import { HttpStatusCodes } from "@/utils/HttpUtils";
import { ErrorsService } from "@/services/ErrorsService";

@Component({
  components: { CollapsibleTree, PageView, SubMenu },
})
export default class DataTypesManagementView extends Vue {
  @Prop() applicationName;

  applicationService = ApplicationService.INSTANCE;
  alertService = AlertService.INSTANCE;
  dataService = DataService.INSTANCE;
  errorsService = ErrorsService.INSTANCE;

  application = new ApplicationResult();
  subMenuPaths = [];
  buttons = [
    new Button(
      this.$t("referencesManagement.consult"),
      "eye",
      (label) => this.consultDataType(label),
      "is-primary"
    ),
    new Button(this.$t("referencesManagement.download"), "download", (label) =>
      this.downloadDataType(label)
    ),
  ];
  dataTypes = [];
  errorsMessages = [];

  created() {
    this.subMenuPaths = [
      new SubMenuPath(
        this.$t("dataTypesManagement.data-types").toLowerCase(),
        () => {},
        () => this.$router.push("/applications")
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

  async uploadDataTypeCsv(label, file) {
    this.errorsMessages = [];
    try {
      await this.dataService.addData(this.applicationName, label, file);
      this.alertService.toastSuccess(this.$t("alert.data-updated"));
    } catch (error) {
      this.checkMessageErrors(error);
    }
  }

  async downloadDataType(label) {
    this.dataService.getDataTypesCsv(this.applicationName, label);
  }

  checkMessageErrors(error) {
    if (error.httpResponseCode === HttpStatusCodes.BAD_REQUEST) {
      this.errorsMessages = this.errorsService.getCsvErrorsMessages(error.content);
    } else {
      this.alertService.toastServerError(error);
    }
  }
}
</script>
