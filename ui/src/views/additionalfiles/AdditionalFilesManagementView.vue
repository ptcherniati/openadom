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
            <div class="columns">
                <b-select
                        :placeholder="$t('additionalFiles.menu')"
                        :value="additionalFileName"
                        style="margin: 10px"
                        @input="changeFileType"
                >
                    <option v-for="(option, id) in additionalFileNames" :key="id" :value="option"
                    >
                        {{
                        internationalisationService.getLocaleforPath(
                            application,
                            "additionalFiles." + option + ".internationalizationName"
                        )
                        }}
                    </option>
                </b-select
                >
                <div v-if="additionalFileName" class="column is-offset-9 is-2">
                    <b-button icon-left="plus" type="is-primary is-right" @click="addAdditionalFile">
                        {{
                        $t("additionalFilesManagement.addAdditionalFile", {
                          localName: internationalisationService.getLocaleforPath(
                              application,
                              "additionalFiles." + additionalFileName + ".internationalizationName"
                          ),
                        })
                        }}
                    </b-button>
                </div>
            </div>
            <div class="columns">
                <caption v-if="!additionalFileName">
                    <p>{{ $t("additionalFilesManagement.selectAdditionalFilesType") }}</p>
                </caption>
            </div>
            <div v-for="(additionalFile, i) in additionalFiles" :key="i">
                <div v-if="additionalFile.fileType === additionalFileName">
                    <CollapsibleTree
                            v-if="true || isVisibleRequest(additionalFile.setted)"
                            :id="additionalFile.id"
                            :application-title="$t('titles.references-page')"
                            :buttons="getButtons(additionalFile.user)"
                            :level="0"
                            :line-count="12"
                            :on-click-label-cb="() => ''"
                            :option="additionalFile"
                            :repository-redirect="(label) => manageRequest(label)"
                            class="liste"
                    >
                        <template v-slot:secondaryMenu> &nbsp;</template>
                        <template v-slot:upload> &nbsp;</template>
                        <template v-slot:label="{ option }">
                            <b-icon :icon="'check'" :type="'is-primary'" class="column">
                                {{ $t("dataTypeAuthorizations.scopes.close") }}
                            </b-icon>
                            <div class="column">{{ option.fileName }}</div>
                            <div class="column">{{ humanFileSize(option.size) }}</div>
                            <div class="column">
                                <b-tooltip :label="option.comment" multilined position="is-top" size="is-large">
                                    <a>{{ $t("dataTypesRepository.comment") }}</a>
                                </b-tooltip>
                            </div>
                        </template>
                        <template v-slot:synthesisDetail="{ option }">
                            <div class="column is-one-fifth">
                                {{ option.updateDate && getDate(option.updateDate) }}
                            </div>
                            <div class="column is-one-fifth">
                                {{ users.find((user) => user.id === option.user).label }}
                            </div>
                        </template>
                        <template v-slot:default="{ option, displayChildren }">
                            <div v-if="displayChildren" class="rows">
                                <div style="padding-left: 1rem">
                                    <p style="text-decoration: underline; font-size: large">
                                        {{ $t("additionalFilesManagement.recap") }}{{ $t("ponctuation.colon") }}
                                    </p>
                                </div>
                                <div
                                        v-for="(value, name) in option.additionalBinaryFileForm"
                                        :key="name"
                                        class="row"
                                >
                                    <div class="columns">
                                        <div v-if="value !== ''" class="column is-offset-1 is-primary">
                                            <p>
                                                {{
                                                internationalisationService.getLocaleforPath(
                                                    application,
                                                    "rightsRequest.format." + name,
                                                    name
                                                )
                                                }}{{ $t("ponctuation.colon") }} {{ value }}
                                            </p>
                                        </div>
                                    </div>
                                </div>
                            </div>
                        </template>
                    </CollapsibleTree>
                </div>
                <div v-else class="columns">
                    <caption class="column is-one-quarter">
                        <p style="color: rgb(255, 170, 0)">
                            {{ $t("additionalFilesManagement.emptyAdditionalFilesList") }}
                        </p>
                    </caption>
                </div>
            </div>
        </div>
    </PageView>
</template>

<script>
import moment from "moment";
import SubMenu, {SubMenuPath} from "@/components/common/SubMenu.vue";
import {AlertService} from "@/services/AlertService";
import {ApplicationService} from "@/services/rest/ApplicationService";
import {AdditionalFileService} from "@/services/rest/AdditionalFileService";
import {InternationalisationService} from "@/services/InternationalisationService";
import {Component, Prop, Vue} from "vue-property-decorator";
import PageView from "../common/PageView.vue";
import {ApplicationResult} from "@/model/ApplicationResult";
import {Button} from "@/model/Button";
import CollapsibleTree from "@/components/common/CollapsibleTree.vue";
import {AdditionalFilesInfos} from "@/model/additionalFiles/AdditionalFilesInfos";

