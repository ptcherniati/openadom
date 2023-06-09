<template>
  <PageView class="with-submenu">
    <SubMenu
        :aria-label="$t('menu.aria-sub-menu')"
        :paths="subMenuPaths"
        :root="application.localName || application.title"
        role="navigation"
    />
    <h1 class="title main-title">
      {{
        $t("titles.data-types-page", {
          applicationName: application.localName || application.title,
        })
      }}
    </h1>

    <div class="column is-offset-one-third is-one-third">
      <TagsCollapse
          v-if="tags && Object.keys(tags).length > 1"
          :tags="tags"
      />
    </div>
    <AvailablityChart v-if="false"/>
    <div v-if="errorsMessages.length" style="margin: 10px">
      <div v-for="msg in errorsMessages" :key="msg">
        <b-message
            :aria-close-label="$t('message.close')"
            :title="$t('message.data-type-config-error')"
            class="mt-4 DataTypesManagementView-message"
            has-icon
            type="is-danger"
        >
          <span v-html="msg"/>
        </b-message>
      </div>
    </div>
    <div>
      <CollapsibleTree
          v-for="(data, i) in dataTypesToBeShown"
          :id="i + 1"
          :key="data.id"
          :buttons="buttons(data)"
          :is-loading="isLoading"
          :level="0"
          :on-click-label-cb="(event, label) => openDataTypeCb(event, label)"
          :on-click-label-synthesis-detail-cb="
          (event, option) => openDataTypeDetailSynthesisCb(event, option)
        "
          :on-upload-cb="data.repository ? null : (label, file) => uploadDataTypeCsv(label, file)"
          :option="{
          ...data,
          synthesis: synthesis[data.id],
          synthesisMinMax: synthesisMinMax[data.id],
          withSynthesis: true,
          withTooltip: true,
        }"
          :repository="data.repository"
          :repository-redirect="(label) => showRepository(label)"
          class="liste"
      />
      <DataTypeDetailsPanel
          :application-name="applicationName"
          :close-cb="(newVal) => (openPanel = newVal)"
          :data-type="chosenDataType"
          :left-align="false"
          :open="openPanel"
          :tags="tags"
      />
      <b-modal v-model="openSynthesisDetailPanel" class="modalByAgrégation" width="100rem">
        <DetailModalCard
            :application-name="applicationName"
            :close-cb="(newVal) => (openSynthesisDetailPanel = newVal)"
            :data-type="chosenDataType"
            :open="true"
            :options="currentOptions"
        >
        </DetailModalCard>
      </b-modal>
    </div>
  </PageView>
</template>

<script>
import {Component, Prop, Vue} from "vue-property-decorator";
import PageView from "@/views/common/PageView.vue";
import {ApplicationService} from "@/services/rest/ApplicationService";
import {SynthesisService} from "@/services/rest/SynthesisService";
import SubMenu, {SubMenuPath} from "@/components/common/SubMenu.vue";
import CollapsibleTree from "@/components/common/CollapsibleTree.vue";
import {ApplicationResult} from "@/model/ApplicationResult";
import {Button} from "@/model/Button";
import {AlertService} from "@/services/AlertService";
import {DataService} from "@/services/rest/DataService";
import {HttpStatusCodes} from "@/utils/HttpUtils";
import {ErrorsService} from "@/services/ErrorsService";
import {InternationalisationService} from "@/services/InternationalisationService";
import DataTypeDetailsPanel from "@/components/datatype/DataTypeDetailsPanel.vue";
import AvailablityChart from "@/components/charts/AvailiblityChart.vue";
import DetailModalCard from "@/components/charts/DetailModalCard";
import {DownloadDatasetQuery} from "@/model/application/DownloadDatasetQuery";
import TagsCollapse from "@/components/common/TagsCollapse.vue";

