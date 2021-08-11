<template>
  <PageView>
    <h1 class="title main-title">{{ $t("titles.applications-page") }}</h1>

    <div class="columns columnPrincipale">
      <div class="column is-3">
        <section>
          <div v-if="canCreateApplication" class="card is-clickable">
            <div
              class="card-header createApplication"
              role="button"
              style="margin-bottom: 50px"
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
                    false-value="false"
                    true-value="true"
                    field="name"
                    class="column"
                    @input="recalculate"
                  >{{ $t("applications.trierRecent") }}</b-checkbox>
                </b-field>
              </div>
              <div class="content">
                <b-field class="columns">
                  <b-checkbox
                    id="checkboxTrieA_z"
                    v-model="checkboxTrieA_z"
                    false-value="false"
                    true-value="true"
                    field="name"
                    class="column"
                    @input="recalculate"
                  >{{ $t("applications.trierA_z") }}</b-checkbox>
                  <b-checkbox
                    id="checkboxTrieZ_a"
                    v-model="checkboxTrieZ_a"
                    false-value="false"
                    true-value="true"
                    field="name"
                    class="column"
                    @input="recalculate"
                    >{{ $t("applications.trierZ_a") }}</b-checkbox
                  >
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
                <p v-if="selected != null" class="content">
                  <b>{{ $t("applications.selected") }}</b>
                  {{ selected.name }}
                </p>
                <b-field>
                  {{ $t("applications.name") }}
                  <b-autocomplete
                    v-model="filterName"
                    :data="selectedApplications"
                    field="name"
                    placeholder="olac"
                    @click.native="recalculate"
                    @keyup.native="recalculate"
                  >
                  </b-autocomplete>
                </b-field>
<!--                <hr />
                <b-field>
                  {{ $t("applications.creation-date") }}
                  <b-datepicker v-model="filterDateDebut" :locale="localLang" editable icon="calendar"> </b-datepicker>
                </b-field>
                <b-field>
                  {{ $t("applications.creation-date") }}
                  <b-datepicker v-model="filterDateFin" :locale="localLang" editable icon="calendar"> </b-datepicker>
                </b-field>-->
              </div>
            </div>
            <footer class="card-footer">
              <a class="card-footer-item">Recherche par date</a>
            </footer>
          </div>
        </section>
      </div>
      <div class="column">
        <div class="columns is-9">
          <div v-for="(application, index) in selectedApplications" v-bind:key="application.name">
            <div class="column">
              <div
                v-if="index >= (current - 1) * perPage && index < current * perPage"
                class="applicationCard card"
              >
                <div class="card-header">
                  <div class="title card-header-title">
                    <p field="name">{{ application.name }}</p>
                  </div>
                  <b-button
                    class="btnModal"
                    icon-left="external-link-square-alt"
                    size="is-medium"
                    type="is-primary"
                    @click="showModal(application.name)"
                  />
                  <b-modal
                    v-show="isSelectedName == application.name"
                    :id="application.name"
                    v-model="isCardModalActive"
                  >
                    <div class="card">
                      <div class="card-header">
                        <div class="title card-header-title">
                          <p field="name">{{ application.name }}</p>
                        </div>
                      </div>
                      <div class="card-content">
                        <div class="content">
                          <p>{{ application.referenceType }}, {{ application.dataType }}</p>
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
                      </div>
                    </div>
                  </b-modal>
                </div>
                <div class="card-content">
                  <div class="content">
                    <p field="creationDate">
                      {{ new Date(application.creationDate).toLocaleString(localLang) }}
                    </p>
                  </div>
                </div>
                <div class="card-footer">
                  <div class="card-footer-item">
                    <b-button
                      icon-left="drafting-compass"
                      @click="displayReferencesManagement(application)"
                    >
                      {{ $t("applications.references") }}
                    </b-button>
                  </div>
                  <div class="card-footer-item">
                    <b-button icon-left="poll" @click="displayDataSetManagement(application)">
                      {{ $t("applications.dataset") }}
                    </b-button>
                  </div>
                </div>
              </div>
            </div>
          </div>
        </div>
        <hr />
        <b-pagination
          :current.sync="current"
          :per-page="perPage"
          :range-after="2"
          :range-before="2"
          :rounded="true"
          :total="applications.length"
        >
        </b-pagination>
      </div>
    </div>
  </PageView>
</template>

<script>
import { ApplicationService } from "@/services/rest/ApplicationService";
import { Component, Vue } from "vue-property-decorator";
import PageView from "@/views/common/PageView.vue";
import { LoginService } from "@/services/rest/LoginService";

@Component({
  components: { PageView },
})
export default class ApplicationsView extends Vue {
  applicationService = ApplicationService.INSTANCE;

  applications = [];
  canCreateApplication =
    LoginService.INSTANCE.getAuthenticatedUser().authorizedForApplicationCreation;
  // show modal and cards
  isSelectedName = "";
  isCardModalActive = false;
  localLang = localStorage.getItem("lang");
  // pagination variable
  current = 1;
  perPage = 12;
  selectedApplications = [];
  // filtre variable
  filterName = "";
  selected = null;
  /*filterDateDebut = "";
  filterDateFin = "";*/
  checkboxTrieA_z = "false";
  checkboxTrieZ_a = "false";
  checkboxDate = "true";

  copyOfApplications(application) {
    return [...application];
  }
  recalculate() {
    this.selectedApplications = this.copyOfApplications(this.applications);
    this.selectedApplications = this.selectedApplications.filter(
      (a) => a.name.toString().toLowerCase().indexOf(this.filterName.toLowerCase()) >= 0
    );

    if (this.checkboxDate == "true") this.selectedApplications.sort((a, b) => b.creationDate - a.creationDate);
    else
      this.selectedApplications.sort((a, b) => b.creationDate - a.creationDate).reverse();

    if (this.checkboxTrieZ_a == "true" || this.checkboxTrieA_z == "true") {
      if (this.checkboxTrieA_z == "true" && document.activeElement.parentElement == document.getElementById("checkboxTrieZ_a")) {
        this.checkboxTrieA_z = "false";
        this.selectedApplications.sort((a, b) => a.name.localeCompare(b.name));
      }
      else if (this.checkboxTrieZ_a == "true" && document.activeElement.parentElement == document.getElementById("checkboxTrieA_z")) {
        this.selectedApplications.sort((a, b) => a.name.localeCompare(b.name)).reverse();
        this.checkboxTrieZ_a = "false";
      }
    }else {
      this.checkboxTrieA_z = "false";
      this.checkboxTrieZ_a = "false";
    }
  }

  async created() {
    await this.init();
  }

  async init() {
    this.applications = await this.applicationService.getApplications();
    this.selectedApplications = this.applications;
    if (this.checkboxDate == "true") this.selectedApplications.sort((a, b) => b.creationDate - a.creationDate);
  }

  createApplication() {
    this.$router.push("/applicationCreation");
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
    margin-left: 100px;
    margin-top: 50px;
  }
}

.column {
  display: grid;

  .card {
    &.applicationCard {
      width: 300px;

      .card-footer {
        border: none;
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
</style>
