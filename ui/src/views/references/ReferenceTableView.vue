<template>
  <PageView class="with-submenu">
    <SubMenu
      :aria-label="$t('menu.aria-sub-menu')"
      :paths="subMenuPaths"
      :root="application.localName"
      role="navigation"
    />
    <h1 class="title main-title">
      {{ $t("titles.references-data", { refName: application.localRefName }) }}
    </h1>
    <div v-if="reference && columns">
      <b-table
        :current-page="currentPage"
        :data="tableValues"
        :is-focusable="true"
        :is-hoverable="true"
        :loading="isLoading"
        :per-page="params.limit"
        :sticky-header="true"
        :striped="true"
        height="72.5vh"
        paginated
        style="padding-bottom: 20px; position: relative; z-index: 1"
      >
        <template #pagination>
          <b-pagination
            v-model="currentPage"
            :aria-current-label="$t('menu.aria-curent-page')"
            :aria-label="$t('menu.aria-pagination')"
            :aria-next-label="$t('menu.aria-next-page')"
            :aria-previous-label="$t('menu.aria-previous-page')"
            :current-page.sync="currentPage"
            :per-page="params.limit"
            :rounded="true"
            :total="totalRows"
            order="is-centered"
            range-after="3"
            range-before="3"
            role="navigation"
            @change="changePage"
          />
        </template>
        <b-table-column
          searchable
          v-for="column in columns"
          :key="column.id"
          :field="column.id"
          :label="column.title"
          :sticky="column.key"
          sortable
        >
          <template #searchable="props">
            <b-input
              v-if="column.id !== '#'"
              v-model="props.filters[props.column.field]"
              :placeholder="$t('dataTypeAuthorizations.search')"
              icon="search"
              size="is-normal"
            />
          </template>
          <template v-slot="props">
            <ReferencesDynamicLink
              v-if="info(column.id)"
              :info="info(column.id)"
              :info-values="props.row[column.id]"
              :application="application"
              :reference-type="column.reference"
              :loaded-references-by-key="{}"
              :column-id="column.id"
            ></ReferencesDynamicLink>
            <ReferencesManyLink
              v-else-if="multiplicity(column.id, props.row[column.id])"
              :multiplicity="multiplicity(column.id, props.row[column.id])"
              :info-values="props.row[column.id]"
              :application="application"
              :reference-type="column.linkedTo"
              :loaded-references-by-key="{}"
              :column-id="column.id"
            ></ReferencesManyLink>
            <ReferencesLink
              v-else-if="column.id !== '#'"
              :application="application"
              :reference-type="column.linkedTo"
              :value="
                info(column.id) || multiplicity(column.id, props.row[column.id])
                  ? ''
                  : props.row[column.id]
              "
              :loaded-references-by-key="{}"
              :column-title="column.title"
            ></ReferencesLink>
            <div v-else class="columns">
              <a
                @click="
                  askDeletionConfirmation(
                    referenceValues[tableValues.indexOf(props.row)].naturalKey
                  )
                "
              >
                <b-icon icon="times-circle" class="clickable" size="is-small" type="is-danger">
                </b-icon>
              </a>
              <b-collapse :open="false" class="column">
                <template #trigger>
                  <b-button
                    :label="'' + (tableValues.indexOf(props.row) + 1 + params.offset)"
                    aria-controls="contentIdForA11y1"
                    type="is-small"
                  />
                </template>
                {{ referenceValues[tableValues.indexOf(props.row)].naturalKey }}
              </b-collapse>
            </div>
          </template>
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
import { Component, Prop, Vue } from "vue-property-decorator";
import PageView from "../common/PageView.vue";
import { InternationalisationService } from "@/services/InternationalisationService";
import { DownloadDatasetQuery } from "@/model/application/DownloadDatasetQuery";
import ReferencesLink from "@/components/references/ReferencesLink.vue";
import ReferencesManyLink from "@/components/references/ReferencesManyLink.vue";
import ReferencesDynamicLink from "@/components/references/ReferencesDynamicLink.vue";
import { HttpStatusCodes } from "@/utils/HttpUtils";
import { VariableComponentFilters } from "@/model/application/VariableComponentFilters";
import { IntervalValues } from "@/model/application/IntervalValues";

