<template>
  <PageView class="with-submenu">
    <SubMenu
      :root="application.localName"
      :paths="subMenuPaths"
      role="navigation"
      :aria-label="$t('menu.aria-sub-menu')"
    />
    <h1 class="title main-title">
      {{ $t("titles.references-page", { applicationName: application.localName }) }}
    </h1>
    <div v-if="errorsMessages.length" style="margin: 10px">
      <div v-for="msg in errorsMessages" :key="msg">
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
    <div>
      <CollapsibleTree
        class="liste"
        v-for="(ref, i) in references"
        :key="ref.id"
        :option="ref"
        :level="0"
        :id="i + 1"
        :on-click-label-cb="(event, label) => openRefDetails(event, label)"
        :on-upload-cb="(label, refFile) => uploadReferenceCsv(label, refFile)"
        :buttons="buttons"
        :application-title="$t('titles.references-page')"
        :line-count="lineCount(ref)"
      >
      </CollapsibleTree>
      <ReferencesDetailsPanel
        :left-align="false"
        :open="openPanel"
        :reference="chosenRef"
        :close-cb="(newVal) => (openPanel = newVal)"
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
import { InternationalisationService } from "@/services/InternationalisationService";
import { ReferenceService } from "@/services/rest/ReferenceService";

import PageView from "../common/PageView.vue";
import { ApplicationResult } from "@/model/ApplicationResult";
import SubMenu, { SubMenuPath } from "@/components/common/SubMenu.vue";
import { AlertService } from "@/services/AlertService";
import { Button } from "@/model/Button";
import { HttpStatusCodes } from "@/utils/HttpUtils";
import { ErrorsService } from "@/services/ErrorsService";

@Component({
  components: { CollapsibleTree, ReferencesDetailsPanel, PageView, SubMenu },
})
export default class ReferencesManagementView extends Vue {
  @Prop() applicationName;

  applicationService = ApplicationService.INSTANCE;
  referenceService = ReferenceService.INSTANCE;
  internationalisationService = InternationalisationService.INSTANCE;
  alertService = AlertService.INSTANCE;
  errorsService = ErrorsService.INSTANCE;

  references = [];
  currentPage = 1;
  openPanel = false;
  chosenRef = null;
  application = new ApplicationResult();
  subMenuPaths = [];
  errorsMessages = [];
  errorsList = [];
  buttons = [
    new Button(
      this.$t("referencesManagement.consult"),
      "eye",
      (label) => this.consultReference(label),
      "is-dark"
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
      this.application = {
        ...this.application,
        localName: this.internationalisationService.mergeInternationalization(this.application)
          .localName,
      };
      if (!this.application?.id) {
        return;
      }
      this.references = convertReferencesToTrees(
        Object.values(this.internationalisationService.treeReferenceName(this.application))
      );
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

  lineCount(ref) {
    for (let i = 0; i <= this.application.referenceSynthesis.length - 1; i++) {
      if (this.application.referenceSynthesis[i].referenceType === ref.label) {
        return this.application.referenceSynthesis[i].lineCount;
      } else {
        for (let n = 0; n < ref.children.length; n++) {
          if (this.application.referenceSynthesis[i].referenceType === ref.children[n].label) {
            ref.children[n] = {
              ...ref.children[n],
              lineCountChild: this.application.referenceSynthesis[i].lineCount,
            };
          } else {
            for (let j = 0; j < ref.children[n].children.length; j++) {
              if (
                this.application.referenceSynthesis[i].referenceType ===
                ref.children[n].children[j].label
              ) {
                ref.children[n].children[j] = {
                  ...ref.children[n].children[j],
                  lineCountChild: this.application.referenceSynthesis[i].lineCount,
                };
              }
            }
          }
        }
      }
    }
  }

  async downloadReference(label) {
    const reference = this.findReferenceByLabel(label);
    if (reference) {
      let csv = await this.referenceService.getReferenceCsv(this.applicationName, reference.id);
      var hiddenElement = document.createElement("a");
      hiddenElement.href = "data:text/csv;charset=utf-8," + encodeURI(csv);

      //provide the name for the CSV file to be downloaded
      hiddenElement.download = "export.csv";
      hiddenElement.click();
      return false;
    }
  }

  async uploadReferenceCsv(label, refFile) {
    this.errorsMessages = [];
    const reference = this.findReferenceByLabel(label);
    try {
      await this.referenceService.createReference(this.applicationName, reference.id, refFile);
      this.alertService.toastSuccess(this.$t("alert.reference-updated"));
    } catch (errors) {
      await this.checkMessageErrors(errors);
    }
  }

  async checkMessageErrors(errors) {
    if (errors.httpResponseCode === HttpStatusCodes.BAD_REQUEST) {
      errors.content.then((value) => {
        for (let i = 0; i < value.length; i++) {
          this.errorsList[i] = value[i];
        }
        if (this.errorsList.length !== 0) {
          this.errorsMessages = this.errorsService.getCsvErrorsMessages(this.errorsList);
        } else {
          this.errorsMessages = this.errorsService.getErrorsMessages(errors);
        }
      });
    } else {
      this.alertService.toastError(this.$t("alert.reference-csv-upload-error"), errors);
    }
  }

  findReferenceByLabel(label) {
    var ref = Object.values(this.application.references).find((ref) => ref.label === label);
    return ref;
  }
}
</script>
<style lang="scss" scoped>
.liste {
  margin-bottom: 10px;
  border: 1px solid white;
}
</style>
