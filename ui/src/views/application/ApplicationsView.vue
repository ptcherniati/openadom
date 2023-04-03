<template>
  <PageView>
    <h1 class="title main-title">{{ $t("titles.applications-page") }}</h1>

    <div class="columns columnPrincipale">
      <div class="column is-3-widescreen is-12-desktop">
        <section>
          <div v-if="canCreateApplication" class="card is-clickable">
            <div
              class="card-header createApplication"
              role="button"
              style="margin-bottom: 50px"
              tabindex="0"
              @click="createApplication"
            >
              <a class="card-header-icon createApplication">
                <b-icon icon="plus"></b-icon>
              </a>
              <p class="card-header-title createApplication">
                {{ $t("applications.create") }}
              </p>
            </div>
          </div>
          <div class="card">
            <div class="card-header">
              <p class="card-header-title">
                {{ $t("applications.trier") }}
              </p>
            </div>
            <div class="card-content">
              <div class="content">
                <b-field class="columns">
                  <b-checkbox
                    v-model="checkboxDate"
                    class="column"
                    false-value="false"
                    field="name"
                    true-value="true"
                    @input="recalculate"
                    >{{ $t("applications.trierRecent") }}
                  </b-checkbox>
                </b-field>
              </div>
              <div class="content">
                <b-field class="columns">
                  <b-checkbox
                    id="checkboxTrieA_z"
                    v-model="checkboxTrieA_z"
                    class="column"
                    false-value="false"
                    field="name"
                    true-value="true"
                    @input="recalculate"
                    >{{ $t("applications.trierA_z") }}
                  </b-checkbox>
                  <b-checkbox
                    id="checkboxTrieZ_a"
                    v-model="checkboxTrieZ_a"
                    class="column"
                    false-value="false"
                    field="name"
                    true-value="true"
                    @input="recalculate"
                    >{{ $t("applications.trierZ_a") }}
                  </b-checkbox>
                </b-field>
              </div>
            </div>
          </div>
          <div class="card">
            <div class="card-header">
              <p class="card-header-title">{{ $t("applications.filter") }}</p>
            </div>
            <div class="card-content">
              <div class="content">
                <b-field>
                  {{ $t("applications.name") }}
                  <b-autocomplete
                    v-model="filterName"
                    :data="selectedApplications"
                    field="localName"
                    placeholder="olac"
                    @click.native="recalculate"
                    @keyup.native="recalculate"
                  >
                  </b-autocomplete>
                </b-field>
              </div>
            </div>
          </div>
        </section>
      </div>
      <div class="column is-9-widescreen is-12-desktop">
        <caption v-if="loading" class="columns">
          <div class="column loader-wrapper">
            <div class="loader is-loading"></div>
          </div>
        </caption>
        <div class="columns">
          <div
            v-for="(application, index) in selectedApplications"
            :key="application.name"
            style="margin-left: 30px"
          >
            <div class="column">
              <div
                v-if="index >= (current - 1) * perPage && index < current * perPage"
                class="applicationCard card"
                style="padding-bottom: 10px"
              >
                <div class="card-header">
                  <div class="title card-header-title">
                    <p field="name" style="font-size: 1.5rem">{{ application.localName }}</p>
                  </div>
                  <b-button
                    class="btnModal"
                    icon-left="ellipsis-h"
                    size="is-medium"
                    type="is-primary"
                    @click="showModal(application.name)"
                  />
                  <b-modal
                    v-show="isSelectedName === application.name"
                    :id="application.name"
                    v-model="isCardModalActive"
                  >
                    <div class="card">
                      <div class="card-header">
                        <div class="title card-header-title">
                          <p field="name">{{ application.localName }}</p>
                        </div>
                      </div>
                      <div class="card-content">
                        <div class="content">
                          <p
                            v-html="
                              $t('applications.version', {
                                applicationName: application.localName,
                                version: application.version,
                              })
                            "
                          />
                          <p class="comment">
                            <span
                              :class="application.comment ? 'has-text-primary' : 'has-text-warning'"
                            >
                              {{
                                application.comment
                                  ? $t("applications.comment")
                                  : $t("applications.no-comment")
                              }}
                            </span>
                            <span>{{ application.comment }}</span>
                          </p>
                        </div>
                      </div>
                      <div class="card-footer">
                        <div class="card-footer-item">
                          <b-button
                            icon-left="drafting-compass"
                            @click="displayReferencesManagement(application)"
                            >{{ $t("applications.references") }}
                          </b-button>
                        </div>
                        <div class="card-footer-item">
                          <b-button icon-left="poll" @click="displayDataSetManagement(application)"
                            >{{ $t("applications.dataset") }}
                          </b-button>
                        </div>
                        <div v-if="canCreateApplication" class="card-footer-item">
                          <b-button
                            icon-left="pen-square"
                            type="is-warning"
                            @click="updateApplication(application.id)"
                          >
                            {{ $t("applications.change") }}
                          </b-button>
                        </div>
                      </div>
                      <div class="card-footer">
                        <div v-if="canCreateApplication" class="card-footer-item">
                          <b-button
                            icon-left="download"
                            type="is-primary"
                            @click="downloadYamlApplication(application)"
                          >
                            {{ $t("referencesManagement.download") }}
                          </b-button>
                        </div>
                        <div v-if="!canCreateApplication" class="card-footer-item">
                          <b-button
                            icon-left="users-cog"
                            type="is-primary"
                            @click="showRequestRights(application)"
                          >
                            {{ $t("dataTypeAuthorizations.showRequests") }}
                          </b-button>
                        </div>
                        <div v-else class="card-footer-item">
                          <b-button
                            icon-left="users-cog"
                            type="is-primary"
                            @click="requestRights(application)"
                          >
                            {{ $t("dataTypeAuthorizations.request") }}
                          </b-button>
                        </div>
                      </div>
                    </div>
                  </b-modal>
                </div>
                <div class="card-content" style="padding: 12px">
                  <div class="content">
                    <p field="creationDate">
                      {{ new Date(application.creationDate).toLocaleString(localLang) }}
                    </p>
                  </div>
                </div>
                <div class="card-footer">
                  <div
                    v-if="application.referenceType && application.referenceType.length !== 0"
                    class="card-footer-item"
                  >
                    <b-button
                      icon-left="drafting-compass"
                      @click="displayReferencesManagement(application)"
                    >
                      {{ $t("applications.references") }}
                    </b-button>
                  </div>
                  <div
                    v-if="application.dataType && application.dataType.length !== 0"
                    class="card-footer-item"
                  >
                    <b-button icon-left="poll" @click="displayDataSetManagement(application)">
                      {{ $t("applications.dataset") }}
                    </b-button>
                  </div>
                </div>
                <div class="card-footer">
                  <div
                    v-if="application.additionalFile && application.additionalFile.length !== 0"
                    class="card-footer-item"
                  >
                    <b-button
                      icon-left="file"
                      @click="displayAdditionalFilesManagement(application)"
                    >
                      {{ $t("applications.additionalFile") }}
                    </b-button>
                  </div>
                </div>
              </div>
            </div>
          </div>
        </div>
        <hr />
        <b-pagination
          v-if="perPage <= applications.length"
          :aria-current-label="$t('menu.aria-curent-page')"
          :aria-label="$t('menu.aria-pagination')"
          :aria-next-label="$t('menu.aria-next-page')"
          :aria-previous-label="$t('menu.aria-previous-page')"
          :current.sync="current"
          :per-page="perPage"
          :range-after="2"
          :range-before="2"
          :rounded="true"
          :total="applications.length"
          order="is-centered"
          role="navigation"
        >
        </b-pagination>
      </div>
    </div>
  </PageView>
