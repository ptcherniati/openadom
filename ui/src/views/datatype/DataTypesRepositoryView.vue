<template>
  <div>
    <PageView class="with-submenu">
      <SubMenu
        :paths="subMenuPaths"
        :root="application.localName || application.title"
        role="navigation"
        :aria-label="$t('menu.aria-sub-menu')"
      />
      <h1 class="title main-title">
        {{
          $t("titles.data-types-repository", {
            applicationName: application.localDatatypeName || dataTypeId,
          })
        }}
      </h1>
      <div class="columns">
        <div v-for="(authReference, authKey) in authReferences" :key="authKey" class="column is-3">
          <div class="columns">
            <div class="column">
              <b-field>
                <b-dropdown :ref="authKey" expanded>
                  <template #trigger="{ active }">
                    <b-button
                      expanded
                      :icon-right="active ? 'chevron-up' : 'chevron-down'"
                      type="is-primary"
                    >
                      {{
                        internationalisationService.getLocaleforPath(
                          application,
                          getAuthorizationScopePath(authKey),
                          authKey
                        )
                      }}
                    </b-button>
                  </template>
                  <DropDownMenu
                    v-for="(option, optionKey) in authReference"
                    :key="optionKey"
                    :option="option"
                    @select-menu-item="selectAuthorization(authKey, $event)"
                  />
                </b-dropdown>
              </b-field>
            </div>
          </div>
        </div>
        <div class="column" style="padding-top: 20px">
          <h1>
            {{
              this.requiredAuthorizationsObject
                ? Object.entries(this.requiredAuthorizationsObject)
                  .filter((e) => e[1])
                  .map(
                    (e) =>
                      internationalisationService.getLocaleforPath(
                          application,
                          getAuthorizationScopePath(e[0]),
                          e[0]
                      ) +
                      " : " +
                      e[1]
                  )
                  .join(", ")
                : ""
            }}
          </h1>
        </div>
      </div>
      <div class="columns">
        <div class="column">
          <form class="card">
            <b-collapse animation="slide" aria-id="fileDeposit" class="card">
              <template #trigger="props">
                <div aria-controls="fileDeposit" class="card-header" role="button">
                  <h2 class="card-header-title">
                    {{ $t("dataTypesRepository.card-title-upload-file") }}
                  </h2>
                  <a class="card-header-icon">
                    <b-icon :icon="props.open ? 'chevron-down' : 'chevron-up'"></b-icon>
                  </a>
                </div>
              </template>
              <div class="card-content">
                <div class="content">
                  <div class="columns">
                    <div class="column">
                      <b-field :label="$t('dataTypesRepository.start-date')" data-cy="dateStart">
                        <b-datepicker
                          v-model="startDate"
                          :date-parser="parseDate"
                          :date-formatter="formatDate"
                          :placeholder="
                            $t('dataTypesRepository.placeholder-datepicker') +
                            ' dd-MM-YYYY, dd-MM-YYYY hh, dd-MM-YYYY hh:mm, dd-MM-YYYY HH:mm:ss'
                          "
                          editable
                          icon="calendar"
                        >
                        </b-datepicker>
                      </b-field>
                    </div>
                    <div class="column">
                      <b-field :label="$t('dataTypesRepository.end-date')" data-cy="dateEnd">
                        <b-datepicker
                          v-model="endDate"
                          :date-parser="parseDate"
                          :date-formatter="formatDate"
                          :placeholder="
                            $t('dataTypesRepository.placeholder-datepicker') +
                            ' dd-MM-YYYY, dd-MM-YYYY hh, dd-MM-YYYY hh:mm, dd-MM-YYYY HH:mm:ss'
                          "
                          editable
                          icon="calendar"
                        >
                        </b-datepicker>
                      </b-field>
                    </div>
                    <div class="column">
                      <b-upload
                        @input="changeFile"
                        v-model="file"
                        class="file-label"
                        style="margin-top: 30px"
                        data-cy="changeFileButton"
                      >
                        <span class="file-cta">
                          <b-icon class="file-icon" icon="upload"></b-icon>
                          <span class="file-label">{{
                            $t("dataTypesRepository.choose-file")
                          }}</span>
                        </span>
                        <span v-if="file" class="file-name">
                          {{ file.name }}
                        </span>
                      </b-upload>
                    </div>
                  </div>
                  <div class="columns">
                    <b-field class="column" :label="$t('dataTypesRepository.comment')" expanded>
                      <b-input v-model="comment" maxlength="200" type="textarea"></b-input>
                    </b-field>
                  </div>
                </div>
              </div>
              <footer class="card-footer">
                <div class="column is-10"></div>
                <div class="column is-2" style="float: right">
                  <b-button type="is-dark" @click="upload" style="float: right" expanded
                    >{{ $t("dataTypesRepository.submit") }}
                  </b-button>
                </div>
              </footer>
            </b-collapse>
          </form>
        </div>
      </div>
      <div v-if="isAuthorisationsSelected()" class="columns">
        <div class="card column">
          <div class="card-content">
            <table
              v-if="datasets && Object.keys(datasets).length"
              class="table is-striped is-fullwidth numberData"
              style="text-align: center; vertical-align: center"
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
                v-for="(dataset, periode) in datasets"
                :key="dataset.id"
                @click="showDatasets(dataset)"
                @keypress.enter="showDatasets(dataset)"
                tabindex="0"
                style="cursor: pointer"
              >
                <td align>{{ periode }}</td>
                <td align>{{ Object.keys(dataset.datasets).length }}</td>
                <td align>{{ dataset.publication }}</td>
              </tr>
            </table>
            <table
              v-if="currentDataset && currentDataset.length"
              class="table is-striped is-fullwidth"
              style="text-align: center; vertical-align: center"
            >
              <caption>
                {{
                  $t("dataTypesRepository.list-file-data-period")
                }}
                {{
                  currentDataset[0].periode
                }}
                <div v-if="errorsMessages.length" style="margin: 10px">
                  <div v-for="msg in errorsMessages" v-bind:key="msg">
                    <b-message
                        :title="$t('message.data-type-config-error')"
                        type="is-danger"
                        has-icon
                        :aria-close-label="$t('message.close')"
                        class="mt-4 DataTypesManagementView-message"
                    >
                      <span v-html="msg"/>
                    </b-message>
                  </div>
                </div>
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
                <td align>
                  <b-tooltip type="is-dark" :id="dataset.id" multilined role="tooltip">
                    <template v-slot:content>
                      <h3>{{ $t("dataTypesRepository.comment") }} {{ $t("ponctuation.colon") }}</h3>
                      <p>{{ UTCToString(dataset.params.binaryFiledataset.comment) }}</p>
                    </template>
                    <a
                      :aria-describedby="dataset.id"
                      tabindex="0"
                      @keypress.enter="changeCss(dataset.id)"
                    >{{ dataset.id.slice(0, 8) }}</a
                    >
                  </b-tooltip>
                </td>
                <td align>{{ dataset.size }}</td>
                <td align>{{ formatDate(dataset.params.createdate) }}</td>
                <td align>{{ dataset.createuser }}</td>
                <td align>{{ formatDate(dataset.params.publisheddate) }}</td>
                <td align>{{ dataset.publisheduser }}</td>
                <td align>
                  <b-field>
                    <b-button
                      :icon-right="dataset.params.published ? 'check-circle' : 'circle'"
                      size="is-medium"
                      type="is-primary is-light"
                      @click="publish(dataset, !dataset.params.published)"
                      style="height: 1.5em; background-color: transparent; font-size: 1.45rem"
                    />
                  </b-field>
                </td>
                <td>
                  <b-field>
                    <b-button
                      icon-right="times-circle"
                      size="is-medium"
                      type="is-danger is-light"
                      @click="remove(dataset, dataset.params.published)"
                      style="height: 1.5em; background-color: transparent; font-size: 1.45rem"
                    />
                  </b-field>
                </td>
              </tr>
            </table>
          </div>
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
import { LOCAL_STORAGE_LANG } from "@/services/Fetcher";
import DropDownMenu from "@/components/common/DropDownMenu";
import moment from "moment";
import {HttpStatusCodes} from "@/utils/HttpUtils";