@Component({
    components: {PageView, SubMenu, CollapsibleTree},
})
export default class AdditionalFilesManagementView extends Vue {
    @Prop() applicationName;
    @Prop() additionalFileName;
    vid = null;
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
    getReadableFileSizeStringfunction;
    currentUserId = JSON.parse(localStorage.authenticatedUser).id;

    updated() {
        if (document.readyState === "complete") {
            let list = document.getElementsByClassName("CollapsibleTree-header-infos column is-narrow");
            for (let i = 0; i < list.length; i++) {
                list[i].setAttribute("class", "CollapsibleTree-header-infos column is-two-thirds");
            }
        }
    }

    created() {
        this.init();
        this.subMenuPaths = [
            new SubMenuPath(
                this.$t("additionalFilesManagement.additionalFilesManagement").toLowerCase(),
                () => {
                },
                () => this.$router.push("/applications")
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
        if (this.additionalFileName) {
            this.loadAdditionalFiles()
        }
    }

    isVisibleRequest(setted) {
        if (this.filterState === 0) {
            return !setted;
        } else if (this.filterState === 1) {
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

    addAdditionalFile() {
        if (this.additionalFileName) {
            this.$router.push(
                `/applications/${this.applicationName}/additionalFiles/${this.additionalFileName}/new`
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

    async deleteAdditionalFile(event) {
        var fileTodelete = (this.additionalFiles
            .find(file => file.id == event) || {});
        this.$buefy.dialog.confirm({
            type: "is-danger",
            title: this.$t('additionalFiles.alert.alertDeleteTitle'),
            message: this.$t('additionalFiles.alert.alertDelete', fileTodelete),
            confirmText: this.$t('additionalFiles.alert.alertDeleteConfirm'),
            cancelText: this.$t('additionalFiles.alert.alertDeleteCancel'),
            onConfirm: () => {
                this.$buefy.toast.open(this.$t('additionalFiles.alert.confirmDelete', fileTodelete));
                this.doDeleteAdditionalFile(fileTodelete);
            },
            hasIcon: true,
        });
    }

    async doDeleteAdditionalFile(fileTodelete) {
        let param = new AdditionalFilesInfos([fileTodelete.id]);
        let uuid = await this.additionalFileService.deleteAdditionalFile(this.applicationName, param);
        uuid = uuid ? uuid.split(',') : []
        this.additionalFiles = this.additionalFiles
            .filter(file => !uuid.includes(file.id))
    }

    manageRequest(id) {
        this.$router.push(`/applications/${this.applicationName}/authorizationsRequest/${id}`);
    }

    changeFileType(event) {
        this.$router.push(`/applications/${this.applicationName}/additionalFiles/${event}`);
        this.loadAdditionalFiles();
    }

    async loadAdditionalFiles() {
        let additionalFiles = await this.additionalFileService.getAdditionalFiles(
            this.applicationName,
            this.additionalFileName,
            {}
        );
        let users1 = additionalFiles.users || [];
        users1.shift();
        this.users = users1;
        for (const request of additionalFiles.additionalBinaryFiles) {
            request.children = [{}];
            request.label = request.id;
        }
        this.additionalFiles = additionalFiles.additionalBinaryFiles;
        //this.changeValueAttribute();
    }

    humanFileSize(bytes, si = false, dp = 1) {
        const thresh = si ? 1000 : 1024;

        if (Math.abs(bytes) < thresh) {
            return bytes + " B";
        }

        const units = si
            ? ["KiB", "MiB", "GiB", "TiB", "PiB", "EiB", "ZiB", "YiB"]
            : ["kB", "MB", "GB", "TB", "PB", "EB", "ZB", "YB"];
        let u = -1;
        const r = 10 ** dp;

        do {
            bytes /= thresh;
            ++u;
        } while (Math.round(Math.abs(bytes) * r) / r >= thresh && u < units.length - 1);

        return bytes.toFixed(dp) + " " + units[u];
    }

    getButtons(fileId) {
        let buttons = [
            new Button(
                this.$t("additionalFiles.buttons.consult"),
                "eye",
                (label) => this.consultAdditionalFile(label),
                "is-dark"
            ),
            new Button(this.$t("additionalFiles.buttons.download"), "download", (label) =>
                this.downloadAdditionalFile(label)
            ),
        ]
        if (this.application.isAdministrator || fileId == this.currentUserId) {
            buttons.push(
                new Button(this.$t("additionalFiles.buttons.delete"), "times", (label) =>
                        this.deleteAdditionalFile(label),
                    "is-danger"
                )
            );
        }
        return buttons;
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