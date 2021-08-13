<template>
  <PageView class="with-submenu">
    <SubMenu :root="application.title" :paths="subMenuPaths" />

    <h1 class="title main-title">{{ dataTypeId }}</h1>
    <b-collapse v-model="showSort" aria-id="contentIdForA11y1">
      <template #trigger>
        <b-button label="Sort" type="is-primary" aria-controls="contentIdForA11y1" />
      </template>
      <div class="notification">
        <div class="content">
          <div class="rows">
            <div class="row">
              <div class="columns">
                <div class="column">
                  <b-field>
                    <ul>
                      <div class="rows">
                        <div
                          class="row variableComponent"
                          v-for="(variableComponent, index) in variableComponentsListToSort"
                          :key="index"
                          :class="variableComponent.order"
                        >
                          <div class="columns">
                            <div class="column orderLabel">
                              {{ variableComponent.order
                              }}{{ variableComponent.variableComponentKey.variable }} :
                              {{ variableComponent.variableComponentKey.component }}
                            </div>
                            <div
                              class="column asc"
                              @click="addVariableComponentToSortedList(variableComponent, 'ASC')"
                            >
                              ASC
                            </div>
                            <div
                              class="column desc"
                              @click="addVariableComponentToSortedList(variableComponent, 'DESC')"
                            >
                              DESC
                            </div>
                          </div>
                        </div>
                      </div>
                    </ul>
                  </b-field>
                </div>
                <div class="column">
                  <div class="rows">
                    <div
                      class="row"
                      v-for="(variableComponent, index) in this.params.variableComponentOrderBy"
                      :key="index"
                      :class="variableComponent.order"
                    >
                      <div class="columns">
                        <div class="column orderLabel">
                          {{ variableComponent.order }} ->
                          {{ variableComponent.variableComponentKey.variable }} :
                          {{ variableComponent.variableComponentKey.component }}
                        </div>
                      </div>
                    </div>
                  </div>
                </div>
              </div>
            </div>
            <div class="row">
              <b-button type="is-success" expanded size="is-large" @click="initDatatype"
                >order</b-button
              >
            </div>
          </div>
        </div>
      </div>
    </b-collapse>
    <div class="b-table">
      <div class="DataSetTableView-wrapper table-wrapper has-sticky-header" style="height: 750px">
        <table class="table is-striped">
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
                :variable="comp.variable"
                :component="comp.component"
              >
                {{ comp.label }}
                <b-icon :icon="getSortIcon(comp.variable, comp.component)"></b-icon>
              </th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="(row, rowIndex) in rows" :key="`row_${rowIndex}`">
              <td
                v-for="(component, index) in variableComponents"
                :key="`row_${rowIndex}-${index}`"
                :variable="component.variable"
                :component="component.component"
              >
                {{ row[variables[getVariableIndex(index)].id][component.id] }}
              </td>
            </tr>
          </tbody>
        </table>
      </div>
      <b-pagination
        :total="totalRows"
        v-model="currentPage"
        :per-page="params.limit"
        size="is-large"
        order="is-centered"
        range-before="3"
        range-after="3"
        aria-next-label="Next page"
        aria-previous-label="Previous page"
        aria-page-label="Page"
        aria-current-label="Current page"
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

@Component({
  components: { PageView, SubMenu },
})
export default class DataTypeTableView extends Vue {
  @Prop() applicationName;
  @Prop() dataTypeId;

  applicationService = ApplicationService.INSTANCE;
  dataService = DataService.INSTANCE;
  alertService = AlertService.INSTANCE;

  application = new ApplicationResult();
  subMenuPaths = [];
  rows = [];
  variables = [];
  variableComponents = [];
  mapVariableIndexByColumnIndex = new Map();
  params = {
    offset: 0,
    limit: 15,
    variableComponentOrderBy: [],
  };
  showSort = false;
  controlPanels = null
  totalRows = -1;
  currentPage = 1;
  variableComponentsListToSort = [];

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
    this.totalRows = dataTypes.totalRows;
    this.rows = dataTypes.rows.map((r) => {
      return { ...r.values };
    });

    const variablesModels = this.application.dataTypes[this.dataTypeId].variables;
    this.variables = dataTypes.variables.map((v) => variablesModels[v]);
    this.variableComponents = this.variables
      .map((v) => {
        return Object.values(v.components).map((c) =>
          Object.assign(c, { variable: v.label, component: c.id })
        );
      })
      .flat();
    this.variableComponentsListToSort = this.variables
      .map((v) => {
        return Object.keys(v.components).reduce(
          (acc, comp) => [
            ...acc,
            { variableComponentKey: { variable: v.id, component: comp }, order: null },
          ],
          []
        );
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
      this.params.variableComponentOrderBy.push(variableComponentSorted);
    }
  }
  getSortIcon(variable, component) {
    return this.params.variableComponentOrderBy
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

.DataSetTableView-variable-row {
  height: $row-variable-height;
}
.orderLabel {
  flex-grow: 10;
}
.row.variableComponent:hover {
  background-color: grey;
  color: white;
}
.ASC .asc,
.DESC .desc {
  background-color: rgb(87, 141, 87);
}
</style>
this.params.variableComponentSelects.map( (comp) => comp.variableComponentKey.variable +
this.variable + comp.variableComponentKey.component + this.component, component )