@Component({
  components: {DropDownMenu, CollapsibleTree, PageView, SubMenu},
})
export default class DataTypesRepositoryView extends Vue {
  @Prop() applicationName;
  @Prop() dataTypeId;
  @Prop() applicationConfiguration;

  referenceService = ReferenceService.INSTANCE;
  references = {};
  fileService = FileService.INSTANCE;
  applicationService = ApplicationService.INSTANCE;
  alertService = AlertService.INSTANCE;
  dataService = DataService.INSTANCE;
  errorsService = ErrorsService.INSTANCE;
  internationalisationService = InternationalisationService.INSTANCE;
  getAuthorizationScopePath = this.internationalisationService.getAuthorizationScopePath;
  getDataTypeDisplay = this.internationalisationService.getDataTypeDisplay;
  subMenuPaths = [];
  application = new ApplicationResult();
  applications = [];
  configuration = {};
  authorizations = [];
  authReferences = {};
  selected = null;
  requiredAuthorizationsObject = null;
  datasets = {};
  file = null;
  startDate = null;
  endDate = null;
  comment = "";
  currentDataset = null;
  repository = {};
  errorsMessages = [];

  mounted() {
    this.$on("authorizationChanged", this.updateDatasets);
    this.$on("uploaded", this.updateDatasets);
    this.$on("published", this.updateDatasets);
    this.$on("deleted", this.updateDatasets);
    this.$on("listFilesUploaded", this.getDatasetMap);
    this.$on("parseAuth", this.parseAuth);
  }