@Component({
  components: { PageView, SubMenu, ReferencesLink, ReferencesManyLink, ReferencesDynamicLink },
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
  isLoading = false;
  application = new ApplicationResult();
  subMenuPaths = [];
  reference = {};
  columns = [];
  referenceValues = [];
  tableValues = [];
  checkedRows = [];

  // show modal and cards
  isCardModalActive = false;
  modalArrayObj = [];
  modalTblObj = [];
  referencesDynamic;
  display = "__display_" + window.localStorage.lang;
  loadedReferences = {};
  currentReferenceDetail = { active: false };
  columnSearch = [];

  addVariableSearch(columnName) {
    let { key, column, type, format } = columnName;
    let value = this.search[key];
    this.params.variableComponentFilters = this.params.variableComponentFilters.filter(
      (c) => c.column !== column
    );
    let search = null;
    if (value && value.length > 0) {
      search = new VariableComponentFilters({
        columnKey: column,
        filter: value,
        type: type,
        format: format,
      });
    }
    if (columnName.intervalValues) {
      search = new VariableComponentFilters({
        columnKey: column,
        type: type,
        format: format,
        intervalValues: columnName.intervalValues,
        ...(search ? new IntervalValues(search) : {}),
      });
    }
    if (search) {
      this.columnSearch.push(search);
    }
    this.init();
  }

  addSearch() {
    this.params.variableComponentFilters = [];
    for (var i = 0; i < this.variableSearch.length; i++) {
      if (this.variableSearch[i]) {
        this.params.variableComponentFilters.push(this.variableSearch[i]);
      }
    }
    this.initDatatype();
    this.showFilter = false;
  }

  askDeletionConfirmation(rowId) {
    this.alertService.dialog(
      this.$t("alert.warning"),
      this.$t("alert.reference-deletion-msg", { label: rowId }),
      this.$t("alert.delete"),
      "is-danger",
      () => this.deleteRowReference(rowId)
    );
  }

  async deleteRowReference(rowId) {
    console.log(rowId);
    try {
      await this.referenceService.deleteReferenceValuesByKey(
        this.applicationName,
        this.reference.label,
        rowId
      );
      this.alertService.toastSuccess(this.$t("alert.reference-updated"));
    } catch (errors) {
      await this.checkMessageErrors(errors);
    }
  }

  async checkMessageErrors(errors) {
    if (errors.httpResponseCode === HttpStatusCodes.BAD_REQUEST) {
      errors.content.then((value) => {
        for (let i = 0; i < value.length; i++) {
          this.errorsList[i] = value[i];
        }
        if (this.errorsList.length !== 0) {
          this.errorsMessages = this.errorsService.getCsvErrorsMessages(this.errorsList);
        } else {
          this.errorsMessages = this.errorsService.getErrorsMessages(errors);
        }
      });
    } else {
      this.alertService.toastError(this.$t("alert.delete-reference-error"), errors);
    }
  }

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

  info(refType) {
    let dynamicColumns = Object.entries(this.reference.dynamicColumns).filter((a) => a[1]);
    //console.log(dynamicColumns)
    for (let i = 0; i < dynamicColumns.length; i++) {
      if (dynamicColumns[i][0] === refType) return true;
    }
    return false;
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
    this.isLoading = true;
    try {
      this.application = await this.applicationService.getApplication(this.applicationName, [
        "CONFIGURATION",
        "REFERENCETYPE",
      ]);
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
    this.isLoading = false;
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
.b-table .table th.is-sortable {
  width: max-content;
  position: sticky;
}
</style>
