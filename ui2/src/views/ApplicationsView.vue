<template>
  <div>
    <PageView>
      <h1 class="title main-title">{{ $t("titles.applications-page") }}</h1>
      <UploadApplication />
      <b-table
        :data="applications"
        :striped="true"
        :isFocusable="true"
        :isHoverable="true"
      >
        <b-table-column field="name" label="Name" sortable v-slot="props">
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
import UploadApplication from "@/components/applications/UploadApplication.vue";
import { ApplicationService } from "@/services/rest/ApplicationService";
import { Component, Vue } from "vue-property-decorator";
import PageView from "./common/PageView.vue";

@Component({
  components: { PageView, UploadApplication },
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
}
</script>
