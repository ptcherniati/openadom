<template>
  <PageView>
    <h1 class="title main-title">{{ $t("titles.applications-page") }}</h1>
    <div class="buttons">
      <b-button type="is-primary" @click="createApplication" icon-right="plus">
        {{ $t("applications.create") }}
      </b-button>
    </div>
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
      <b-table-column
        field="name"
        :label="$t('applications.name')"
        sortable
        width="50%"
        v-slot="props"
      >
        <div @click="displayApplication(props.row)" class="clickable">
          {{ props.row.name }}
        </div>
      </b-table-column>
      <b-table-column
        field="creationDate"
        :label="$t('applications.creation-date')"
        sortable
        v-slot="props"
      >
        <div @click="displayApplication(props.row)" class="clickable">
          {{ new Date(props.row.creationDate) }}
        </div>
      </b-table-column>
    </b-table>
  </PageView>
</template>

<script>
import { ApplicationService } from "@/services/rest/ApplicationService";
import { Component, Vue } from "vue-property-decorator";
import PageView from "@/views/common/PageView.vue";

@Component({
  components: { PageView },
})
export default class ApplicationsView extends Vue {
  applicationService = ApplicationService.INSTANCE;

  applications = [];

  created() {
    this.init();
  }

  async init() {
    this.applications = await this.applicationService.getApplications();
  }

  createApplication() {
    this.$router.push("/applicationCreation");
  }

  displayApplication(application) {
    if (!application) {
      return;
    }
    this.$router.push("/application/" + application.name + "/0");
  }
}
</script>
