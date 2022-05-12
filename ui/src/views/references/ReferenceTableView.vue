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
        height="72.5vh"
        style="padding-bottom: 20px; position: relative; z-index: 1"
        paginated
        :current-page="currentPage"
        :per-page="params.limit"
      >
        <template #pagination>
          <b-pagination
            v-model="currentPage"
            :current-page.sync="currentPage"
            :per-page="params.limit"
            :total="totalRows"
            role="navigation"
            :aria-label="$t('menu.aria-pagination')"
            :aria-current-label="$t('menu.aria-curent-page')"
            :aria-next-label="$t('menu.aria-next-page')"
            :aria-previous-label="$t('menu.aria-previous-page')"
            order="is-centered"
            range-after="3"
            range-before="3"
            :rounded="true"
            @change="changePage"
          />
        </template>
        <b-table-column
          v-for="column in columns"
          :key="column.id"
          :field="column.id"
          :label="column.title"
          sortable
          :sticky="column.key"
          v-slot="props"
        >
          <span v-if="info(column.id) || multiplicity(column.id, props.row[column.id])">
            <b-button
              size="is-small"
              type="is-dark"
              v-if="showBtnTablDynamicColumn(props.row[column.id])"
              @click="showModal(column.id, props.row[column.id])"
              icon-left="eye"
              rounded
              style="height: inherit"
            ></b-button>
            <p v-else></p>
            <b-modal v-model="isCardModalActive" class="modalCardRef" width="70%">
              <div class="card">
                <div class="card-header">
                  <div class="title card-header-title">
                    <p field="name" style="font-size: 1.5rem">
                      {{ column.title }}
                    </p>
                  </div>
                </div>
                <div class="card-content">
                  <div class="columns modalArrayObj" v-for="key in modalArrayObj" :key="key.id">
                    <p class="column" v-if="key.column">
                      {{ key.column }} {{ $t("ponctuation.colon") }}
                    </p>
                    <p class="column" v-if="key.value">{{ key.value }}</p>
                  </div>
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
                :label="'' + (tableValues.indexOf(props.row) + 1 + params.offset)"
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
import { InternationalisationService } from "@/services/InternationalisationService";
import { DownloadDatasetQuery } from "@/model/application/DownloadDatasetQuery";

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
  params = new DownloadDatasetQuery({
    application: null,
    applicationNameOrId: this.applicationName,
    reference: this.refId,
    offset: 0,
    limit: 10,
  });
  totalRows = -1;
  currentPage = 1;

  application = new ApplicationResult();
  subMenuPaths = [];
  reference = {};
  columns = [];
  referenceValues = [];
  tableValues = [];

  // show modal and cards
  isCardModalActive = false;
  modalArrayObj = [];
  modalTblObj = [];
  referencesDynamic;
  display = "__display_" + window.localStorage.lang;

  async changePage(value) {
    this.params.offset = (value - 1) * this.params.limit;
    const references = await this.referenceService.getReferenceValues(
        this.applicationName,
        this.refId,
        {
          _offset_: this.params.offset,
          _limit_: this.params.limit,
        }
    );
    if (references) {
      this.referenceValues = references.referenceValues;
    }
    if (this.referenceValues) {
      this.tableValues = Object.values(this.referenceValues).map((refValue) => refValue.values);
    }
  }

  async showModal(columName, tablDynamicColumn) {
    this.isCardModalActive = true;
    this.modalArrayObj = Object.entries(tablDynamicColumn)
      .filter((a) => a[1])
      .map(function (a) {
        let obj = {};
        obj[a[0]] = a[1];
        return obj;
      });
    if (this.referencesDynamic) {
      for (let i = 0; i < this.referencesDynamic.referenceValues.length; i++) {
        let hierarchicalKey = this.referencesDynamic.referenceValues[i].hierarchicalKey;
        for (let j = 0; j < this.modalArrayObj.length; j++) {
          if (this.modalArrayObj[j][hierarchicalKey]) {
            let column = this.referencesDynamic.referenceValues[i].values[this.display]
              ? this.referencesDynamic.referenceValues[i].values[this.display]
              : hierarchicalKey;
            let value = this.modalArrayObj[j][hierarchicalKey];
            this.modalArrayObj[j] = { ...this.modalArrayObj[j], column: column, value: value };
          }
        }
        for (let j = 0; j < tablDynamicColumn.length; j++) {
          if (tablDynamicColumn[j] === hierarchicalKey) {
            let value = this.referencesDynamic.referenceValues[i].values[this.display]
              ? this.referencesDynamic.referenceValues[i].values[this.display]
              : columName;
            this.modalArrayObj[j] = { ...this.modalArrayObj[j], value: value };
          }
        }
      }
      return this.modalArrayObj;
    }
    return this.modalArrayObj;
  }

  info(refType) {
    let dynamicColumns = Object.entries(this.reference.dynamicColumns).filter((a) => a[1]);
    for (let i = 0; i < dynamicColumns.length; i++) {
      if (dynamicColumns[i][0] === refType) return true;
    }
    return false;
  }

  showBtnTablDynamicColumn(tablDynamicColumn) {
    let showModal = Object.entries(tablDynamicColumn)
      .filter((a) => a[1])
      .map(function (a) {
        let obj = {};
        obj[a[0]] = a[1];
        return obj;
      });
    return showModal.length !== 0;
  }

  multiplicity(column, arrayValues) {
    for (let i = 0; i < this.tableValues.length; i++) {
      let showModal = Object.entries(this.tableValues[i]).filter((a) => a[1]);
      for (let j = 0; j < showModal.length; j++) {
        if (
          showModal[j][0] === column &&
          showModal[j][1] === arrayValues &&
          Array.isArray(showModal[j][1])
        ) {
          return true;
        }
      }
    }
    return false;
  }

  async created() {
    await this.init();
    await this.setInitialVariables();
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
        this.refId,
        {
          _offset_: this.params.offset,
          _limit_: this.params.limit,
        }
      );
      if (references) {
        this.referenceValues = references.referenceValues;
      }
      for (let i = 0; i < this.application.referenceSynthesis.length; i++) {
        if (this.application.referenceSynthesis[i].referenceType === this.refId) {
          this.totalRows = this.application.referenceSynthesis[i].lineCount;
        }
      }
    } catch (error) {
      this.alertService.toastServerError();
    }
  }

  async setInitialVariables() {
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
        ...Object.values(this.reference.dynamicColumns).sort((c1, c2) => {
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
    let dynamicColumns = Object.entries(this.reference.dynamicColumns).filter((a) => a[1]);
    for (let i = 0; i < dynamicColumns.length; i++) {
      this.referencesDynamic = await this.referenceService.getReferenceValues(
        this.applicationName,
        dynamicColumns[i][1].reference,
        {
          _offset_: this.offset,
          _limit_: this.limit,
        }
      );
    }
    let interNameColumn = Object.entries(this.application.internationalization.references).filter(
      (a) => a[1]
    );
    for (let i = 0; i < this.columns.length; i++) {
      for (let j = 0; j < interNameColumn.length; j++) {
        if (interNameColumn[j][0] === this.reference.id) {
          let listInterHeaderColumn = Object.entries(
            interNameColumn[j][1].internationalizedDynamicColumns
          ).filter((a) => a[1]);
          for (let g = 0; g < listInterHeaderColumn.length; g++) {
            if (this.columns[i].id === listInterHeaderColumn[g][0]) {
              let tradNameColumn = Object.entries(listInterHeaderColumn[g][1]).filter((a) => a[1]);
              for (let x = 0; x < tradNameColumn.length; x++) {
                if (tradNameColumn[x][0] === window.localStorage.lang) {
                  this.columns[i].title = tradNameColumn[x][1];
                }
              }
            }
          }
        }
      }
      if (this.application.references[this.columns[i].id]) {
        this.referencesDynamic = await this.referenceService.getReferenceValues(
          this.applicationName,
          this.columns[i].id
        );
      }
    }
  }
}
</script>
<style lang="scss" scoped>
.b-table .table th.is-sortable{
  width: max-content;
  position: sticky;
}
</style>
