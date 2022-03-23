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

    <AvailablityChart v-if="false" />
    <div>
      <CollapsibleTree
        v-for="data in dataTypes"
        :key="data.id"
        :option="{
          ...data,
          synthesis: synthesis[data.id],
          synthesisMinMax: synthesisMinMax[data.id],
          withSynthesis: true,
        }"
        :level="0"
        :onClickLabelCb="(event, label) => openDataTypeCb(event, label)"
        :onClickLabelSynthesisDetailCb="
          (event, option) => openDataTypeDetailSynthesisCb(event, option)
        "
        :onUploadCb="data.repository ? null : (label, file) => uploadDataTypeCsv(label, file)"
        :repository="data.repository"
        :repositoryRedirect="(label) => showRepository(label)"
        :buttons="buttons"
      />
      <DataTypeDetailsPanel
        :leftAlign="false"
        :open="openPanel"
        :dataType="chosenDataType"
        :closeCb="(newVal) => (openPanel = newVal)"
        :applicationName="applicationName"
      />
      <b-modal v-model="openSynthesisDetailPanel" width="100rem">
        <DetailModalCard
          v-show="openSynthesisDetailPanel"
          :options="currentOptions"
          :dataType="chosenDataType"
          :closeCb="(newVal) => (openSynthesisDetailPanel = newVal)"
          :applicationName="applicationName"
        >
        </DetailModalCard>
      </b-modal>
    </div>
    <div v-if="errorsMessages.length">
      <div v-for="msg in errorsMessages" v-bind:key="msg">
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
  </PageView>
</template>

<script>
import { Component, Prop, Vue } from "vue-property-decorator";
import PageView from "@/views/common/PageView.vue";
import { ApplicationService } from "@/services/rest/ApplicationService";
import { SynthesisService } from "@/services/rest/SynthesisService";
import SubMenu, { SubMenuPath } from "@/components/common/SubMenu.vue";
import CollapsibleTree from "@/components/common/CollapsibleTree.vue";
import { ApplicationResult } from "@/model/ApplicationResult";
import { Button } from "@/model/Button";
import { AlertService } from "@/services/AlertService";
import { DataService } from "@/services/rest/DataService";
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
export default class DataTypesManagementView extends Vue {
  @Prop() applicationName;

  applicationService = ApplicationService.INSTANCE;
  synthesisService = SynthesisService.INSTANCE;
  internationalisationService = InternationalisationService.INSTANCE;
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
      "is-dark"
    ),
    new Button(this.$t("referencesManagement.download"), "download", (label) =>
      this.downloadDataType(label)
    ),
  ];
  dataTypes = [];
  errorsMessages = [];
  openPanel = false;
  openSynthesisDetailPanel = false;
  currentOptions = {};
  chosenDataType = null;
  synthesis = {};
  synthesisMinMax = {};

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
      this.application = {
        ...this.application,
        localName: this.internationalisationService.mergeInternationalization(this.application)
          .localName,
      };
      if (!this.application?.id) {
        return;
      }
      this.dataTypes = Object.values(
        this.internationalisationService.localeDatatypeName(this.application)
      );
      this.initSynthesis();
    } catch (error) {
      this.alertService.toastServerError();
    }
  }
  async initSynthesis() {
    for (const datatype in this.application.dataTypes) {
      let minmaxByDatatypes = [];
      let synthesis = await this.synthesisService.getSynthesis(this.applicationName, datatype);
      for (const variable in synthesis) {
        let resultByAggregation = {
          variable,
          ranges: [],
          minmax: [],
        };
        let rangesForVariable = synthesis[variable];
        let minmaxByVariable = [];
        for (const aggregationIndex in rangesForVariable) {
          let aggregation = rangesForVariable[aggregationIndex].aggregation;
          let unit = rangesForVariable[aggregationIndex].unit;
          let ranges = rangesForVariable[aggregationIndex].ranges;
          let minmax = ranges.reduce((acc, range) => {
            resultByAggregation.ranges = [...resultByAggregation.ranges, range.range];
            let min = acc[0];
            let max = acc[0];
            min = min ? (min <= range.range[0] ? min : range.range[0]) : range.range[0];
            max = max ? (max >= range.range[1] ? max : range.range[1]) : range.range[1];
            return [min, max];
          }, []);
          minmaxByVariable[0] = minmaxByVariable[0]
            ? minmaxByVariable[0] < minmax[0]
              ? minmaxByVariable[0]
              : minmax[0]
            : minmax[0];
          minmaxByVariable[1] = minmaxByVariable[1]
            ? minmaxByVariable[1] < minmax[1]
              ? minmaxByVariable[1]
              : minmax[1]
            : minmax[1];

          resultByAggregation[aggregation] = {
            variable,
            aggregation,
            unit,
            ranges,
            minmax,
          };
        }
        resultByAggregation.minmax = minmaxByVariable;
        minmaxByDatatypes[0] = minmaxByDatatypes[0]
          ? minmaxByDatatypes[0] < minmaxByVariable[0]
            ? minmaxByDatatypes[0]
            : minmaxByVariable[0]
          : minmaxByVariable[0];
        minmaxByDatatypes[1] = minmaxByDatatypes[1]
          ? minmaxByDatatypes[1] < minmaxByVariable[1]
            ? minmaxByDatatypes[1]
            : minmaxByVariable[1]
          : minmaxByVariable[1];
        this.synthesis[datatype] = this.synthesis[datatype] || {};
        this.synthesis[datatype].minmax = minmaxByDatatypes;
        this.synthesis[datatype].ranges = this.synthesis[datatype].ranges || [];
        this.synthesis[datatype].ranges = [
          ...this.synthesis[datatype].ranges,
          ...resultByAggregation.ranges,
        ];
        this.synthesis[datatype][variable] = resultByAggregation;
      }
      if (minmaxByDatatypes.length) this.synthesisMinMax[datatype] = minmaxByDatatypes;
    }
    this.synthesis = { ...this.synthesis };
    this.synthesisMinMax = { ...this.synthesisMinMax };
  }

  consultDataType(label) {
    const dataType = this.dataTypes.find((dt) => dt.label === label);
    this.$router.push(`/applications/${this.applicationName}/dataTypes/${dataType.id}`);
  }

  openDataTypeCb(event, label) {
    event.stopPropagation();
    this.openPanel =
      this.chosenDataType && this.chosenDataType.label === label ? !this.openPanel : true;
    this.chosenDataType = this.dataTypes.find((dt) => dt.label === label);
  }

  openDataTypeDetailSynthesisCb(event, option) {
    event.stopPropagation();
    this.currentOptions = { ...option };
    this.openSynthesisDetailPanel =
      this.chosenDataType && this.chosenDataType.label === option.label
        ? !this.openSynthesisDetailPanel
        : true;
    this.chosenDataType = this.dataTypes.find((dt) => dt.label === option.label);
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

  async downloadDataType(event, label) {
    this.dataService.getDataTypesCsv(this.applicationName, label);
  }

  checkMessageErrors(error) {
    if (error.httpResponseCode === HttpStatusCodes.BAD_REQUEST) {
      if (error.content != null) {
        this.errorsMessages = this.errorsService.getCsvErrorsMessages(error.content);
      } else {
        this.alertService.toastServerError(error);
      }
    } else {
      this.alertService.toastServerError(error);
    }
  }
  showRepository(label) {
    const dataType = this.dataTypes.find((dt) => dt.label === label);
    this.$router.push(`/applications/${this.applicationName}/dataTypesRepository/${dataType.id}`);
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
</style>
