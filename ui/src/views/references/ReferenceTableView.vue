<template>
  <PageView class="with-submenu">
    <SubMenu
      :root="application.localName"
      :paths="subMenuPaths"
      role="navigation"
      :aria-label="$t('menu.aria-sub-menu')"
    />
    <h1 class="title main-title">
      {{ $t("titles.references-data", { refName: application.localRefName }) }}
    </h1>

    <div v-if="reference && columns">
      <b-table
          :data="tableValues"
          :striped="true"
          :isFocusable="true"
          :isHoverable="true"
          :sticky-header="true"
          height="100%"
          style="padding-bottom: 20px; position: relative;z-index: 1;"
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
          <span v-if="column.id === 'nom du taxon déterminé'">
            <a @click="showModal(props.row[column.id])">
              {{ props.row[column.id] }}</a
            >
            <b-modal
                v-show="isSelectedName === props.row[column.id]"
                v-model="isCardModalActive"
                width = 70%
                data-backdrop="static"
            >
              <div class="card">
                <div class="card-header">
                  <div class="title card-header-title">
                    <p field="name">{{ props.row[column.id] }}</p>
                  </div>
                </div>
                <div class="card-content">
                  {{ props.row['propriétés de taxons'] }}
                  <!-- TO DO à mettre en forme
                  <b-table :data="data" :columns="columns"/> -->
                </div>
              </div>
            </b-modal>
          </span>
          <span v-else-if="column.id !== '#'">
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
      <b-pagination
        v-if="perPage <= tableValues.length"
        v-model="currentPage"
        :per-page="perPage"
        :total="tableValues.length"
        role="navigation"
        :aria-label="$t('menu.aria-pagination')"
        :aria-current-label="$t('menu.aria-curent-page')"
        :aria-next-label="$t('menu.aria-next-page')"
        :aria-previous-label="$t('menu.aria-previous-page')"
        order="is-centered"
        range-after="3"
        range-before="3"
        :rounded="true"
      >
      </b-pagination>
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
import { InternationalisationService } from "@/services/InternationalisationService";

@Component({
  components: { PageView, SubMenu },
})
export default class ReferenceTableView extends Vue {
  @Prop() applicationName;
  @Prop() refId;

  alertService = AlertService.INSTANCE;
  applicationService = ApplicationService.INSTANCE;
  internationalisationService = InternationalisationService.INSTANCE;
  referenceService = ReferenceService.INSTANCE;

  application = new ApplicationResult();
  subMenuPaths = [];
  reference = {};
  columns = [];
  referenceValues = [];
  tableValues = [];
  currentPage = 1;
  perPage = 15;

  // show modal and cards
  isSelectedName = "";
  isCardModalActive = false;
  nameColumn = 'nom du taxon déterminé';

  showModal(name) {
    this.isSelectedName = name;
    this.isCardModalActive = true;
    console.log(this.tableValues[0]);
  }

  async created() {
    await this.init();
    this.setInitialVariables();
  }

  async init() {
    try {
      this.application = await this.applicationService.getApplication(this.applicationName);
      this.application = {
        ...this.application,
        localName: this.internationalisationService.mergeInternationalization(this.application)
          .localName,
        localRefName: this.internationalisationService.localeReferenceName(
          this.application.references[this.refId],
          this.application
        ),
      };
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
