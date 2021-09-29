<template>
  <PageView class="with-submenu">
    <SubMenu :root="application.title" :paths="subMenuPaths" />
    <h1 class="title main-title">
      {{ $t("titles.references-data", { refName: reference.label }) }}
    </h1>

    <div v-if="reference && columns">
      <b-table
        :data="tableValues"
        :striped="true"
        :isFocusable="true"
        :isHoverable="true"
        :sticky-header="true"
        :paginated="true"
        :per-page="15"
        height="100%"
      >
        <b-table-column
          v-for="column in columns"
          :key="column.id"
          :field="column.id"
          :label="column.title"
          sortable
          :sticky="column.key"
          v-slot="props"
        >
          <span v-if="column.id != '#'">
            {{ props.row[column.id] }}
          </span>
          <b-collapse v-else :open="false">
            <template #trigger>
              <b-button
                :label="'' + (tableValues.indexOf(props.row) + 1)"
                type="is-small"
                aria-controls="contentIdForA11y1"
              />
            </template>
            {{ referenceValues[tableValues.indexOf(props.row)].naturalKey }}
          </b-collapse>
        </b-table-column>
      </b-table>
    </div>
  </PageView>
</template>

<script>
import SubMenu, { SubMenuPath } from "@/components/common/SubMenu.vue";
import { ApplicationResult } from "@/model/ApplicationResult";
import { AlertService } from "@/services/AlertService";
import { ApplicationService } from "@/services/rest/ApplicationService";
import { ReferenceService } from "@/services/rest/ReferenceService";
import { Prop, Vue, Component } from "vue-property-decorator";
import PageView from "../common/PageView.vue";

@Component({
  components: { PageView, SubMenu },
})
export default class ReferenceTableView extends Vue {
  @Prop() applicationName;
  @Prop() refId;

  alertService = AlertService.INSTANCE;
  applicationService = ApplicationService.INSTANCE;
  referenceService = ReferenceService.INSTANCE;

  application = new ApplicationResult();
  subMenuPaths = [];
  reference = {};
  columns = [];
  referenceValues = [];
  tableValues = [];

  async created() {
    await this.init();
    this.setInitialVariables();
  }

  async init() {
    try {
      this.application = await this.applicationService.getApplication(this.applicationName);
      const references = await this.referenceService.getReferenceValues(
        this.applicationName,
        this.refId
      );
      if (references) {
        this.referenceValues = references.referenceValues;
      }
    } catch (error) {
      this.alertService.toastServerError();
    }
  }

  setInitialVariables() {
    if (!this.application?.references) {
      return;
    }

    this.reference = Object.values(this.application.references).find(
      (ref) => ref.id === this.refId
    );

    this.subMenuPaths = [
      new SubMenuPath(
        this.$t("referencesManagement.references").toLowerCase(),
        () => this.$router.push(`/applications/${this.applicationName}/references`),
        () => this.$router.push(`/applications`)
      ),
      new SubMenuPath(
        this.reference.label,
        () => this.$router.push(`/applications/${this.applicationName}/references/${this.refId}`),
        () => this.$router.push(`/applications/${this.applicationName}/references`)
      ),
    ];

    if (this.reference && this.reference.columns) {
      this.columns = [
        { id: "#", title: "#id", key: false, linkedTo: null },
        ...Object.values(this.reference.columns).sort((c1, c2) => {
          if (c1.title < c2.title) {
            return -1;
          }

          if (c1.title > c2.title) {
            return 1;
          }
          return 0;
        }),
      ];
    }

    if (this.referenceValues) {
      this.tableValues = Object.values(this.referenceValues).map((refValue) => refValue.values);
    }
  }
}
</script>