</template>

<script>
import { ApplicationService } from "@/services/rest/ApplicationService";
import { InternationalisationService } from "@/services/InternationalisationService";
import { Component, Vue } from "vue-property-decorator";
import PageView from "@/views/common/PageView.vue";
import { LoginService } from "@/services/rest/LoginService";
import { FileService } from "@/services/rest/FileService";

@Component({
  components: { PageView },
})
export default class ApplicationsView extends Vue {
  applicationService = ApplicationService.INSTANCE;
  internationalisationService = InternationalisationService.INSTANCE;

  applications = [];
  canCreateApplication =
    LoginService.INSTANCE.getAuthenticatedUser().authorizedForApplicationCreation;
  fileService = FileService.INSTANCE;
  // show modal and cards
  isSelectedName = "";
  isCardModalActive = false;
  localLang = localStorage.getItem("lang");
  // pagination variable
  current = 1;
  perPage = 8;
  selectedApplications = [];
  // filtre variable
  filterName = "";
  selected = null;
  // tie variable
  checkboxTrieA_z = "false";
  checkboxTrieZ_a = "false";
  checkboxDate = "true";
  loading = false;

  copyOfApplications(application) {
    return [...application];
  }

  recalculate() {
    this.selectedApplications = this.copyOfApplications(this.applications);

    // filter by name
    this.selectedApplications = this.selectedApplications.filter(
      (a) => a.localName.toString().toLowerCase().indexOf(this.filterName.toLowerCase()) >= 0
    );

    // order by date or name
    if (this.checkboxDate === "true")
      this.selectedApplications.sort((a, b) => b.creationDate - a.creationDate);
    else this.selectedApplications.sort((a, b) => b.creationDate - a.creationDate).reverse();
    if (this.checkboxTrieZ_a === "true" || this.checkboxTrieA_z === "true") {
      if (
        this.checkboxTrieA_z === "true" &&
        document.activeElement.parentElement === document.getElementById("checkboxTrieA_z")
      ) {
        this.selectedApplications.sort((a, b) => a.name.localeCompare(b.name));
        this.checkboxTrieZ_a = "false";
      } else if (
        this.checkboxTrieZ_a === "true" &&
        document.activeElement.parentElement === document.getElementById("checkboxTrieZ_a")
      ) {
        this.selectedApplications.sort((a, b) => a.name.localeCompare(b.name)).reverse();
        this.checkboxTrieA_z = "false";
      }
    }
  }

