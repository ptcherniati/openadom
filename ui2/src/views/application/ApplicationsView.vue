<template>
  <PageView>
    <h1 class="title main-title">{{ $t("titles.applications-page") }}</h1>
    <div class="buttons" v-if="canCreateApplication">
      <b-button type="is-primary" @click="createApplication" icon-right="plus">
        {{ $t("applications.create") }}
      </b-button>
    </div>

    <div class='container has-text-centered'>
      <div class='columns is-mobile is-centered'>
        <div v-for="application in applications" :key="application.name">
          <div class='column'>
            <div class="card">
              <div class="card-header">
                <div class="title card-header-title">
                  <h2 field="name"> {{ application.name }}</h2>
                </div>
              </div>
              <div class="card-content">
                <p field="creationDate">{{ new Date(application.creationDate) }}</p>
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
<!--
    <b-table
      :data="applications"
      :striped="true"
      :isFocusable="true"
      :isHoverable="true"
      :sticky-header="true"
      :paginated="true"
      :per-page="15"
      height="100%"
    >
      <b-table-column field="name" :label="$t('applications.name')" sortable v-slot="props">
        {{ props.row.name }}
      </b-table-column>
      <b-table-column
        field="creationDate"
        :label="$t('applications.creation-date')"
        sortable
        v-slot="props"
      >
        {{ new Date(props.row.creationDate) }}
      </b-table-column>
      <b-table-column field="actions" :label="$t('applications.actions')" v-slot="props">
        <b-button icon-left="drafting-compass" @click="displayReferencesManagement(props.row)">{{
          $t("applications.references")
        }}</b-button>
        <b-button icon-left="poll" @click="displayDataSetManagement(props.row)">{{
          $t("applications.dataset")
        }}</b-button>
      </b-table-column>
    </b-table>
    --->
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
}
</script>
