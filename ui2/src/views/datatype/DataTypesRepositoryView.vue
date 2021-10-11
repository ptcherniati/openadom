<template>
  <div>
    <PageView class="with-submenu">
      <SubMenu :root="application.localName || application.title" :paths="subMenuPaths" />
      <h1 class="title main-title">
        {{
          $t("titles.data-types-repository", { applicationName: localDatatypeName || dataTypeId })
        }}
      </h1>
      <div class="columns">
        <div class="column" v-for="(authReference, key) in authReferences" :key="key">
          <div class="columns">
            <div class="column" style="padding-top: 20px">
              <p style="text-transform: capitalize">{{ key }}</p>
            </div>
            <div class="column">
              <b-field>
                <b-checkbox
                  v-for="option in authReference.referenceValues"
                  :key="option.naturalKey"
                >
                  {{ option.naturalKey }} {{ option.values.zet_nom_key }}</b-checkbox
                >
              </b-field>
            </div>
          </div>
        </div>
        <div class="column" style="padding-top: 20px">
          <h1>
            {{
              Object.entries(this.selected.requiredauthorizations)
                .map((e) => e[0] + " : " + e[1])
                .join(", ")
            }}
          </h1>
        </div>
      </div>
      <div class="columns">
        <div class="column">
          <form class="card">
            <b-collapse class="card" animation="slide" aria-id="fileDeposit">
              <template #trigger="props">
                <div class="card-header" role="button" aria-controls="fileDeposit">
                  <p class="card-header-title">
                    {{ $t("dataTypesRepository.card-title-upload-file") }}
                  </p>
                  <a class="card-header-icon">
                    <b-icon :icon="props.open ? 'chevron-down' : 'chevron-up'"> </b-icon>
                  </a>
                </div>
              </template>
              <div class="card-content">
                <div class="content">
                  <div class="columns">
                    <div class="column">
                      <b-field :label="$t('dataTypesRepository.start-date')">
                        <b-datepicker
                          :placeholder="$t('dataTypesRepository.placeholder-datepicker')"
                          icon="calendar"
                          editable
                          v-model="startDate"
                        >
                        </b-datepicker>
                      </b-field>
                    </div>
                    <div class="column">
                      <b-field :label="$t('dataTypesRepository.end-date')">
                        <b-datepicker
                          :placeholder="$t('dataTypesRepository.placeholder-datepicker')"
                          icon="calendar"
                          editable
                          v-model="endDate"
                        >
                        </b-datepicker>
                      </b-field>
                    </div>
                  </div>
                  <b-upload v-model="file" class="file-label">
                    <span class="file-cta">
                      <b-icon class="file-icon" icon="upload"></b-icon>
                      <span class="file-label">{{ $t("dataTypesRepository.choose-file") }}</span>
                    </span>
                    <span class="file-name" v-if="file">
                      {{ file.name }}
                    </span>
                  </b-upload>
                </div>
              </div>
              <footer class="card-footer">
                <div class="column is-4">
                  <b-button type="is-dark" @click="upload">{{
                    $t("dataTypesRepository.submit")
                  }}</b-button>
                </div>
              </footer>
            </b-collapse>
          </form>
        </div>
      </div>
      <div class="card">
        <div class="card-content" v-if="isAuthorisationsSelected()">
          <table
            class="table is-bordered is-striped is-fullwidth"
            v-if="datasets && Object.keys(datasets).length"
          >
            <caption>
              {{
                $t("dataTypesRepository.list-file-data")
              }}
            </caption>
            <tr>
              <th align>{{ $t("dataTypesRepository.table-file-data-period") }}</th>
              <th align>{{ $t("ponctuation.star") }}</th>
              <th align>{{ $t("dataTypesRepository.table-file-data-publication") }}</th>
            </tr>
            <tr
              @click="showDatasets(dataset)"
              v-for="(dataset, periode) in datasets"
              :key="dataset.id"
            >
              <td align>{{ periode }}</td>
              <td align>{{ Object.keys(dataset.datasets).length }}</td>
              <td align>{{ dataset.publication }}</td>
            </tr>
          </table>
          <table
            class="table is-bordered is-striped is-fullwidth"
            v-if="currentDataset && currentDataset.length"
          >
            <caption>
              {{
                $t("dataTypesRepository.list-file-data-period")
              }}
              {{
                currentDataset[0].periode
              }}
            </caption>
            <tr>
              <th align>{{ $t("dataTypesRepository.table-file-data-id") }}</th>
              <th align>{{ $t("dataTypesRepository.table-file-data-size") }}</th>
              <th align>{{ $t("dataTypesRepository.table-file-data-create") }}</th>
              <th align>{{ $t("dataTypesRepository.table-file-data-create-by") }}</th>
              <th align>{{ $t("dataTypesRepository.table-file-data-publish") }}</th>
              <th align>{{ $t("dataTypesRepository.table-file-data-publish-by") }}</th>
              <th align>{{ $t("dataTypesRepository.table-file-data-publication") }}</th>
              <th align>{{ $t("dataTypesRepository.table-file-data-delete") }}</th>
            </tr>
            <tr v-for="dataset in currentDataset" :key="dataset.id">
              <td align>{{ dataset.id.slice(0, 8) }}</td>
              <td align>{{ dataset.size }}</td>
              <td align>{{ UTCToString(dataset.params.createdate) }}</td>
              <td align>{{ dataset.createuser }}</td>
              <td align>{{ UTCToString(dataset.params.publisheddate) }}</td>
              <td align>{{ dataset.publisheduser }}</td>
              <td align>
                <b-field>
                  <b-button
                    type="is-primary is-light"
                    size="is-large"
                    :icon-right="dataset.params.published ? 'check-circle' : 'circle'"
                    @click="publish(dataset, !dataset.params.published)"
                  />
                </b-field>
              </td>
              <td>
                <b-field>
                  <b-button
                    type="is-danger"
                    size="is-medium"
                    icon-right="trash-alt"
                    @click="remove(dataset, dataset.params.published)"
                  />
                </b-field>
              </td>
            </tr>
          </table>
        </div>
      </div>
    </PageView>
  </div>
