<template>
  <div>
    <PageView>
      <h1 class="title main-title">{{ $t("titles.applications-page") }}</h1>
      <div class="buttons">
        <b-button
          type="is-primary"
          @click="createApplication"
          icon-right="plus"
        >
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
          label="Name"
          sortable
          width="50%"
          v-slot="props"
        >
          {{ props.row.name }}
        </b-table-column>
        <b-table-column
          field="creationDate"
          label="Creation Date"
          sortable
          v-slot="props"
        >
          {{ new Date(props.row.creationDate) }}
        </b-table-column>
      </b-table>
    </PageView>
  </div>
</template>

<script>
import { ApplicationService } from "@/services/rest/ApplicationService";
import { Component, Vue } from "vue-property-decorator";
import PageView from "./common/PageView.vue";

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
}
</script>
