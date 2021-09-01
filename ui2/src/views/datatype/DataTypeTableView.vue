/* eslint-disable @intlify/vue-i18n/no-raw-text */
<template>
  <PageView class="with-submenu">
    <SubMenu :paths="subMenuPaths" :root="application.title" />

    <h1 class="title main-title">{{ dataTypeId }}</h1>
    <div class="DataSetTableView-wrapper table-wrapper">
      <table class="table is-striped">
        <tr>
          <td>
            <b-button
              icon-left="filter"
              :label="$t('applications.trier')"
              type="is-primary"
              @click="showSort = !showSort"
              outlined
            ></b-button>
          </td>
          <td>
            <b-button icon-left="redo" type="is-danger" @click="reInit" outlined>{{
              $t("dataTypesManagement.réinitialiser")
            }}</b-button>
          </td>
        </tr>
      </table>
    </div>
    <b-modal v-model="currentReferenceDetail.active" custom-class="referenceDetails" width="500">
      <div class="card">
        <header class="card-header is-align-content-center">
          <p class="card-header-title">{{ currentReferenceDetail.reference }}</p>
        </header>
        <div class="card-content">
          <div class="content is-align-content-center">
            <b-table
              :columns="currentReferenceDetail.columns"
              :data="currentReferenceDetail.data"
            />
          </div>
        </div>
      </div>
    </b-modal>

    <div v-if="showSort" class="notification" style="background-color: rgba(0, 163, 166, 0.1)">
      <div class="content">
        <div class="columns">
          <div class="column is-9">
            <b-tabs
              v-model="activeTab"
              :multiline="true"
              type="is-boxed"
              position="is-right"
              style="text-transform: capitalize; text-decoration: none"
            >
              <template v-for="variable in variables" class="row variableComponent">
                <b-tab-item
                  :key="variable.id"
                  :label="variable.id"
                  style="text-transform: capitalize"
                >
                  <div
                    v-for="(variableComponent, index) in variableComponentsListToSort"
                    :key="index"
                    :class="variableComponent.order"
                    class="row variableComponent"
                  >
                    <div
                      v-if="variableComponent.variableComponentKey.variable == variable.id"
                      class="columns"
                    >
                      <div class="column orderLabel">
                        {{ variableComponent.variableComponentKey.variable }} :
                        {{ variableComponent.variableComponentKey.component }}
                      </div>
                      <div>
                        <b-button
                          class="column asc"
                          style="margin: 10px; border-color: #dbdbdb"
                          type="is-white"
                          @click="addVariableComponentToSortedList(variableComponent, 'ASC')"
                        >
                          {{ $t("dataTypesManagement.ASC") }}
                        </b-button>
                      </div>
                      <div style="margin-right: 10px">
                        <b-button
                          class="column desc"
                          style="margin: 10px; border-color: #dbdbdb"
                          type="is-white"
                          @click="addVariableComponentToSortedList(variableComponent, 'DESC')"
                        >
                          {{ $t("dataTypesManagement.DESC") }}
                        </b-button>
                      </div>
                    </div>
                  </div>
                </b-tab-item>
              </template>
            </b-tabs>
          </div>
          <div class="column is-3">
            <draggable class="rows">
              <div
                v-for="(variableComponent, index) in this.params.variableComponentOrderBy"
                :key="index"
                :class="variableComponent.order"
                class="row"
              >
                <div class="control column" style="padding: 6px" :id="variableComponent.variableComponentKey.variable+variableComponent.variableComponentKey.component">
                  <div class="tags has-addons">
                    <span class="tag is-primary grape" style="font-size: 1rem">
                      <b-icon icon="stream" style="transform: rotate(180deg)"></b-icon>
                    </span>
                    <span class="tag is-primary orderLabel" style="font-size: 1rem">
                      {{ variableComponent.order }} ->
                      {{ variableComponent.variableComponentKey.variable }} :
                      {{ variableComponent.variableComponentKey.component }}
                    </span>
                    <a class="tag is-delete is-primary" style="font-size: 1rem; color: white" @click="deleteTag(variableComponent.variableComponentKey.variable,variableComponent.variableComponentKey.component)"></a>
                  </div>
                </div>
              </div>
            </draggable>
            <div class="row">
              <div class="columns">
                <div class="column">
                  <b-button icon-left="redo" expanded type="is-danger" @click="reInit" outlined>{{
                    $t("dataTypesManagement.réinitialiser")
                  }}</b-button>
                </div>
                <div class="column">
                  <b-button
                    icon-left="check"
                    type="is-success"
                    expanded
                    @click="initDatatype"
                    outlined
                    >{{ $t("dataTypesManagement.validate") }}</b-button
                  >
                </div>
              </div>
            </div>
          </div>
        </div>
      </div>
    </div>
    <div class="b-table">
      <div class="DataSetTableView-wrapper table-wrapper has-sticky-header" style="height: 750px">
        <table class="table is-striped">
          <caption v-if="variables.length == 0">
            {{
              $t("alert.dataTypeFiltreEmpty")
            }}
            <b-button icon-left="redo" type="is-primary" @click="reInit">{{
              $t("dataTypesManagement.réinitialiser")
            }}</b-button>
          </caption>
          <thead>
            <tr class="DataSetTableView-variable-row">
              <th
                v-for="variable in variables"
                :key="variable.id"
                :colspan="Object.values(variable.components).length"
                :variable="variable.id"
              >
                {{ variable.label }}
              </th>
            </tr>
            <tr>
              <th
                v-for="(comp, index) in variableComponents"
                :key="`${comp.label}-${index}`"
                :component="comp.component"
                :variable="comp.variable"
              >
                {{ comp.label }}
                <!--b-icon :icon="getSortIcon(comp.variable, comp.component)"></b-icon-->
              </th>
            </tr>
            <tr>
              <th
                v-for="(component, index) in variableComponents"
                :key="`${index}`"
                :component="component.component"
                :variable="component.variable"
              >
                <b-field grouped>
                  <b-field v-if="'date' == component.type || 'numeric' == component.type">
                    <CollapsibleInterval
                      :variableComponent="component"
                      @setting_interval="addSearch"
                    ></CollapsibleInterval>
                  </b-field>
                  <b-field>
                    <b-input
                      v-model="search[component.variable + '_' + component.component]"
                      icon="search"
                      icon-clickable
                      icon-pack="fas"
                      placeholder="Search..."
                      type="search"
                      @icon-click="addSearch(component)"
                    >
                    </b-input>
                  </b-field>
                </b-field>
              </th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="(row, rowIndex) in rows" :key="row.rowId" :rowId="row.rowId">
              <td
                v-for="(component, index) in variableComponents"
                :key="`row_${rowIndex}-${index}`"
                :component="component.component"
                :variable="component.variable"
              >
                <span>
                  {{ row[component.variable][component.component] }}
                  {{ row.result }}
                </span>
                <span v-if="getRefsLinkedToId(row, component)">
                  <b-button
                    icon-right="eye"
                    size="is-small"
                    @click="getReferenceValues(row, component)"
                  >
                  </b-button>
                </span>
              </td>
            </tr>
          </tbody>
        </table>
      </div>
      <b-pagination
        v-model="currentPage"
        :per-page="params.limit"
        :total="totalRows"
        aria-current-label="Current page"
        aria-next-label="Next page"
        aria-page-label="Page"
        aria-previous-label="Previous page"
        order="is-centered"
        range-after="3"
        range-before="3"
        size="is-large"
        @change="changePage"
      >
      </b-pagination>
    </div>
  </PageView>
