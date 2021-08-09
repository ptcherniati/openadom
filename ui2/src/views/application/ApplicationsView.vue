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
                  <b-checkbox class="column">{{ $t("applications.trierA_z") }}</b-checkbox>
                  <b-checkbox class="column">{{ $t("applications.trierZ_a") }}</b-checkbox>
                </b-field>
                <b-field class="columns">
                  <b-checkbox class="column">{{ $t("applications.trierRecent") }}</b-checkbox>
                  <b-checkbox class="column">{{ $t("applications.trierAncien") }}</b-checkbox>
                </b-field>
              </div>
            </div>
          </div>
          <div class="card">
            <div class="card-header">
              <p class="card-header-title">Filtrer</p>
            </div>
            <div class="card-content">
              <div class="content">
                <!--
                <b-field>
                  {{ $t("applications.name") }}
                  <b-taginput
                      ref="taginput"
                      v-model="applications.name"
                      :data="applications"
                      :open-on-focus="true"
                      :type="'is-primary'"
                      autocomplete
                      field="name"
                      placeholder="olac"
                      rounded
                      style="width: 100%"
                  >
                    <template slot-scope="props">
                      {{ props.option.name }}
                    </template>
                  </b-taginput>
                </b-field>-->

                <p v-if="selected != null" class="content"><b>Selected:</b> {{ selected.name }}</p>
                <b-field>
                  {{ $t("applications.name") }}
                  <b-autocomplete
                    v-model="name"
                    :data="getFilterByName"
                    field="name"
                    :open-on-focus="true"
                    placeholder="olac"
                    @select="(option) => (selected = option)"
                  >
                  </b-autocomplete>
                </b-field>
                <b-field>
                  {{ $t("applications.creation-date") }}
                  <b-datepicker id="dateFilter" :locale="localLang" editable icon="calendar">
                  </b-datepicker>
                </b-field>
              </div>
            </div>
            <footer class="card-footer">
              <a class="card-footer-item">Confirmer</a>
            </footer>
          </div>
        </section>
      </div>
      <div class="column">
        <div class="columns is-9">
          <div v-for="application in visiblePages(applications)" v-bind:key="application.name">
            <div class="column">
              <div class="applicationCard card">
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
  canCreateApplication = LoginService.INSTANCE.getAuthenticatedUser()
    .authorizedForApplicationCreation;
  isSelectedName = "";
  isCardModalActive = false;
  localLang = localStorage.getItem("lang");
  // pagination variable
  current = 1;
  perPage = 12;
  // filtre variable
  selected = null;
  name = "";

  get getFilterByName() {
    return this.applications.filter((option) => {
      return option.name.toString().toLowerCase().indexOf(this.name.toLowerCase()) >= 0;
    });
  }

  // visibilité des card fonction
  visiblePages() {
    let numberCardDebut = (this.current - 1) * this.perPage;
    let numberCardFin = this.current * this.perPage;
    return this.applications.slice(numberCardDebut, numberCardFin);
  }

  created() {
    this.init();
  }

  async init() {
    this.applications = await this.applicationService.getApplications();
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
