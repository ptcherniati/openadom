<template>
  <PageView>
    <h1 class="title main-title">{{ $t("titles.applications-page") }}</h1>

    <div class="columns">
      <div class="column is-3">
        <b-collapse class="card" animation="slide" aria-id="contentIdForA11y3">
        <template #trigger="props">
          <div
              class="card-header"
              role="button"
              aria-controls="contentIdForA11y3">
            <p class="card-header-title">
              Filtre
            </p>
            <a class="card-header-icon">
              <b-icon
                  :icon="props.open ? 'menu-down' : 'menu-up'">
              </b-icon>
            </a>
          </div>
        </template>
        <div class="card-content">
          <div class="content">
          </div>
        </div>
        <footer class="card-footer">
        </footer>
      </b-collapse>
      </div>
      <div class="column">
        <div class="columns is-9">
          <div>
            <div class="column" v-if="canCreateApplication">
              <div class="applicationCard card is-clickable"
                   @click="createApplication" style="background-color: #00a3a6; opacity: 75%; height: 195px;">
                <div class="card-header">
                  <div class="title card-header-title" style="color: white">
                    <p>{{ $t("applications.create") }}</p>
                  </div>
                </div>
                <div class="card-content buttons is-centered" style="padding:10px;">
                  <b-button class="btnModal" icon-left="plus"
                            type="is-primary"
                            size="is-large"
                            style="color: white; opacity: 100%;"/>
                </div>
              </div>
            </div>
          </div>
          <div v-for="application in applications" :key="application.name">
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
                    <p field="creationDate">{{ (new Date(application.creationDate)).toLocaleString("fr") }}</p>
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
            </div>
          </div>
        </div>
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
  canCreateApplication = LoginService.INSTANCE.getAuthenticatedUser().authorizedForApplicationCreation;
  isSelectedName='';
  isCardModalActive = false;

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