</template>

<script>
import { Component, Prop, Vue } from "vue-property-decorator";
import PageView from "@/views/common/PageView.vue";
import { ApplicationService } from "@/services/rest/ApplicationService";
import { ApplicationResult } from "@/model/ApplicationResult";
import SubMenu, { SubMenuPath } from "@/components/common/SubMenu.vue";
import { DataService } from "@/services/rest/DataService";
import { AlertService } from "@/services/AlertService";
import CollapsibleInterval from "@/components/common/CollapsibleInterval.vue";
import { ReferenceService } from "@/services/rest/ReferenceService";
import { DownloadDatasetQuery } from "@/model/application/DownloadDatasetQuery";
import { VariableComponentFilters } from "@/model/application/VariableComponentFilters";
import { VariableComponentKey } from "@/model/application/VariableComponentKey";
import { IntervalValues } from "@/model/application/IntervalValues";
import { VariableComponentOrderBy } from "@/model/application/VariableComponentOrderBy";
import draggable from "vuedraggable";

@Component({
  components: { PageView, SubMenu, CollapsibleInterval, draggable },
})
export default class DataTypeTableView extends Vue {
  @Prop() applicationName;
  @Prop() dataTypeId;
  @Prop() applicationConfiguration;

  applicationService = ApplicationService.INSTANCE;
  dataService = DataService.INSTANCE;
  referenceService = ReferenceService.INSTANCE;
  alertService = AlertService.INSTANCE;
  arrow;
  application = new ApplicationResult();
  subMenuPaths = [];
  rows = [];
  variables = [];
  variableComponents = [];
  mapVariableIndexByColumnIndex = new Map();
  params = new DownloadDatasetQuery({
    application: null,
    applicationNameOrId: this.applicationName,
    dataType: this.dataTypeId,
    offset: 0,
    limit: 15,
    variableComponentSelects: [],
    variableComponentFilters: [],
    variableComponentOrderBy: [],
  });
  showDetails = false;
  showSort = false;
  controlPanels = null;
  totalRows = -1;
  currentPage = 1;
  variableComponentsListToSort = [];
  search = {};
  refsLinkedTo = {};
  loadedReferences = {};
  currentReferenceDetail = { active: false };
  activeTab = 0;