</template>

<script>
import { Component, Prop, Vue } from "vue-property-decorator";
import PageView from "@/views/common/PageView.vue";
import { ApplicationService } from "@/services/rest/ApplicationService";
import { ApplicationResult } from "@/model/ApplicationResult";
import CollapsibleTree from "@/components/common/CollapsibleTree.vue";
import { AlertService } from "@/services/AlertService";
import { DataService } from "@/services/rest/DataService";
import { FileService } from "@/services/rest/FileService";
import { ReferenceService } from "@/services/rest/ReferenceService";
import { ErrorsService } from "@/services/ErrorsService";
import SubMenu, { SubMenuPath } from "@/components/common/SubMenu.vue";
import { BinaryFileDataset } from "@/model/file/BinaryFileDataset";
import { BinaryFile } from "@/model/file/BinaryFile";
import { FileOrUUID } from "@/model/file/FileOrUUID";
import { Dataset } from "@/model/file/Dataset";
import { InternationalisationService } from "@/services/InternationalisationService";

@Component({
  components: { CollapsibleTree, PageView, SubMenu },
})
export default class DataTypesRepositoryView extends Vue {
  @Prop() applicationName;
  @Prop() dataTypeId;
  @Prop() applicationConfiguration;

  referenceService = ReferenceService.INSTANCE;
  fileService = FileService.INSTANCE;
  applicationService = ApplicationService.INSTANCE;
  alertService = AlertService.INSTANCE;
  dataService = DataService.INSTANCE;
  errorsService = ErrorsService.INSTANCE;
  internationalisationService = InternationalisationService.INSTANCE;

  subMenuPaths = [];
  application = new ApplicationResult();
  applications = [];
  configuration = {};
  authorizations = [];
  authReferences = {};
  selected = null;
  datasets = {};
  file = null;
  startDate = null;
  endDate = null;
  currentDataset = null;
  mounted() {
    this.$on("authorizationChanged", this.updateDatasets);
    this.$on("uploaded", this.updateDatasets);
    this.$on("published", this.updateDatasets);
    this.$on("deleted", this.updateDatasets);
    this.$on("listFilesUploaded", this.getDatasetMap);
  }

  created() {
    const prevPath = `/applications/${this.applicationName}/dataTypes`;
    this.subMenuPaths = [
      new SubMenuPath(
        this.dataTypeId.toLowerCase(),
        () => {},
        () => this.$router.push(prevPath)
      ),
    ];

    this.init();
  }

