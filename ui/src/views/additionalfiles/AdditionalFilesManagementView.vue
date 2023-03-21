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
        additionalFileName
          ? $t("titles.additionalFileWithType", {
              localName: internationalisationService.getLocaleforPath(
                application,
                "additionalFiles." + additionalFileName + ".internationalizationName"
              ),
            })
          : $t("titles.additionalFile")
      }}
    </h1>
    <div>
      <b-select
        v-model="additionalFileName"
        :placeholder="$t('additionalFiles.menu')"
        @input="loadAdditionalFiles"
      >
        <option v-for="(option, id) in additionalFileNames" :key="id" :value="option">
          {{
            internationalisationService.getLocaleforPath(
              application,
              "additionalFiles." + option + ".internationalizationName"
            )
          }}
        </option>
      </b-select>
      <div v-for="(additionalFile, i) in additionalFiles" :key="i">
        <CollapsibleTree
          v-if="true || isVisibleRequest(additionalFile.setted)"
          :id="additionalFile.id"
          :application-title="$t('titles.references-page')"
          :buttons="buttons"
          :level="0"
          :line-count="12"
          :on-click-label-cb="() => ''"
          :option="additionalFile"
          :repository-redirect="(label) => manageRequest(label)"
          class="liste"
        >
          <template v-slot:secondaryMenu> &nbsp; </template>
          <template v-slot:upload> &nbsp; </template>
          <template v-slot:label="{ option }">
            <b-icon :icon="'check'" :type="'is-primary'" class="column is-one-fifth">
              {{ $t("dataTypeAuthorizations.scopes.close") }}
            </b-icon>
            <div class="column is-four-fifth">{{ option.fileName }}</div>
            <div class="column is-four-fifth">{{ humanFileSize(option.size) }}</div>
            <div class="column is-four-fifth">{{ option.comment }}</div>
          </template>
          <template v-slot:synthesisDetail="{ option }">
            <div class="column is-one-fifth">
              {{ option.updateDate && getDate(option.updateDate) }}
            </div>
            <div class="column is-one-fifth">
              {{ users.find((user) => user.id == option.user).label }}
            </div>
          </template>
          <template v-slot:default="{ option, displayChildren }">
            <div v-if="displayChildren" class="rows">
              <div v-for="(value, name) in option.additionalBinaryFileForm" :key="name" class="row">
                <div class="columns">
                  <div class="column is-primary">
                    {{
                      internationalisationService.getLocaleforPath(
                        application,
                        "rightsRequest.format." + name,
                        name
                      )
                    }}
                  </div>
                  <div class="column">{{ value }}</div>
                </div>
              </div>
            </div>
          </template>
        </CollapsibleTree>
      </div>
    </div>
  </PageView>
</template>

<script>
import moment from "moment";
import SubMenu, { SubMenuPath } from "@/components/common/SubMenu.vue";
import { AlertService } from "@/services/AlertService";
import { ApplicationService } from "@/services/rest/ApplicationService";
import { AdditionalFileService } from "@/services/rest/AdditionalFileService";
import { InternationalisationService } from "@/services/InternationalisationService";
import { Component, Prop, Vue } from "vue-property-decorator";
import PageView from "../common/PageView.vue";
import { ApplicationResult } from "@/model/ApplicationResult";
import { Button } from "@/model/Button";
import CollapsibleTree from "@/components/common/CollapsibleTree.vue";
import { AdditionalFilesInfos } from "@/model/additionalFiles/AdditionalFilesInfos";

@Component({
  components: { PageView, SubMenu, CollapsibleTree },
})
export default class AdditionalFilesManagementView extends Vue {
  @Prop() applicationName;
  toList;
  internationalisationService = InternationalisationService.INSTANCE;
  alertService = AlertService.INSTANCE;

  additionalFileService = AdditionalFileService.INSTANCE;
  applicationService = ApplicationService.INSTANCE;
  application = new ApplicationResult();
  // pagination
  offset = 0;
  currentPage = 1;
  perPage = 10;
  isSelectedName = "";
  isSelectedAuthorization = "";
  isCardModalActive = false;
  isCardModalActive2 = false;
  localizations = {};
  additionalFiles = {};
  canManageRights = false;
  users = [];
  buttons = [
    new Button(
      this.$t("referencesManagement.consult"),
      "eye",
      (label) => this.consultAdditionalFile(label),
      "is-dark"
    ),
    new Button(this.$t("referencesManagement.download"), "download", (label) =>
      this.downloadAdditionalFile(label)
    ),
  ];
  filterState = 0;
  filterStates = [
    {
      type: "open",
      label: this.$t("dataTypeAuthorizations.filterScope.open"),
    },
    {
      type: "close",
      label: this.$t("dataTypeAuthorizations.filterScope.close"),
    },
    {
      type: "all",
      label: this.$t("dataTypeAuthorizations.filterScope.all"),
    },
  ];
  additionalFileNames = [];
  additionalFileName = null;
  getReadableFileSizeStringfunction;