  async created() {
    await this.init();
    this.subMenuPaths = [
      new SubMenuPath(
        this.$t("dataTypesManagement.data-types").toLowerCase(),
        () => this.$router.push(`/applications/${this.applicationName}/dataTypes`),
        () => this.$router.push(`/applications`)
      ),
      new SubMenuPath(
        this.dataTypeId,
        () =>
          this.$router.push(`/applications/${this.applicationName}/dataTypes/${this.dataTypeId}`),
        () => this.$router.push(`/applications/${this.applicationName}/dataTypes`)
      ),
    ];
  }

  async reInit() {
    this.params = new DownloadDatasetQuery({
      application: null,
      applicationNameOrId: this.applicationName,
      dataType: this.dataTypeId,
      offset: 0,
      limit: 15,
      variableComponentSelects: [],
      variableComponentFilters: [],
      variableComponentOrderBy: [],
    });
    this.initDatatype();
  }

  async init() {
    this.application = await this.applicationService.getApplication(this.applicationName);
    await this.initDatatype();
  }

  async initDatatype() {
    this.showSort = false;
    const dataTypes = await this.dataService.getDataType(
      this.applicationName,
      this.dataTypeId,
      this.params
    );
    this.refsLinkedTo = dataTypes.rows.reduce((acc, d) => {
      acc[d.rowId] = d.refsLinkedTo;
      return acc;
    }, {});
    this.totalRows = dataTypes.totalRows;
    this.rows = dataTypes.rows.map((r) => {
      return { rowId: r.rowId, ...r.values };
    });

    const variablesModels = this.application.dataTypes[this.dataTypeId].variables;
    this.variables = dataTypes.variables.map((v) => variablesModels[v]);
    this.variableComponents = this.variables
      .map((v) => {
        return Object.values(v.components).map((c) =>
          Object.assign(c, {
            variable: v.label,
            component: c.id,
            ...this.getVariableComponentInfos(v.label, c.id, dataTypes),
          })
        );
      })
      .flat();
    this.variableComponentsListToSort = this.variables
      .map((v) => {
        return Object.keys(v.components).reduce((acc, comp) => {
          return [
            ...acc,
            {
              variableComponentKey: { variable: v.id, component: comp },
              order: null,
              ...this.getVariableComponentInfos(v.id, comp, dataTypes),
            },
          ];
        }, []);
      })
      .flat();

    let columnIndex = 0;
    this.variables.forEach((variable, variableIndex) => {
      Object.values(variable.components).forEach(() => {
        let columnIndexes = this.mapVariableIndexByColumnIndex.get(variableIndex);
        if (!columnIndexes) {
          columnIndexes = [];
        }
        columnIndexes.push(columnIndex);
        this.mapVariableIndexByColumnIndex.set(variableIndex, columnIndexes);
        columnIndex++;
      });
    });
  }