  changeCss(id) {
    if (document.getElementById(id).querySelector(".tooltip-content").style.display === "block")
      document.getElementById(id).querySelector(".tooltip-content").style.display = "none";
    else document.getElementById(id).querySelector(".tooltip-content").style.display = "block";
  }

  created() {
    const prevPath = `/applications/${this.applicationName}/dataTypes`;
    this.subMenuPaths = [
      new SubMenuPath(
        this.dataTypeId.toLowerCase(),
        () => {
        },
        () => this.$router.push(prevPath)
      ),
    ];

    this.init();
  }

  changeFile(file) {
    console.log("repository", this.repository);
    let pattern = this.repository.filePattern;
    let split = [];
    if (pattern && pattern.length) {
      let matches = new RegExp(pattern).exec(file.name);
      if (matches) {
        if (this.repository.authorizationScope) {
          for (const authorizationScopeKey in this.repository.authorizationScope) {
            let authorizationScope =
              matches[this.repository.authorizationScope[authorizationScopeKey]];

            var currentNode = this.authReferences[authorizationScopeKey];

            // on teste une naturalKey
            split = authorizationScope.split("__");
            for (const key in split) {
              if (currentNode.referenceValues) {
                let path = currentNode.currentPath + "__" + split[key];
                currentNode = currentNode.referenceValues[path];
              } else {
                currentNode = currentNode[split[key]];
              }
              if (currentNode) {
                this.selectAuthorization(authorizationScopeKey, currentNode);
              }
              if (!currentNode || currentNode.isLeaf) break;
            }
            if (!currentNode) {
              //on teste une hierarchicalKey
              currentNode = this.authReferences[authorizationScopeKey];
              split = authorizationScope.split(".");
              for (const key in split) {
                if (currentNode.referenceValues) {
                  currentNode = currentNode.referenceValues[split[key]];
                } else {
                  currentNode = currentNode[split[key]];
                }
                if (currentNode) {
                  this.selectAuthorization(authorizationScopeKey, currentNode);
                }
                if (!currentNode || currentNode.isLeaf) break;
              }
            }
          }
        }
        if (
          this.repository.startDate &&
          this.repository.startDate.token &&
          matches[this.repository.startDate.token]
        ) {
          this.startDate = moment(matches[this.repository.startDate.token], "DD-MM-YYYY").toDate();
        }
        if (
          this.repository.endDate &&
          this.repository.endDate.token &&
          matches[this.repository.endDate.token]
        ) {
          this.endDate = moment(matches[this.repository.endDate.token], "DD-MM-YYYY").toDate();
        }
      }
    }
  }

  getRefType(variable, component) {
    let refType = "";
    let compositeReferenceDependance = false;

    try {
      let variable = this.configuration.data[variable];
      refType = ({...variable.components, ...variable.compositeComponents})[component].checker.params.refType;
      compositeReferenceDependance = false;
      for (const ref in this.application.references) {
        if (this.application.references[ref].children.length) {
          compositeReferenceDependance = this.application.references[ref].children;
        }
      }
      return {
        refType,
        compositeReferenceDependance,
      };
    } catch (e) {
      return {
        refType,
        compositeReferenceDependance,
      };
    }
  }