  created() {
    this.init();
    this.subMenuPaths = [
      new SubMenuPath(
        this.$t("dataTypesManagement.data-types").toLowerCase(),
        () => this.$router.push(`/applications/${this.applicationName}/dataTypes`),
        () => this.$router.push("/applications")
      ),
      new SubMenuPath(
        this.$t(`dataTypeAuthorizations.sub-menu-data-type-authorizations`, {
          dataType: this.dataTypeId,
        }),
        () => {
          this.$router.push(`/applications/${this.applicationName}/authorizations`);
        },
        () => this.$router.push(`/applications/${this.applicationName}/dataTypes`)
      ),
    ];
  }

  getDate(value) {
    return moment(value).format("DD/MM/YYYY");
  }

  async init() {
    try {
      this.application = await this.applicationService.getApplication(this.applicationName, [
        "CONFIGURATION",
        "DATATYPE",
        "RIGHTSREQUEST",
      ]);
      this.application = {
        ...this.application,
        localName: this.internationalisationService.mergeInternationalization(this.application)
          .localName,
      };
      this.additionalFileNames = Object.keys(this.application.additionalFiles || {});
      this.canManageRights =
        this.application.isAdministrator ||
        Object.values(this.application.authorizationsDatatypesRights || []).some(
          (rights) => rights.ADMIN
        );
    } catch (error) {
      this.alertService.toastServerError;
    }
  }

  isVisibleRequest(setted) {
    if (this.filterState == 0) {
      return !setted;
    } else if (this.filterState == 1) {
      return setted;
    } else {
      return true;
    }
  }

  consultAdditionalFile(label) {
    const ref = this.findAdditionalFileByLabel(label);
    if (ref) {
      this.$router.push(
        `/applications/${this.applicationName}/additionalFiles/${this.additionalFileName}/${ref.id}`
      );
    }
  }

  findAdditionalFileByLabel(label) {
    var ref = this.additionalFiles.find((dt) => dt.label === label);
    return ref;
  }

  async downloadAdditionalFile(event) {
    let param = new AdditionalFilesInfos([event]);
    let path = await this.additionalFileService.getAdditionalFileZip(this.applicationName, param);
    let link = document.createElement("a");
    link.href = path;
    link.download = "additionalFile.zip";
    link.click();
    window.URL.revokeObjectURL(link.href);
    return false;
  }

  manageRequest(id) {
    this.$router.push(`/applications/${this.applicationName}/authorizationsRequest/${id}`);
  }

  async loadAdditionalFiles() {
    let additionalFiles = await this.additionalFileService.getAdditionalFiles(
      this.applicationName,
      this.additionalFileName,
      {}
    );
    console.log(additionalFiles);
    let users1 = additionalFiles.users || [];
    users1.shift();
    this.users = users1;
    for (const request of additionalFiles.additionalBinaryFiles) {
      request.children = [{}];
      request.label = request.id;
    }
    this.additionalFiles = additionalFiles.additionalBinaryFiles;
  }

  humanFileSize(bytes, si = false, dp = 1) {
    const thresh = si ? 1000 : 1024;

    if (Math.abs(bytes) < thresh) {
      return bytes + " B";
    }

    const units = si
      ? ["kB", "MB", "GB", "TB", "PB", "EB", "ZB", "YB"]
      : ["KiB", "MiB", "GiB", "TiB", "PiB", "EiB", "ZiB", "YiB"];
    let u = -1;
    const r = 10 ** dp;

    do {
      bytes /= thresh;
      ++u;
    } while (Math.round(Math.abs(bytes) * r) / r >= thresh && u < units.length - 1);

    return bytes.toFixed(dp) + " " + units[u];
  }
}
</script>
<style lang="scss">
td {
  padding: 6px;

  .columns {
    margin: 0;

    .column.is-half {
      padding: 6px;
    }
  }
}

.listAuthorization {
  border: solid #dbdbdb;
  border-width: 0 0 1px;
  margin: 0 10px 0 10px;
  padding: 15px;
}

.listAuthorization:nth-child(odd) {
  background-color: #f5f5f5;
}
</style>