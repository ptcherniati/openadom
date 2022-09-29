<template>
  <PageView class="with-submenu">
    <SubMenu
      :root="application.localName || application.title"
      :paths="subMenuPaths"
      role="navigation"
      :aria-label="$t('menu.aria-sub-menu')"
    />
    <h1 class="title main-title">
      {{
        $t("titles.data-types-page", {
          applicationName: application.localName || application.title,
        })
      }}
    </h1>

    <div v-if="errorsMessages.length" style="margin: 10px">
      <div v-for="msg in errorsMessages" :key="msg">
        <b-message
          :title="$t('message.data-type-config-error')"
          type="is-danger"
          has-icon
          :aria-close-label="$t('message.close')"
          class="mt-4 DataTypesManagementView-message"
        >
          <span v-html="msg" />
        </b-message>
      </div>
    </div>
    <div>
      <CollapsibleTree
          class="liste"
          v-for="(additionalFile, i) in additionalFiles"
          :key="additionalFile.id"
          :option="additionalFile"
          :level="0"
          :id="i + 1"
          :on-click-label-cb="(event, label) => openAdditionalFileDetails(event, label)"
          :repository-redirect="(label) => showNewAdditionalFile(label)"
          :buttons="buttons"
          :application-title="$t('titles.references-page')"
          :line-count="12"
      >
      </CollapsibleTree>
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
import { HttpStatusCodes } from "@/utils/HttpUtils";
import { ErrorsService } from "@/services/ErrorsService";
import { InternationalisationService } from "@/services/InternationalisationService";
import DataTypeDetailsPanel from "@/components/datatype/DataTypeDetailsPanel.vue";
import AvailablityChart from "@/components/charts/AvailiblityChart.vue";
import DetailModalCard from "@/components/charts/DetailModalCard";

@Component({
  components: {
    DetailModalCard,
    CollapsibleTree,
    PageView,
    SubMenu,
    DataTypeDetailsPanel,
    AvailablityChart,
  },
})
export default class AdditionalFilesManagementView extends Vue {
  @Prop() applicationName;

  applicationService = ApplicationService.INSTANCE;
  internationalisationService = InternationalisationService.INSTANCE;
  alertService = AlertService.INSTANCE;
  errorsService = ErrorsService.INSTANCE;

  application = new ApplicationResult();
  subMenuPaths = [];
  buttons = [
    new Button(
      this.$t("referencesManagement.consult"),
      "eye",
      (label) => this.consultAdditionalFile(label),
      "is-dark"
    ),
    new Button(this.$t("referencesManagement.download"), "download", (label) =>
      this.downloadDataType(label)
    ),
  ];

  additionalFiles = [];
  errorsMessages = [];
  errorsList = [];
  openPanel = false;
  openSynthesisDetailPanel = false;
  currentOptions = {};
  chosenDataType = null;
  synthesis = {};
  synthesisMinMax = {};
  grantables = {}

  created() {
    this.subMenuPaths = [
      new SubMenuPath(
        this.$t("additionalFilesmanagement.additionalFilesManagement").toLowerCase(),
        () => this.$router.push(`/applications/${this.applicationName}/additionalFiles`),
        () => this.$router.push(`/applications`)
      ),
    ];
    this.init();
  }

  openAdditionalFileDetails(event, label) {
    event.stopPropagation();
    this.openPanel = this.chosenRef && this.chosenRef.label === label ? !this.openPanel : true;
    this.chosenRef = this.findAdditionalFileByLabel(label);
  }

  showNewAdditionalFile(label) {
    const additionalfile = this.findAdditionalFileByLabel(label);
    this.$router.push(`/applications/${this.applicationName}/additionalFiles/${additionalfile.id}/new`);
  }


  consultAdditionalFile(label) {
    const ref = this.findAdditionalFileByLabel(label);
    if (ref) {
      this.$router.push(`/applications/${this.applicationName}/additionalFiles/${ref.id}/consult`);
    }
  }

  findAdditionalFileByLabel(label) {
    var ref = this.additionalFiles.find((dt) => dt.label === label);
    return ref;
  }


  async init() {
    try {
      this.application = await this.applicationService.getApplication(this.applicationName);
      this.application = {
        ...this.application,
        localName: this.internationalisationService.mergeInternationalization(this.application)
            .localName,
      };
      if (!this.application?.id) {
        return;
      }
      this.additionalFiles =
          this.convertToNode(this.internationalisationService.additionalFilesNames(this.application));
    } catch (error) {
      this.alertService.toastServerError();
    }
  }
  convertToNode(additionalFiles){
    let result = []
    for (const additionalFilesKey in additionalFiles) {
      let additionalFile = additionalFiles[additionalFilesKey]
      let af = {
        children:[],
        fields: additionalFile.fields,
        id:additionalFilesKey,
        label: additionalFile.refNameLocal || additionalFile.name,
        localName: additionalFile.refNameLocal || additionalFile.name,
        name: additionalFile.name,

      }
      result.push(af)
    }
    return result
  }
  checkMessageErrors(error) {
    if (error.httpResponseCode === HttpStatusCodes.BAD_REQUEST) {
      if (error.content != null) {
        this.errorsList = [];
        error.content.then((value) => {
          for (let i = 0; i < value.length; i++) {
            this.errorsList[i] = value[i];
          }
          if (this.errorsList.length !== 0) {
            this.errorsMessages = this.errorsService.getCsvErrorsMessages(this.errorsList);
          } else {
            this.errorsMessages = this.errorsService.getErrorsMessages(error);
          }
        });
      }
    } else {
      this.alertService.toastServerError(error);
    }
  }
}
</script>

<style lang="scss">
.DataTypesManagementView-message {
  .media-content {
    width: calc(100% - 3em - 4rem);
    overflow-wrap: break-word;
  }
}
.liste {
  margin-bottom: 10px;
  border: 1px solid white;
}
</style>