@Component({
  components: {
    DetailModalCard,
    CollapsibleTree,
    PageView,
    SubMenu,
    DataTypeDetailsPanel,
    AvailablityChart,
    TagsCollapse,
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
  isLoading = false;
  subMenuPaths = [];
  dataTypes = [];
  errorsMessages = [];
  errorsList = [];
  openPanel = false;
  openSynthesisDetailPanel = false;
  currentOptions = {};
  chosenDataType = {};
  synthesis = {};
  synthesisMinMax = {};
  tags = {};

  get dataTypesToBeShown() {
    if (!this.tags) {
      return this.dataTypes;
    }
    let selectedTags = Object.keys(this.tags).filter((t) => this.tags[t].selected);
    if (!Object.keys(this.tags).length) {
      return this.dataTypes;
    }
    return this.dataTypes.filter((dataType) => {
      return dataType.tags.some((t) => {
        return selectedTags.includes(t);
      });
    });
  }

  buttons(dty) {
    return [
      new Button(
          this.$t("referencesManagement.consult"),
          "eye",
          (label) => this.consultDataType(label),
          "is-dark",
          null,
          !dty.canRead
      ),
      new Button(this.$t("referencesManagement.download"),
          "download", (label) => this.downloadDataType(label),
          null,
          null,
          !dty.canDownload
      ),
    ]
  };

  buildTags() {
    let tags = {};
    for (const dataType of this.dataTypes) {
      let currentTags = dataType.tags;
      if (!currentTags) {
        continue;
      }
      for (const tagName of currentTags) {
        if (tags[tagName]) {
          continue;
        }
        tags[tagName] = {};
        tags[tagName].selected = true;
        tags[tagName].localName = this.internationalisationService.getLocaleforPath(
            this.application,
            "internationalizedTags." + tagName,
            tagName
        );
      }
      dataType.localtags = dataType.tags.map((tag) => tags[tag]?.localName || tag);
    }
    this.tags = tags;
  }

  toggle(tag) {
    let tags = this.tags;
    tags[tag].selected = !tags[tag].selected;
    this.tags = tags;
  }

  created() {
    this.subMenuPaths = [
      new SubMenuPath(
          this.$t("dataTypesManagement.data-types").toLowerCase(),
          () => {
          },
          () => this.$router.push("/applications")
      ),
    ];

    this.init();
  }

  async init() {
    try {
      this.application = await this.applicationService.getApplication(this.applicationName, [
        "CONFIGURATION",
        "DATATYPE",
        "SYNTHESIS",
      ]);
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
      ).map(dty => {
            let authorizationsDatatypesRights = this.application.authorizationsDatatypesRights[dty.label];
            let isAdmin = authorizationsDatatypesRights.ADMIN;
            let canUpload = isAdmin || authorizationsDatatypesRights.UPLOAD;
            let canRead = isAdmin || authorizationsDatatypesRights.UPLOAD;
            let canDownload = isAdmin || authorizationsDatatypesRights.DOWNLOAD;
            let canDelete = isAdmin || authorizationsDatatypesRights.DELETE;
            let canPublish = isAdmin || authorizationsDatatypesRights.PUBLICATION;
            let any = isAdmin || authorizationsDatatypesRights.ANY;
            //let canManage = this.ownAuthorizations.isAdministrator || (this.ownAuthorizations.authorizationResults.manage || []).includes(ref.label)
            return {
              ...dty,
              isAdmin: isAdmin,
              canUpload: canUpload,
              canRead: canRead,
              canDownload: canDownload,
              canDelete: canDelete,
              canPublish: canPublish,
              any: any
            }
          }
      );
      this.buildTags();
      await this.initSynthesis();
    } catch (error) {
      this.alertService.toastServerError();
    }
  }

  async initSynthesis() {
    this.isLoading = true;
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
    this.synthesis = {...this.synthesis};
    this.synthesisMinMax = {...this.synthesisMinMax};
    this.isLoading = false;
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
    this.currentOptions = {...option};
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

  async downloadDataType(event) {
    let param = new DownloadDatasetQuery(this.application, this.applicationName, event);
    let csv = await this.dataService.getDataTypesCsv(this.applicationName, event, param);
    var hiddenElement = document.createElement("a");
    hiddenElement.href = "data:text/csv;charset=utf-8," + encodeURI(csv);

    //provide the name for the CSV file to be downloaded
    hiddenElement.download = "export.csv";
    hiddenElement.click();
    return false;
  }

  checkMessageErrors(error) {
    if (error.httpResponseCode === HttpStatusCodes.BAD_REQUEST) {
      if (error.content != null) {
        this.errorsList = [];
        error.content.then((value) => {
          for (let i = 0; i < value.length; i++) {
            console.log(value[i]);
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

.liste {
  margin-bottom: 10px;
  border: 1px solid white;
}
</style>