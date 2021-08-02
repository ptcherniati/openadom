<template>
  <PageView>
    <h1 class="title main-title">{{ $t("titles.applications-page") }}</h1>

    <div class="columns" style="margin-left: 100px">
      <div class="column is-3">
        <section>
          <div class="card is-clickable" v-if="canCreateApplication">
            <div class="card-header" role="button" @click="createApplication" style="background-color: #00a3a6; opacity: 75%;">
              <a class="card-header-icon" style="color: white">
                <b-icon icon="plus">
                </b-icon>
              </a>
              <p class="card-header-title" style="color: white">
              {{ $t("applications.create") }}
              </p>
            </div>
          </div>
          <div class="card">
            <div class="card-header">
              <p class="card-header-title">
                Trier
              </p>
            </div>
            <div class="card-content">
              <div class="content">
                <b-field>
                  <b-checkbox>Nom</b-checkbox>
                </b-field>
                <b-field>
                  <b-checkbox>Plus récent</b-checkbox>
                </b-field>
              </div>
            </div>
          </div>
          <div class="card">
            <div
                class="card-header"
                role="button">
              <p class="card-header-title">
                Filtrer
              </p>
            </div>
            <div class="card-content">
              <div class="content">
                <b-field label="Name">
                  <b-input value="Kevin Garvey"></b-input>
                </b-field>
                <b-field label="Select datetime">
                  <b-datetimepicker
                      placeholder="Type or select a date..."
                      icon="calendar-today"
                      :locale="localLang"
                      editable>
                  </b-datetimepicker>
                </b-field>
              </div>
            </div>
            <footer class="card-footer">
              <i class="card-footer-item"></i>
              <i class="card-footer-item"></i>
              <a class="card-footer-item">Confirmer</a>
            </footer>
          </div>
        </section>
      </div>
      <div class="column">
        <div class="columns is-9">
          <div v-for="application in visiblePages(applications)" :key="application.name">
            <div class="column">
              <div class="applicationCard card">
                <div class="card-header">
                  <div class="title card-header-title">
                    <p field="name"> {{ application.name }}</p>
                  </div>
                  <b-button class="btnModal" icon-left="external-link-square-alt"
                            type="is-primary"
                            size="is-medium"
                            @click="showModal(application.name)"/>
                  <b-modal v-model="isCardModalActive" v-show="isSelectedName == application.name" :id="application.name">
                    <div class="card">
                      <div class="card-header">
                        <div class="title card-header-title">
                          <p field="name"> {{ application.name }}</p>
                        </div>
                      </div>
                      <div class="card-content">
                        <div class="content">
                          <p>{{ application.referenceType }}, {{ application.dataType}}</p>
                        </div>
                      </div>
                      <div class="card-footer">
                        <div class="card-footer-item">
                          <b-button icon-left="drafting-compass" @click="displayReferencesManagement(application)">{{
                              $t("applications.references")
                            }}</b-button>
                        </div>
                        <div class="card-footer-item">
                          <b-button icon-left="poll" @click="displayDataSetManagement(application)">{{
                              $t("applications.dataset")
                            }}</b-button>
                        </div>
                      </div>
                    </div>
                  </b-modal>
                </div>
                <div class="card-content">
                  <div class="content">
                    <p field="creationDate">{{ (new Date(application.creationDate)).toLocaleString(localLang) }}</p>
                  </div>
                </div>
                <div class="card-footer">
                  <div class="card-footer-item">
                    <b-button icon-left="drafting-compass" @click="displayReferencesManagement(application)">
                      {{ $t("applications.references") }}</b-button>
                  </div>
                  <div class="card-footer-item">
                    <b-button icon-left="poll" @click="displayDataSetManagement(application)">
                      {{ $t("applications.dataset") }}</b-button>
                  </div>
                </div>
              </div>
            </div>
          </div>
        </div>
        <hr />
        <b-pagination
          :total="applications.length"
          :current.sync="current"
          :range-before="2"
          :range-after="2"
          :rounded="true"
          :per-page="perPage"
          :icon-prev="prevIcon"
          :icon-next="nextIcon"
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
  current = 1;
  perPage = 12;

  visiblePages() {
    return this.applications.slice((this.current - 1) * this.perPage, this.current * this.perPage);
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
  showModal(name){
    this.isSelectedName= name;
    this.isCardModalActive = true;
  }
}
</script>

<style lang="scss" scoped>
// card & modal style
.columns {
  flex-wrap: wrap;
  margin:0px;
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
      color: #00a3a6;
      background-color: transparent;
    }
    .card-footer-item {
      border-right: none;
    }
  }
}
.card-header-title{
  &.title {
    margin-top: 0;
    text-transform: uppercase;
    margin-bottom: 0px;
  }
}
</style>