  getRefsLinkedToId(row, component) {
    return this.refsLinkedTo[row.rowId][component.variable][component.component];
  }

  async getReferenceValues(row, component) {
    const rowId = this.getRefsLinkedToId(row, component);
    const variable = component.checker.refType;
    if (!this.loadedReferences[rowId]) {
      let params = { _row_id_: [rowId] };
      if (!variable) {
        params.any = true;
      }
      const reference = await this.referenceService.getReferenceValues(
        this.applicationName,
        variable,
        params
      );
      const data = Object.entries(reference.referenceValues[0].values)
        .map((entry) => ({ colonne: entry[0], valeur: entry[1] }))
        .reduce((acc, entry) => {
          acc.push(entry);
          return acc;
        }, []);
      const result = {};
      result[rowId] = {
        data: data,
        columns: [
          {
            field: "colonne",
            label: "Colonne",
          },
          {
            field: "valeur",
            label: "Valeur",
          },
        ],
        active: true,
        reference: variable,
      };
      this.loadedReferences = {
        ...this.loadedReferences,
        ...result,
      };
    }
    let referenceValue = this.loadedReferences[rowId];
    this.currentReferenceDetail = { ...referenceValue, active: true };
    return referenceValue;
  }

  async changePage(value) {
    this.params.offset = (value - 1) * this.params.limit;
    await this.initDatatype();
  }

  getVariableIndex(columnIndex) {
    let variableIndex = 0;
    for (const [key, value] of this.mapVariableIndexByColumnIndex) {
      if (value.some((v) => v === columnIndex)) {
        variableIndex = key;
        break;
      }
    }
    return variableIndex;
  }

  addVariableComponentToSortedList(variableComponentSorted, order) {
    variableComponentSorted.order = variableComponentSorted.order == order ? null : order;
    this.params.variableComponentOrderBy = this.params.variableComponentOrderBy.filter(
      (c) =>
        c.variableComponentKey.variable != variableComponentSorted.variableComponentKey.variable ||
        c.variableComponentKey.component != variableComponentSorted.variableComponentKey.component
    );
    if (variableComponentSorted.order) {
      this.params.variableComponentOrderBy.push(
        new VariableComponentOrderBy(variableComponentSorted)
      );
    }
  }
  deleteTag(variable,component) {
    this.params.variableComponentOrderBy = this.params.variableComponentOrderBy.filter(
      (c) =>
        c.variableComponentKey.variable != variable ||
        c.variableComponentKey.component != component
    );
    this.params.variableComponentOrderBy.delete();
    document.getElementById(variable+component).remove();
  }

  getSortIcon(variable, component) {
    variable, component, event;
    let icon = this.params.variableComponentOrderBy
      .filter(
        (c) =>
          c.variableComponentKey.variable == variable &&
          c.variableComponentKey.component == component
      )
      .map((vc) => {
        if (vc.order == "ASC") {
          return "arrow-down";
        } else if (vc.order == "DESC") {
          return "arrow-up";
        } else {
          return "";
        }
      })[0];
    return icon ? icon : null;
  }