  async init() {
    try {
      this.applications = await this.applicationService.getApplications();
      this.application = await this.applicationService.getApplication(this.applicationName);
      this.repository = this.application.dataTypes[this.dataTypeId].repository;
      this.application = {
        ...this.application,
        localName: this.internationalisationService.mergeInternationalization(this.application)
          .localName,
        localDatatypeName: this.internationalisationService.localeDataTypeIdName(
          this.application,
          this.application.dataTypes[this.dataTypeId]
        ),
      };
      this.configuration = this.applications
        .filter((a) => a.name === this.applicationName)
        .map((a) => a.configuration.dataTypes[this.dataTypeId])[0];
      console.log("refType", this.getRefType("site", "chemin"));
      this.authorizations = this.configuration.authorization.authorizationScopes;
      let requiredAuthorizations = Object.keys(this.authorizations).reduce((acc, auth) => {
        acc[auth] = null;
        return acc;
      }, {});
      this.selected = new BinaryFileDataset({
        datatype: this.dataTypeId,
        requiredAuthorizations,
        from: "",
        to: "",
        comment: "",
      });
      this.requiredAuthorizationsObject = Object.keys(this.authorizations).reduce((acc, auth) => {
        acc[auth] = null;
        return acc;
      }, {});
      let ret = {};
      for (let auth in this.authorizations) {
        let vc = this.authorizations[auth];
        let variables = this.configuration.data[vc.variable];
        var reference =
            ({...variables.components, ...variables.computedComponents})[vc.component].checker.params.refType;
        let ref = await this.getOrLoadReferences(reference);
        ret[auth] = ref;
      }

      let refs = Object.values(ret)
        .reduce(
          (acc, k) => [
            ...acc,
            ...k.referenceValues.reduce(
              (a, b) => [...a, ...b.hierarchicalReference.split(".")],
              acc
            ),
          ],
          []
        )
        .reduce((a, b) => {
          if (a.indexOf(b) < 0) {
            a.push(b);
          }
          return a;
        }, []);
      for (const refsKey in refs) {
        await this.getOrLoadReferences(refs[refsKey]);
      }
      for (const [key, value] of Object.entries(ret)) {
        let partition = await this.partitionReferencesValues(value.referenceValues);
        ret[key] = partition;
      }
      this.authReferences = ret;
      //this.$emit("parseAuth", ret);
    } catch (error) {
      this.alertService.toastServerError();
    }
  }

  async getOrLoadReferences(reference) {
    if (this.references[reference]) {
      return this.references[reference];
    }
    let ref = await this.referenceService.getReferenceValues(this.applicationName, reference);
    this.references[reference] = ref;
    // eslint-disable-next-line no-self-assign
    this.references = this.references;
    return ref;
  }

  UTCToString(utcString) {
    return utcString && utcString.replace(/(\d{4})-(\d{2})-(\d{2}).*/, "$3/$2/$1");
  }

  parseDate(date) {
    return moment(date, "DD/MM/YYYY").toDate();
  }

  formatDate(date) {
    return moment(date).format("DD/MM/YYYY HH:mm");
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
          this.selected.requiredAuthorizations,
          /(.{10})T(.{8}).*/
            .exec(new Date(this.startDate).toISOString())
            .filter((a, i) => i !== 0)
            .join(" "),
          /(.{10})T(.{8}).*/
            .exec(new Date(this.endDate).toISOString())
            .filter((a, i) => i !== 0)
            .join(" "),
          this.comment
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
    this.errorsMessages = [];
    dataset.params.published = pusblished;
    let requiredAuthorizations = dataset.params.binaryFiledataset.requiredAuthorizations;
    requiredAuthorizations = Object.keys(requiredAuthorizations).reduce(function (acc, key) {
      acc[key] = acc[key] ? acc[key].sql : "";
      return acc;
    }, requiredAuthorizations);
    console.log("requiredAuthorizations", requiredAuthorizations);
    dataset.params.binaryFiledataset.requiredAuthorizations = requiredAuthorizations;
    console.log("binaryFiledataset", dataset.params.binaryFiledataset);
    var fileOrId = new FileOrUUID(dataset.id, dataset.params.binaryFiledataset, pusblished);
    try {
      var uuid = await this.dataService.addData(
        this.applicationName,
        this.dataTypeId,
        null,
        fileOrId
      );
      this.$emit("published", uuid.fileId);
      this.alertService.toastSuccess(this.$t("alert.data-updated"));
    } catch (error) {
      this.checkMessageErrors(error);
    }
  }