  async downloadYamlApplication(application) {
    await this.fileService.download(application.name, application.configFile);
    return false;
  }

  async requestRights(application) {
    this.$router.push(`/applications/${application.name}/authorizationsRequest`);
    return false;
  }

  async showRequestRights(application) {
    this.$router.push(`/applications/${application.name}/authorizationsRequest/new`);
    return false;
  }

  async created() {
    await this.init();
  }

  async init() {
    this.applications = await this.applicationService.getApplications([
      "DATATYPE",
      "REFERENCETYPE",
    ]);
    this.selectedApplications = this.applications;
    if (this.selectedApplications.length === 0) {
      this.loading = true;
    }
    if (this.checkboxDate === "true")
      this.selectedApplications.sort((a, b) => b.creationDate - a.creationDate);
  }

  createApplication() {
    this.$router.push("/applicationCreation");
  }

  updateApplication() {
    this.$router.push(`/applicationCreation`);
  }

  displayReferencesManagement(application) {
    if (!application) {
      return;
    }
    this.$router.push("/applications/" + application.name + "/references");
  }

  displayDataSetManagement(application) {
    if (!application) {
      return;
    }
    this.$router.push("/applications/" + application.name + "/dataTypes");
  }

  displayAdditionalFilesManagement(application) {
    if (!application) {
      return;
    }
    this.$router.push("/applications/" + application.name + "/additionalFiles");
  }

  showModal(name) {
    this.isSelectedName = name;
    this.isCardModalActive = true;
  }
}
</script>

<style lang="scss" scoped>
// card & modal style
.columns {
  flex-wrap: wrap;
  margin: 0px;

  &.columnPrincipale {
    margin-left: 50px;
    margin-top: 50px;
  }
}

.column {
  display: grid;

  .comment {
    display: flex;
    align-items: center;
    align-content: start;
  }

  .card {
    &.applicationCard {
      width: 300px;

      .card-footer {
        border: none;
        .card-footer-item {
          padding-right: 0px;
          .button {
            padding-right: 10px;
            padding-left: 10px;
          }
        }
      }
    }

    .btnModal {
      margin: 5px;
      opacity: 50%;
      color: $primary;
      background-color: transparent;
    }

    .card-footer-item {
      border-right: none;
      padding: 0.5rem;
    }
  }
}

.createApplication {
  background-color: $dark;
  color: white;
}

.card-header-title {
  &.title {
    margin-top: 0;
    text-transform: uppercase;
    margin-bottom: 0px;
  }
}

.control.has-icons-left .icon,
.control.has-icons-right .icon {
  top: 5px;
  left: 5px;
}

.loader-wrapper {
  margin: 50px;
  justify-content: center;

  .loader {
    height: 100px;
    width: 100px;
  }
}
</style>