  addSearch(variableComponent) {
    let { key, variable, component, type, format } = variableComponent;
    let value = this.search[key];
    this.params.variableComponentFilters = this.params.variableComponentFilters.filter(
      (c) =>
        c.variableComponentKey.variable != variable || c.variableComponentKey.component != component
    );
    let search = null;
    if (value && value.length > 0) {
      search = new VariableComponentFilters({
        variableComponentKey: new VariableComponentKey({
          variable: variable,
          component: component,
        }),
        filter: value,
        type: type,
        format: format,
      });
    }
    if (variableComponent.intervalValues) {
      search = new VariableComponentFilters({
        variableComponentKey: new VariableComponentKey({
          variable: variable,
          component: component,
        }),
        type: type,
        format: format,
        intervalValues: variableComponent.intervalValues,
        ...(search ? new IntervalValues(search) : {}),
      });
    }
    if (search) {
      this.params.variableComponentFilters.push(search);
    }
    this.initDatatype();
  }

  getVariableComponentInfos(variableId, componentId, dataTypes) {
    let type = null;
    let format = null;
    var checker = null;
    let key = variableId + "_" + componentId;
    if (dataTypes.checkedFormatVariableComponents) {
      if (
        dataTypes.checkedFormatVariableComponents.DateLineChecker &&
        dataTypes.checkedFormatVariableComponents.DateLineChecker[key]
      ) {
        type = "date";
        format = dataTypes.checkedFormatVariableComponents.DateLineChecker[key].pattern;
        checker = dataTypes.checkedFormatVariableComponents.DateLineChecker[key];
      } else if (
        dataTypes.checkedFormatVariableComponents.IntegerChecker &&
        dataTypes.checkedFormatVariableComponents.IntegerChecker[key]
      ) {
        type = "numeric";
        format = "integer";
        checker = dataTypes.checkedFormatVariableComponents.IntegerChecker[key];
      } else if (
        dataTypes.checkedFormatVariableComponents.FloatChecker &&
        dataTypes.checkedFormatVariableComponents.FloatChecker[key]
      ) {
        type = "numeric";
        format = "float";
        checker = dataTypes.checkedFormatVariableComponents.FloatChecker[key];
      } else if (
        dataTypes.checkedFormatVariableComponents.ReferenceLineChecker &&
        dataTypes.checkedFormatVariableComponents.ReferenceLineChecker[key]
      ) {
        type = "reference";
        format = "uuid";
        checker = dataTypes.checkedFormatVariableComponents.ReferenceLineChecker[key];
      } else {
        type = null;
        format = null;
      }
    }
    return {
      type: type,
      format: format,
      key: key,
      checker: checker,
    };
  }
}
</script>

<style lang="scss" scoped>
$row-variable-height: 60px;

.DataSetTableView-wrapper {
  margin-bottom: 1.5rem;

  &.table-wrapper.has-sticky-header {
    th {
      position: sticky;
      top: $row-variable-height;
      z-index: 2;
      background: white;
      white-space: nowrap;
    }
  }
}

.referenceToast {
  background-color: rgb(61, 107, 8);
}

.DataSetTableView-variable-row {
  height: $row-variable-height;
}

.orderLabel {
  flex-grow: 10;
}
.grape {
  cursor: move;
}

.row.variableComponent {
  padding: 0;
}
.row.variableComponent:hover {
  background-color: rgba(0, 163, 166, 0.2);
}

.ASC .asc,
.DESC .desc {
  background-color: $primary;
  color: white;
}

.numberInput {
  width: 3em;
}

.button.is-success.is-outlined.is-fullwidth {
  color: $primary-dark;
  border-color: $primary-dark;
}
.button.is-success.is-outlined.is-fullwidth:hover {
  border-color: $success;
}
.columns {
  margin: 0;
}
</style>