  checkMessageErrors(error) {
    let message = [];
    if (error.httpResponseCode === HttpStatusCodes.BAD_REQUEST) {
      if (error.content != null) {
        this.errorsList = [];
        error.content.then((value) => {
          for (let i = 0; i < value.length; i++) {
            if (message.length > 0) {
              if (JSON.stringify(value[i]) !== JSON.stringify(value[i - message.length])) {
                console.log(message)
                this.errorsList.push(value[i]);
              }
              for (let j = 0; j < message.length; j++) {
                if (!message.includes(value[i].validationCheckResult.message)) {
                  message.push(value[i].validationCheckResult.message);
                }
              }
            } else {
              message.push(value[i].validationCheckResult.message);
              this.errorsList.push(value[i]);
            }
          }
          console.log(this.errorsList)
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

  selectAuthorization(key, event) {
    console.log("key", key, "event", event);
    this.selected.requiredAuthorizations[key] = event.referenceValues.hierarchicalKey;
    this.requiredAuthorizationsObject[key] = event.completeLocalName;
    this.datasets = this.currentDataset = null;
    this.$refs?.[key]?.[0].toggle();
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
    if (this.selected && this.selected.requiredAuthorizations) {
      return (
        this.selected && Object.values(this.selected.requiredAuthorizations).every((v) => v?.length)
      );
    }
    return false;
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
      this.currentDataset = this.datasets?.[periode]?.datasets;
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

  async partitionReferencesValues(referencesValues, currentPath, currentCompleteLocalName) {
    let returnValues = {};
    for (const referenceValue of referencesValues) {
      var previousKeySplit = currentPath ? currentPath.split(".") : [];
      var keys = referenceValue.hierarchicalKey.split(".");
      var references = referenceValue.hierarchicalReference.split(".");
      if (previousKeySplit.length === keys.length) {
        continue;
      }
      for (let i = 0; i < previousKeySplit.length; i++) {
        keys.shift();
        references.shift();
      }
      var key = keys.shift();
      let newCurrentPath = (currentPath ? currentPath + "." : "") + key;
      var reference = references.shift();
      let refValues = await this.getOrLoadReferences(reference);
      this.internationalisationService.getUserPrefLocale();
      let lang = localStorage.getItem(LOCAL_STORAGE_LANG);
      let localName = refValues.referenceValues.find((r) => r.naturalKey == key);
      if (localName?.values?.["__display_" + lang]) {
        localName = localName?.values?.["__display_" + lang];
      }else{
        localName = localName?.naturalKey
      }
      if (!localName) {
        localName = key;
      }
      var completeLocalName =
        typeof currentCompleteLocalName === "undefined" ? "" : currentCompleteLocalName;
      completeLocalName = completeLocalName + (completeLocalName === "" ? "" : ",") + localName;
      let authPartition = returnValues[key] || {
        key,
        reference,
        referenceValues: [],
        localName,
        isLeaf: false,
        currentPath: newCurrentPath,
        completeLocalName,
      };
      authPartition.referenceValues.push(referenceValue);
      returnValues[key] = authPartition;
    }
    for (const returnValuesKey in returnValues) {
      var auth = returnValues[returnValuesKey];
      let referenceValueLeaf = auth.referenceValues?.[0];
      if (
        auth.referenceValues.length <= 1 &&
        referenceValueLeaf.hierarchicalKey === auth.currentPath
      ) {
        returnValues[returnValuesKey] = {
          ...auth,
          isLeaf: true,
          referenceValues: referenceValueLeaf,
        };
      } else {
        var r = await this.partitionReferencesValues(
          auth.referenceValues,
          auth.currentPath,
          auth.completeLocalName
        );
        returnValues[returnValuesKey] = {
          ...auth,
          isLeaf: false,
          referenceValues: r,
        };
      }
    }
    return returnValues;
  }

  deployMenu(option) {
    console.log(this.partitionReferencesValues(option.referenceValues, option.currentPath));
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

.dropdown-content {
  margin-left: 10px;
  margin-right: -30px;
}

table.datasetsPanel {
  width: 50%;
}

table.datasetsPanel,
table.datasetsPanel th,
table.datasetsPanel td {
  border-collapse: collapse;
  text-align: center;
}

.numberData tr:hover td {
  background-color: $primary;
  color: white;
}

caption {
  color: $dark;
  font-weight: bold;
  font-size: 20px;
  margin-bottom: 15px;
}

.b-tooltip {
  .tooltip-trigger a {
  }

  .tooltip-content {
  }
}
</style>