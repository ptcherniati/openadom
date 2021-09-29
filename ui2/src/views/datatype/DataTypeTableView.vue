/* eslint-disable @intlify/vue-i18n/no-raw-text */
<template>
  <PageView class="with-submenu">
    <SubMenu :paths="subMenuPaths" :root="application.localName || application.title" />

    <h1 class="title main-title">{{ localDatatypeName || dataTypeId }}</h1>
    <div class="columns" v-if="!showSort && !showFilter">
      <div
        v-if="
          this.params.variableComponentOrderBy.length !== 0 ||
          this.params.variableComponentFilters.length !== 0
        "
        class="column is-5-desktop is-12-tablet"
      >
        {{ $t("dataTypesManagement.sorted") }} {{ $t("ponctuation.colon") }}
        <b-field grouped group-multiline>
          <b-taglist>
            <div
              v-for="(variableComponent, index) in this.params.variableComponentOrderBy"
              :key="index"
            >
              <b-tag
                type="is-primary"
                size="is-medium"
                rounded
                style="margin-left: 10px; margin-right: 10px; margin-bottom: 10px"
              >
                {{ variableComponent.variableComponentKey.variable }}
                {{ $t("ponctuation.colon") }}
                {{ variableComponent.variableComponentKey.component }}
                {{ $t("ponctuation.arrow-right") }}
                {{ variableComponent.order }}
              </b-tag>
            </div>
          </b-taglist>
        </b-field>
      </div>
      <div
        v-if="
          this.params.variableComponentOrderBy.length !== 0 ||
          this.params.variableComponentFilters.length !== 0
        "
        class="column is-5-desktop is-12-tablet"
      >
        {{ $t("dataTypesManagement.filtered") }} {{ $t("ponctuation.colon") }}
        <b-field grouped group-multiline>
          <b-taglist>
            <div
              v-for="(variableComponent, index) in this.params.variableComponentFilters"
              :key="index"
            >
              <b-tag
                size="is-medium"
                rounded
                style="margin-left: 10px; margin-right: 10px; margin-bottom: 10px"
              >
                {{ variableComponent.variableComponentKey.variable }}
                {{ $t("ponctuation.colon") }}
                {{ variableComponent.variableComponentKey.component }}
                {{ $t("ponctuation.arrow-right") }}
                {{ variableComponent.filter }}
              </b-tag>
            </div>
          </b-taglist>
        </b-field>
      </div>
    </div>
    <div class="columns">
      <div class="column is-5-desktop is-4-tablet">
        <b-button
          icon-left="sort-amount-down"
          :label="$t('applications.trier')"
          type="is-primary"
          @click="showSort = !showSort"
          outlined
        ></b-button>
      </div>
      <div class="column is-5-desktop is-4-tablet">
        <b-button
          icon-left="filter"
          :label="$t('applications.filter')"
          type="is-light"
          @click="showFilter = !showFilter"
          outlined
          inverted
        ></b-button>
      </div>
      <div class="column is-2-desktop is-4-tablet">
        <b-button icon-left="redo" type="is-danger" @click="reInit" outlined
          >{{ $t("dataTypesManagement.réinitialiser") }}
          {{ $t("dataTypesManagement.all") }}</b-button
        >
      </div>
    </div>
    <b-modal v-model="currentReferenceDetail.active" custom-class="referenceDetails">
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
      <h2>{{ $t("applications.trier") }}</h2>
      <div class="content">
        <div class="columns is-multiline">
          <div class="column is-9-desktop is-12-tablet">
            <b-tabs
              v-model="activeTab"
              :multiline="true"
              type="is-boxed"
              position="is-centered"
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
                      v-if="variableComponent.variableComponentKey.variable === variable.id"
                      class="columns"
                    >
                      <div class="column orderLabel">
                        {{ variableComponent.variableComponentKey.variable }}
                        {{ $t("ponctuation.colon") }}
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
          <div class="column is-3-desktop is-12-tablet">
            <draggable class="rows">
              <div
                v-for="(variableComponent, index) in this.params.variableComponentOrderBy"
                :key="index"
                :class="variableComponent.order"
                class="row"
              >
                <div
                  class="control column"
                  style="padding: 6px"
                  :id="
                    variableComponent.variableComponentKey.variable +
                    variableComponent.variableComponentKey.component
                  "
                >
                  <div class="tags has-addons">
                    <span class="tag is-primary grape" style="font-size: 1rem">
                      <b-icon icon="stream" style="transform: rotate(180deg)"></b-icon>
                    </span>
                    <span class="tag is-primary orderLabel" style="font-size: 1rem">
                      {{ variableComponent.variableComponentKey.variable }}
                      {{ $t("ponctuation.colon") }}
                      {{ variableComponent.variableComponentKey.component }}
                      {{ $t("ponctuation.arrow-right") }}
                      {{ variableComponent.order }}
                    </span>
                    <a
                      class="tag is-delete is-primary"
                      style="font-size: 1rem; color: white"
                      @click="
                        deleteTag(
                          variableComponent.variableComponentKey.variable,
                          variableComponent.variableComponentKey.component
                        )
                      "
                    ></a>
                  </div>
                </div>
              </div>
            </draggable>
            <div class="row">
              <div class="columns">
                <div class="column">
                  <b-button icon-left="redo" expanded type="is-danger" @click="clearOrder" outlined
                    >{{ $t("dataTypesManagement.réinitialiser") }}
                    {{ $t("dataTypesManagement.trie") }}</b-button
                  >
                </div>
                <div class="column">
                  <b-button icon-left="check" type="is-dark" expanded @click="initDatatype" outlined
                    >{{ $t("dataTypesManagement.validate") }}
                    {{ $t("dataTypesManagement.trie") }}</b-button
                  >
                </div>
              </div>
            </div>
          </div>
        </div>
      </div>
    </div>
    <div v-if="showFilter" class="notification">
      <h2>{{ $t("applications.filter") }}</h2>
      <div class="columns is-multiline">
        <div
          class="column is-2-desktop is-6-tablet is-12-mobile"
          v-for="(variable, index) in variables"
          :key="variable.id"
          :variable="variable.id"
        >
          <b-collapse class="card" animation="slide" :open="isOpen == index" @open="isOpen = index">
            <template #trigger="props">
              <div class="card-header" role="button">
                <p class="card-header-title" style="text-transform: capitalize">
                  {{ variable.id }}
                </p>
                <a class="card-header-icon">
                  <b-icon :icon="props.open ? 'sort-down' : 'sort-up'"> </b-icon>
                </a>
              </div>
            </template>
            <div class="card-content" style="padding-bottom: 12px; padding-top: 12px">
              <div
                class="content"
                v-for="(component, index) in variableComponents"
                :key="`${index}`"
                :component="component.component"
                :variable="component.variable"
                style="margin-bottom: 10px"
              >
                <b-field v-if="variable.id === component.variable" :label="component.component">
                  <b-field v-if="'date' === component.type || 'numeric' === component.type">
                    <CollapsibleInterval
                      :variableComponent="component"
                      @setting_interval="addVariableSearch"
                    ></CollapsibleInterval>
                  </b-field>
                  <b-input
                    v-model="search[component.variable + '_' + component.component]"
                    icon-right="search"
                    placeholder="Search..."
                    type="search"
                    @blur="addVariableSearch(component)"
                    size="is-small"
                  ></b-input>
                  <b-field>
                    <b-button
                      type="is-dark"
                      size="is-small"
                      @click="this.params.variableComponentFilters.isRegex = true"
                      outlined
                    >
                      {{ $t("ponctuation.regEx") }}</b-button
                    >
                  </b-field>
                </b-field>
              </div>
            </div>
          </b-collapse>
        </div>
      </div>
      <div class="columns">
        <div class="column is-8">
          {{ $t("dataTypesManagement.filtered") }} {{ $t("ponctuation.colon") }}
          <b-field grouped group-multiline>
            <b-taglist>
              <div
                v-for="(variableComponent, index) in this.params.variableComponentFilters"
                :key="index"
              >
                <b-tag
                  size="is-medium"
                  rounded
                  style="margin-left: 10px; margin-right: 10px; margin-bottom: 10px"
                >
                  {{ variableComponent.variableComponentKey.variable }}
                  {{ $t("ponctuation.colon") }}
                  {{ variableComponent.variableComponentKey.component }}
                  {{ $t("ponctuation.arrow-right") }}
                  {{ variableComponent.filter }}
                </b-tag>
              </div>
            </b-taglist>
          </b-field>
        </div>
        <div class="column is-2">
          <b-button icon-left="redo" expanded type="is-danger" outlined @click="clearSearch"
            >{{ $t("dataTypesManagement.réinitialiser") }}
            {{ $t("dataTypesManagement.filtre") }}</b-button
          >
        </div>
        <div class="column is-2">
          <p class="control">
            <b-button icon-left="check" type="is-dark" expanded outlined @click="addSearch"
              >{{ $t("dataTypesManagement.validate") }}
              {{ $t("dataTypesManagement.filtre") }}</b-button
            >
          </p>
        </div>
      </div>
    </div>
    <div class="b-table">
      <div class="DataSetTableView-wrapper table-wrapper has-sticky-header" style="height: 690px">
        <table class="table is-striped">
          <caption v-if="variables.length === 0">
            <div class="columns">
              {{ $t("alert.dataTypeFiltreEmpty") }}
            </div>
            <div class="columns">
              <b-button icon-left="redo" type="is-danger" @click="reInit">{{
                $t("dataTypesManagement.réinitialiser")
              }}</b-button>
            </div>
          </caption>
          <thead style="text-transform: capitalize; text-align: center">
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
          </thead>
          <tbody>
            <tr v-for="(row, rowIndex) in rows" :key="row.rowId" :rowId="row.rowId">
              <td
                style="text-align: center; vertical-align: middle"
                v-for="(component, index) in variableComponents"
                :key="`row_${rowIndex}-${index}`"
                :component="component.component"
                :variable="component.variable"
              >
                <span
                  v-if="
                    row[component.variable][component.component] &&
                    component.checker &&
                    component.checker.pattern
                  "
                >
                  {{ /.{25}(.*$)/.exec(row[component.variable][component.component])[1] }}
                </span>
                <span v-else>
                  <a
                    class="button inTable"
                    v-if="getRefsLinkedToId(row, component)"
                    @click="getReferenceValues(row, component)"
                  >
                    {{ row[component.variable][component.component] }}
                  </a>
                  <p v-if="!getRefsLinkedToId(row, component)">
                    {{ row[component.variable][component.component] }}
                  </p>
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
        :rounded="true"
        @change="changePage"
        style="padding-bottom: 20px"
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
    limit: 10,
    variableComponentSelects: [],
    variableComponentFilters: [],
    variableComponentOrderBy: [],
  });
  showDetails = false;
  showSort = false;
  showFilter = false;
  controlPanels = null;
  totalRows = -1;
  currentPage = 1;
  variableComponentsListToSort = [];
  search = {};
  refsLinkedTo = {};
  loadedReferences = {};
  currentReferenceDetail = { active: false };
  activeTab = 0;
  isOpen = 0;
  variableSearch = [];
  isRegExp = false;

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
  localeApplicationName(application) {
    return application?.internationalization?.[this.$i18n.locale] ?? application.name;
  }
  localeDatatypeName(datatype) {
    return datatype?.internationalizationName?.[this.$i18n.locale] ?? datatype.name;
  }

  async init() {
    this.application = await this.applicationService.getApplication(this.applicationName);
    this.application = {
      ...this.application,
      localName: this.localeApplicationName(this.application),
    };
    this.localDatatypeName =
      this.application.dataTypes[this.dataTypeId]?.internationalizationName?.[this.$i18n.locale];
    await this.initDatatype();
  }

  async initDatatype() {
    this.showSort = false;
    const dataTypes = await this.dataService.getDataType(
      this.applicationName,
      this.dataTypeId,
      this.params
    );
    this.translations = dataTypes.entitiesTranslations;
    this.data;
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

  getTranslation(row, component) {
    let reference = component.checker.refType;
    let translations = this.translations?.[reference];
    console.log(translations);
    let translation = row[component.variable][component.component];
    return translation;
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
  deleteTag(variable, component) {
    this.params.variableComponentOrderBy = this.params.variableComponentOrderBy.filter(
      (c) =>
        c.variableComponentKey.variable != variable || c.variableComponentKey.component != component
    );
    this.params.variableComponentOrderBy.delete();
    document.getElementById(variable + component).remove();
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

  addVariableSearch(variableComponent) {
    let { key, variable, component, type, format } = variableComponent;
    let isRegExp = this.params.variableComponentFilters.isRegex;
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
        isRegExp: isRegExp,
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
        isRegExp: isRegExp,
        intervalValues: variableComponent.intervalValues,
        ...(search ? new IntervalValues(search) : {}),
      });
    }
    if (search) {
      this.variableSearch.push(search);
    }
    this.initDatatype();
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
  clearSearch() {
    for (var i = 0; i < this.variableSearch.length; i++) {
      this.params.variableComponentFilters = [];
      this.variableSearch = [];
    }
    this.initDatatype();
  }
  clearOrder() {
    for (var i = 0; i < this.params.variableComponentOrderBy.length; i++) {
      this.params.variableComponentOrderBy = [];
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

.button.inTable {
  color: $primary;
  background-color: transparent;
  border: transparent;
}

.columns {
  margin: 0;
}
</style>