  async init() {
    try {
      this.applications = await this.applicationService.getApplications();
      this.application = await this.applicationService.getApplication(this.applicationName);
      this.application = {
        ...this.application,
        localName: this.internationalisationService.localeApplicationName(this.application),
      };
      this.localDatatypeName =
        this.application.dataTypes[this.dataTypeId]?.internationalizationName?.[this.$i18n.locale];
      this.configuration = this.applications
        .filter((a) => a.name === this.applicationName)
        .map((a) => a.configuration.dataTypes[this.dataTypeId])[0];
      this.authorizations = this.configuration.authorization.authorizationScopes;
      let ret = {};
      for (let auth in this.authorizations) {
        let vc = this.authorizations[auth];
        var reference =
          this.configuration.data[vc.variable].components[vc.component].checker.params.refType;
        let ref = await this.referenceService.getReferenceValues(this.applicationName, reference);
        ret[auth] = ref;
      }
      this.authReferences = ret;
      this.selected = new BinaryFileDataset({
        datatype: this.dataTypeId,
        requiredauthorizations: Object.keys(this.authReferences).reduce((acc, auth) => {
          acc[auth] = null;
          return acc;
        }, {}),
        from: "",
        to: "",
      });
    } catch (error) {
      this.alertService.toastServerError();
    }
  }
  UTCToString(utcString) {
    return utcString && utcString.replace(/(\d{4})-(\d{2})-(\d{2}).*/, "$3/$2/$1");
  }
  periodeToString(dataset) {
    return this.periodeToStringForBinaryFileDataset(dataset.params.binaryFiledataset);
  }
  periodeToStringForBinaryFileDataset(binaryFiledataset) {
    return (
      "du " +
      this.dateToString(binaryFiledataset.from) +
      " au " +
      this.dateToString(binaryFiledataset.to)
    );
  }
  async showDatasets(dataset) {
    if (!dataset) {
      this.currentDataset = null;
    }
    var datasets = dataset?.datasets?.sort((d1, d2) => d1.params.createDate < d2.params.createDate);
    this.currentDataset = datasets;
  }
  async upload() {
    if (this.file && this.startDate && this.endDate) {
      var fileOrId = new FileOrUUID(
        null,
        new BinaryFileDataset(
          this.dataTypeId,
          this.selected.requiredauthorizations,
          /(.{10})T(.{8}).*/
            .exec(new Date(this.startDate).toISOString())
            .filter((a, i) => i != 0)
            .join(" "),
          /(.{10})T(.{8}).*/
            .exec(new Date(this.endDate).toISOString())
            .filter((a, i) => i != 0)
            .join(" ")
        ),
        false
      );
      var uuid = await this.dataService.addData(
        this.applicationName,
        this.dataTypeId,
        this.file,
        fileOrId
      );
      this.$emit("uploaded", uuid);
    }
  }
  async publish(dataset, pusblished) {
    dataset.params.published = pusblished;
    var fileOrId = new FileOrUUID(dataset.id, dataset.params.binaryFiledataset, pusblished);
    var uuid = await this.dataService.addData(
      this.applicationName,
      this.dataTypeId,
      null,
      fileOrId
    );
    this.$emit("published", uuid.fileId);
  }
  selectAuthorization(key, value) {
    this.selected.requiredauthorizations[key] = value;
    this.datasets = this.currentDataset = null;
    if (this.isAuthorisationsSelected()) {
      this.$emit("authorizationChanged");
    }
  }
  dateToString(dateString) {
    var today = new Date(dateString);
    var dd = String(today.getDate()).padStart(2, "0");
    var mm = String(today.getMonth() + 1).padStart(2, "0"); //January is 0!
    var yyyy = today.getFullYear();

    today = dd + "/" + mm + "/" + yyyy;
    return today;
  }
  isAuthorisationsSelected() {
    return (
      this.selected && Object.values(this.selected.requiredauthorizations).every((v) => v?.length)
    );
  }
  async updateDatasets(uuid) {
    if (this.isAuthorisationsSelected()) {
      let datasetsList = await this.fileService.getFiles(
        this.applicationName,
        this.dataTypeId,
        this.selected
      );
      if (!datasetsList || !datasetsList.length) {
        this.datasets = {};
        this.currentDataset = null;
        return;
      }
      this.$emit("listFilesUploaded", {
        binaryFileList: datasetsList.map((d) => new BinaryFile(d)),
        uuid: uuid,
      });
    }
  }
  getDatasetMap(fileList) {
    var datasetMap = {};
    for (var index in fileList.binaryFileList) {
      var file = fileList.binaryFileList[index];
      var currentDataset = datasetMap[this.periodeToString(file)] || new Dataset(file);
      currentDataset.addDataset(file);
      datasetMap[this.periodeToString(file)] = currentDataset;
    }
    this.datasets = datasetMap;
    if (fileList.uuid) {
      var periode =
        fileList.uuid &&
        this.datasets &&
        Object.values(this.datasets).find((e) => e.findByUUID(fileList.uuid))?.periode;
      this.currentDataset = this.datasets[periode].datasets;
    }
    return this.datasets;
  }
  remove(dataset, isPublished) {
    this.$buefy.dialog.confirm({
      message:
        (isPublished
          ? "<b>La version contient des données publiées.</b><br /> La supprimer entraînera la suppression de ces données.<br /><br />?"
          : "") + "Etes vous sûr de vouloir supprimer cette version?",
      onConfirm: () => this.deleteFile(dataset.id),
    });
  }
  async deleteFile(uuid) {
    var deleted = await this.fileService.remove(this.applicationName, uuid);
    this.$emit("deleted", deleted);
  }
}
</script>

<style lang="scss">
.DataTypesRepositoryView-message {
  .media-content {
    width: calc(100% - 3em - 4rem);
    overflow-wrap: break-word;
  }
}
table.datasetsPanel {
  width: 50%;
}
table.datasetsPanel,
table.datasetsPanel th,
table.datasetsPanel td {
  border: 1px solid rgb(94, 65, 219);
  border-collapse: collapse;
  text-align: center;
}
</style>
