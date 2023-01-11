<template>
  <PageView class="with-submenu">
    <SubMenu
        :aria-label="$t('menu.aria-sub-menu')"
        :paths="subMenuPaths"
        :root="application.localName || application.title"
        role="navigation"
    />

    <h1 class="title main-title">{{ application.localDatatypeName || dataTypeId }}</h1>
    <div v-if="!showSort && !showFilter" class="columns">
      <div
          v-if="
          this.params.variableComponentOrderBy.length !== 0 ||
          this.params.variableComponentFilters.length !== 0
        "
          class="column is-5-desktop is-12-tablet"
      >
        {{ $t("dataTypesManagement.sorted") }} {{ $t("ponctuation.colon") }}
        <b-field group-multiline grouped>
          <b-taglist>
            <div
                v-for="(variableComponent, index) in this.params.variableComponentOrderBy"
                :key="index"
            >
              <b-tag
                  rounded
                  size="is-medium"
                  style="margin-left: 10px; margin-right: 10px; margin-bottom: 10px"
                  type="is-dark"
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
        <b-field group-multiline grouped>
          <b-taglist>
            <div
                v-for="(variableComponent, index) in this.params.variableComponentFilters"
                :key="index"
            >
              <b-tag
                  rounded
                  size="is-medium"
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
            :label="$t('applications.trier')"
            icon-left="sort-amount-down"
            outlined
            type="is-dark"
            @click="showSort = !showSort"
        ></b-button>
      </div>
      <div class="column is-5-desktop is-4-tablet">
        <b-button
            :label="$t('applications.filter')"
            icon-left="filter"
            inverted
            outlined
            type="is-light"
            @click="
              showFilter = !showFilter;
              showAdvancedSearch = false;
          "
        ></b-button>
        <b-tooltip :label="$t('applications.advancedFilter')" position="is-right">
          <b-button
              icon-left="ellipsis-h"
              inverted
              outlined
              type="is-light"
              @click="
            showFilter = false;
            showAdvancedSearch = !showAdvancedSearch;
            "
          ></b-button>
        </b-tooltip>
      </div>
      <div class="column is-2-desktop is-4-tablet">
        <b-button icon-left="redo" outlined type="is-danger" @click="reInit"
        >{{ $t("dataTypesManagement.réinitialiser") }}
          {{ $t("dataTypesManagement.all") }}
        </b-button>
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
    <div
        v-if="showSort"
        class="notification"
        role="search"
        style="background-color: rgba(0, 163, 166, 0.1)"
    >
      <h2>{{ $t("applications.trier") }}</h2>
      <div class="content">
        <div class="columns is-multiline">
          <div class="column is-9-widescreen is-12-desktop">
            <b-tabs
                v-model="activeTab"
                :multiline="true"
                position="is-centered"
                style="text-transform: capitalize; text-decoration: none"
                type="is-boxed"
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
          <div class="column is-3-widescreen is-12-desktop">
            <draggable class="rows">
              <div
                  v-for="(variableComponent, index) in this.params.variableComponentOrderBy"
                  :key="index"
                  :class="variableComponent.order"
                  class="row"
              >
                <div
                    :id="
                    variableComponent.variableComponentKey.variable +
                    variableComponent.variableComponentKey.component
                  "
                    class="control column"
                    style="padding: 6px"
                >
                  <div class="tags has-addons">
                    <span class="tag is-dark grape" style="font-size: 1rem">
                      <b-icon icon="stream" style="transform: rotate(180deg)"></b-icon>
                    </span>
                    <span class="tag is-dark orderLabel" style="font-size: 1rem">
                      {{ variableComponent.variableComponentKey.variable }}
                      {{ $t("ponctuation.colon") }}
                      {{ variableComponent.variableComponentKey.component }}
                      {{ $t("ponctuation.arrow-right") }}
                      {{ variableComponent.order }}
                    </span>
                    <a
                        class="tag is-delete is-dark"
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
                  <b-button expanded icon-left="redo" outlined type="is-danger" @click="clearOrder"
                  >{{ $t("dataTypesManagement.réinitialiser") }}
                    {{ $t("dataTypesManagement.tri") }}
                  </b-button>
                </div>
                <div class="column">
                  <b-button expanded icon-left="check" outlined type="is-dark" @click="initDatatype"
                  >{{ $t("dataTypesManagement.validate") }}
                    {{ $t("dataTypesManagement.tri") }}
                  </b-button>
                </div>
              </div>
            </div>
          </div>
        </div>
      </div>
    </div>
    <div v-if="showFilter" role="search">
      <h2>{{ $t("applications.filter") }}</h2>
      <div class="notification is-flex is-flex-direction-column is-fullwidth">
        <AuthorizationScopesMenu
            v-for="n in authorizationScopesMenusCount"
            :key="n"
            v-model="authorizationDescriptions[n-1]"
            :application="application"
            :authReferences="authReferences"
            class="tile is-3 is-fullwidth"
            style="border: black solid 2px; width: 100%"
            @input="updateAuthorizationDescription((n-1), $event)"
        />
        <b-button icon-right="plus" size="small" @click="addAuthorizationMenus" class="is-primary"/>
        <b-button v-if="authorizationScopesMenusCount>1" icon-right="minus" size="small"
                  class="is-warning"
                  @click="removeAuthorizationMenus"/>
      </div>
    </div>
    <div v-else-if="showAdvancedSearch" class="columns is-multiline">
      <h2>{{ $t("applications.advancedFilter") }}</h2>
      <div
          v-for="(variable, index) in variables"
          :key="variable.id"
          :variable="variable.id"
          class="column is-2-widescreen is-6-desktop is-12-tablet"
      >
        <b-collapse :open="isOpen === index" animation="slide" class="card" @open="isOpen = index">
          <template #trigger="props">
            <div class="card-header" role="button">
              <p class="card-header-title" style="text-transform: capitalize">
                {{ variable.id }}
              </p>
              <a class="card-header-icon">
                <b-icon :icon="props.open ? 'chevron-up' : 'chevron-down'"></b-icon>
              </a>
            </div>
          </template>
          <div class="card-content" style="padding-bottom: 12px; padding-top: 12px">
            <div
                v-for="(component, index) in variableComponents"
                :key="`${index}`"
                :component="component.component"
                :variable="component.variable"
                class="content"
                style="margin-bottom: 10px"
            >
              <b-field v-if="variable.id === component.variable" :label="component.component">
                <b-field v-if="'date' === component.type || 'numeric' === component.type">
                  <CollapsibleInterval
                      :variable-component="component"
                      @setting_interval="addVariableSearch"
                  ></CollapsibleInterval>
                </b-field>
                <b-input
                    v-model="search[component.variable + '_' + component.component]"
                    :placeholder="$t('dataTypeAuthorizations.search')"
                    icon-right="search"
                    size="is-small"
                    type="search"
                    @blur="addVariableSearch(component)"
                ></b-input>
              </b-field>
            </div>
          </div>
        </b-collapse>
      </div>
    </div>
    <b-field>
      <b-switch
          v-model="params.variableComponentFilters.isRegex"
          :false-value="$t('dataTypesManagement.refuse')"
          :true-value="$t('dataTypesManagement.accepted')"
          passive-type="is-dark"
          type="is-primary"
      >{{ $t("ponctuation.regEx") }} {{ params.variableComponentFilters.isRegex }}
      </b-switch>
      <!--        <b-button
                    class="btnRegExp"
                    type="is-dark"
                    size="is-small"
                    @click="testChangeRegEx()"
                    outlined
                >
                  {{ $t("ponctuation.regEx") }}</b-button
                >-->
    </b-field>
    <div class="columns">
      <div class="column is-8-widescreen is-6-desktop">
        {{ $t("dataTypesManagement.filtered") }} {{ $t("ponctuation.colon") }}
        <b-field group-multiline grouped>
          <b-taglist>
            <div
                v-for="(variableComponent, index) in this.params.variableComponentFilters"
                :key="index"
            >
              <b-tag
                  rounded
                  size="is-medium"
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
      <div class="column is-2-widescreen is-3-desktop">
        <b-button expanded icon-left="redo" outlined type="is-danger" @click="clearSearch"
        >{{ $t("dataTypesManagement.réinitialiser") }}
          {{ $t("dataTypesManagement.filtre") }}
        </b-button>
      </div>
      <div class="column is-2-widescreen is-3-desktop">
        <p class="control">
          <b-button expanded icon-left="check" outlined type="is-dark" @click="addSearch"
          >{{ $t("dataTypesManagement.validate") }}
            {{ $t("dataTypesManagement.filtre") }}
          </b-button>
        </p>
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
              <b-button icon-left="redo" type="is-danger" @click="reInit"
              >{{ $t("dataTypesManagement.réinitialiser") }}
              </b-button>
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
                v-for="(component, index) in variableComponents"
                :key="`row_${rowIndex}-${index}`"
                :component="component.component"
                :variable="component.variable"
                style="text-align: center; vertical-align: middle"
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
              <span
                  v-else-if="
                    row[component.variable][component.computedComponent] &&
                    component.checker &&
                    component.checker.pattern
                  "
              >
                  {{ /.{25}(.*$)/.exec(row[component.variable][component.computedComponent])[1] }}
                </span>
              <span v-else>
                  <a
                      v-if="getRefsLinkedToId(row, component)"
                      class="button inTable"
                      @click="getReferenceValues(row, component)"
                  >
                    {{ getDisplay(row, component.variable, component.component) }}
                  </a>
                  <p
                      v-if="
                      !getRefsLinkedToId(row, component) &&
                      row[component.variable][component.component]
                    "
                  >
                    {{ row[component.variable][component.component] }}
                  </p>
                  <p
                      v-else-if="
                      !getRefsLinkedToId(row, component) &&
                      row[component.variable][component.computedComponent]
                    "
                  >
                    {{ row[component.variable][component.computedComponent] }}
                  </p>
                </span>
            </td>
          </tr>
          </tbody>
        </table>
      </div>
      <b-pagination
          v-model="currentPage"
          :aria-current-label="$t('menu.aria-curent-page')"
          :aria-label="$t('menu.aria-pagination')"
          :aria-next-label="$t('menu.aria-next-page')"
          :aria-previous-label="$t('menu.aria-previous-page')"
          :per-page="params.limit"
          :rounded="true"
          :total="totalRows"
          order="is-centered"
          range-after="3"
          range-before="3"
          role="navigation"
          @change="changePage"
      >
      </b-pagination>
      <div class="buttons" style="margin-top: 16px">
        <b-button
            style="margin-bottom: 15px; float: right"
            type="is-primary"
            @click.prevent="downloadResultSearch"
        >{{ $t("referencesManagement.download") }}
        </b-button>
      </div>
    </div>
  </PageView>
</template>

<script>
import {Component, Prop, Vue} from "vue-property-decorator";
import PageView from "@/views/common/PageView.vue";
import AuthorizationScopesMenu from "@/components/datatype/AuthorizationScopesMenu.vue";
import {ApplicationService} from "@/services/rest/ApplicationService";
import {ApplicationResult} from "@/model/ApplicationResult";
import SubMenu, {SubMenuPath} from "@/components/common/SubMenu.vue";
import {DataService} from "@/services/rest/DataService";
import {AlertService} from "@/services/AlertService";
import CollapsibleInterval from "@/components/common/CollapsibleInterval.vue";
import {ReferenceService} from "@/services/rest/ReferenceService";
import {DownloadDatasetQuery} from "@/model/application/DownloadDatasetQuery";
import {VariableComponentFilters} from "@/model/application/VariableComponentFilters";
import {VariableComponentKey} from "@/model/application/VariableComponentKey";
import {IntervalValues} from "@/model/application/IntervalValues";
import {VariableComponentOrderBy} from "@/model/application/VariableComponentOrderBy";
import draggable from "vuedraggable";
import {InternationalisationService} from "@/services/InternationalisationService";
import {LOCAL_STORAGE_LANG} from "@/services/Fetcher";

const authorizationDescriptionsModel = {
  timeScope: {
    from: null,
    to: null
  },
  requiredAuthorizations: []
};
@Component({
  components: {PageView, SubMenu, CollapsibleInterval, AuthorizationScopesMenu, draggable},
})
export default class DataTypeTableView extends Vue {
  @Prop() applicationName;
  @Prop() dataTypeId;
  @Prop() applicationConfiguration;

  applicationService = ApplicationService.INSTANCE;
  dataService = DataService.INSTANCE;
  referenceService = ReferenceService.INSTANCE;
  alertService = AlertService.INSTANCE;
  internationalisationService = InternationalisationService.INSTANCE;
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
    authorizationDescriptions: []
  });
  showDetails = false;
  showSort = false;
  showFilter = false;
  showAdvancedSearch = false;
  controlPanels = null;
  totalRows = -1;
  currentPage = 1;
  variableComponentsListToSort = [];
  search = {};
  refsLinkedTo = {};
  loadedReferences = {};
  currentReferenceDetail = {active: false};
  activeTab = 0;
  isOpen = 0;
  variableSearch = [];
  referenceLineCheckers = [];
  isRegExp = false;

  authorizationScopesMenus = {};
  authorizationScopesMenusCount = 1;

  authorizationDescriptions = [authorizationDescriptionsModel]

  references = [];
  authReferences = {};

  /*  testChangeRegEx() {
    let checkboxes = document.querySelector('.btnRegExp');
    if (this.params.variableComponentFilters.isRegex === true) {
      this.params.variableComponentFilters.isRegex = false;
      checkboxes.classList.remove('active');
    }
    else if (this.params.variableComponentFilters.isRegex === false) {
      this.params.variableComponentFilters.isRegex = true;
      checkboxes[i].classList.add('active');
    } else {
      this.params.variableComponentFilters.isRegex = !this.isRegExp;
      checkboxes[i].classList.add('active');
    }
    console.log(this.params.variableComponentFilters.isRegex);
  }*/

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

  addAuthorizationMenus() {
    this.authorizationScopesMenusCount++;
    for (let i = this.authorizationDescriptions.length; i <= this.authorizationScopesMenusCount; i++) {
      this.authorizationDescriptions.push(authorizationDescriptionsModel)
    }
  }

  removeAuthorizationMenus() {

    this.authorizationScopesMenusCount--;
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
      authorizationDescriptions:[]
    });
    this.authorizationScopesMenusCount = 1
    this.authorizationDescriptions = [authorizationDescriptionsModel]
    this.initDatatype();
  }

  async init() {
    this.application = await this.applicationService.getApplication(this.applicationName, [
      "CONFIGURATION",
      "DATATYPE",
    ]);
    this.application = {
      ...this.application,
      localName: this.internationalisationService.mergeInternationalization(this.application)
          .localName,
      localDatatypeName: this.internationalisationService.localeDataTypeIdName(
          this.application,
          this.application.dataTypes[this.dataTypeId]
      ),
    };
    await this.initDatatype();
  }

  async initDatatype() {
    this.showSort = false;
    const dataTypes = await this.dataService.getDataType(
        this.applicationName,
        this.dataTypeId,
        this.params
    );
    this.referenceLineCheckers = dataTypes.checkedFormatVariableComponents.ReferenceLineChecker;

    this.translations = dataTypes.entitiesTranslations;
    this.data;
    this.refsLinkedTo = dataTypes.rows.reduce((acc, d) => {
      acc[d.rowId] = d.refsLinkedTo;
      return acc;
    }, {});
    this.totalRows = dataTypes.totalRows;
    this.rows = dataTypes.rows.map((r) => {
      return {rowId: r.rowId, ...r.values};
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
                variableComponentKey: {variable: v.id, component: comp},
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
    this.initAuthorizationScopeMenus();
  }

  async getOrLoadReferences(reference) {
    if (!this.references[reference]) {
      this.references[reference] = await this.referenceService.getReferenceValues(
          this.application.name,
          reference
      );
    }
    return this.references[reference];
  }

  updateAuthorizationDescription(index, authorizationDescription) {
    let authorizationDescriptionsList = this.authorizationDescriptions;
    authorizationDescriptionsList[index] = authorizationDescription;
    this.authorizationDescriptions = authorizationDescriptionsList;
  }

  async initAuthorizationScopeMenus() {
    let dataType = this.application.configuration.dataTypes[this.dataTypeId];
    if (dataType?.authorization.authorizationScopes) {
      let ret = {};
      for (let auth in dataType.authorization.authorizationScopes) {
        let vc = dataType.authorization.authorizationScopes[auth];
        let variables = dataType.data[vc.variable];
        var reference = {...variables.components, ...variables.computedComponents}[vc.component]
            .checker.params.refType;
        let ref = await this.getOrLoadReferences(reference);
        ret[auth] = ref;
      }

      let refs = Object.values(ret)
          .reduce(
              (acc, k) => [
                ...acc,
                ...k.referenceValues.reduce(
                    (a, b) => [...a, ...b.hierarchicalReference.split(".")],
                    acc
                ),
              ],
              []
          )
          .reduce((a, b) => {
            if (a.indexOf(b) < 0) {
              a.push(b);
            }
            return a;
          }, []);
      for (const refsKey in refs) {
        await this.getOrLoadReferences(refs[refsKey]);
      }
      for (const [key, value] of Object.entries(ret)) {
        let partition = await this.partitionReferencesValues(value.referenceValues);
        ret[key] = partition;
      }
      this.authReferences = ret;
      let authorizationScopesMenus = {};
      let referenceforAuthorizationScope = Object.values(
          dataType.authorization.authorizationScopes
      ).map(
          (as) =>
              (
                  dataType.data[as.variable]?.components[as.component] ||
                  dataType.data[as.variable]?.computedComponent[as.component]
              ).checker.params.refType
      );
      this.authorizationScopesMenus = {};
      for (let compositeReference of Object.values(
          this.application.configuration.compositeReferences
      )) {
        let components = [];
        for (let component of compositeReference.components) {
          components.push(component);
          if (referenceforAuthorizationScope.includes(component.reference)) {
            authorizationScopesMenus[component.reference] = {
              refTypeToReturn: component.reference,
              components,
            };
            break;
          }
        }
      }
      this.authorizationScopesMenus = authorizationScopesMenus;
    }
  }

  async partitionReferencesValues(referencesValues, currentPath, currentCompleteLocalName) {
    let returnValues = {};
    for (const referenceValue of referencesValues) {
      var previousKeySplit = currentPath ? currentPath.split(".") : [];
      var keys = referenceValue.hierarchicalKey.split(".");
      var references = referenceValue.hierarchicalReference.split(".");
      if (previousKeySplit.length === keys.length) {
        continue;
      }
      for (let i = 0; i < previousKeySplit.length; i++) {
        keys.shift();
        references.shift();
      }
      var key = keys.shift();
      let newCurrentPath = (currentPath ? currentPath + "." : "") + key;
      var reference = references.shift();
      let refValues = await this.getOrLoadReferences(reference);
      this.internationalisationService.getUserPrefLocale();
      let lang = localStorage.getItem(LOCAL_STORAGE_LANG);
      let localName = refValues.referenceValues.find((r) => r.naturalKey == key);
      if (localName?.values?.["__display_" + lang]) {
        localName = localName?.values?.["__display_" + lang];
      } else {
        localName = localName?.naturalKey;
      }
      if (!localName) {
        localName = key;
      }
      var completeLocalName =
          typeof currentCompleteLocalName === "undefined" ? "" : currentCompleteLocalName;
      completeLocalName = completeLocalName + (completeLocalName === "" ? "" : ",") + localName;
      let authPartition = returnValues[key] || {
        key,
        reference,
        referenceValues: [],
        localName,
        isLeaf: false,
        currentPath: newCurrentPath,
        completeLocalName,
      };
      authPartition.referenceValues.push(referenceValue);
      returnValues[key] = authPartition;
    }
    for (const returnValuesKey in returnValues) {
      var auth = returnValues[returnValuesKey];
      let referenceValueLeaf = auth.referenceValues?.[0];
      if (
          auth.referenceValues.length <= 1 &&
          referenceValueLeaf.hierarchicalKey === auth.currentPath
      ) {
        returnValues[returnValuesKey] = {
          ...auth,
          isLeaf: true,
          referenceValues: referenceValueLeaf,
        };
      } else {
        var r = await this.partitionReferencesValues(
            auth.referenceValues,
            auth.currentPath,
            auth.completeLocalName
        );
        returnValues[returnValuesKey] = {
          ...auth,
          isLeaf: false,
          referenceValues: r,
        };
      }
    }
    return returnValues;
  }

  getRefsLinkedToId(row, component) {
    return (
        this.refsLinkedTo[row.rowId][component.variable][component.component] ||
        this.refsLinkedTo[row.rowId][component.variable][component.computedComponent]
    );
  }

  getTranslation(row, component) {
    let translation =
        row[component.variable][component.component] ||
        row[component.variable][component.computedComponent];
    return translation;
  }

  async getReferenceValues(row, component) {
    const rowId = this.getRefsLinkedToId(row, component);
    const refType = component.checker.referenceLineChecker.refType;
    const key = component.key;
    if (!this.loadedReferences[rowId]) {
      let refvalues;
      if (this.referenceLineCheckers[key].referenceValues) {
        refvalues =
            this.referenceLineCheckers[key].referenceValues.refValues.evaluationContext.datum;
      }
      if (!refvalues) {
        let params = {_row_id_: [rowId]};
        if (!refType) {
          params.any = true;
        }
        const reference = await this.referenceService.getReferenceValues(
            this.applicationName,
            refType,
            params
        );
        refvalues = reference.referenceValues[0].values;
      }
      const data = Object.entries(refvalues)
          .map((entry) => ({colonne: entry[0], valeur: entry[1]}))
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
        reference: refType,
      };
      this.loadedReferences = {
        ...this.loadedReferences,
        ...result,
      };
    }
    let referenceValue = this.loadedReferences[rowId];
    this.currentReferenceDetail = {...referenceValue, active: true};
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
    variableComponentSorted.order = variableComponentSorted.order === order ? null : order;
    this.params.variableComponentOrderBy = this.params.variableComponentOrderBy.filter(
        (c) =>
            c.variableComponentKey.variable !== variableComponentSorted.variableComponentKey.variable ||
            c.variableComponentKey.component !== variableComponentSorted.variableComponentKey.component
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
            c.variableComponentKey.variable !== variable ||
            c.variableComponentKey.component !== component
    );
    this.params.variableComponentOrderBy.delete();
    document.getElementById(variable + component).remove();
  }

  getSortIcon(variable, component) {
    variable, component, event;
    let icon = this.params.variableComponentOrderBy
        .filter(
            (c) =>
                c.variableComponentKey.variable === variable &&
                c.variableComponentKey.component === component
        )
        .map((vc) => {
          if (vc.order === "ASC") {
            return "arrow-down";
          } else if (vc.order === "DESC") {
            return "arrow-up";
          } else {
            return "";
          }
        })[0];
    return icon ? icon : null;
  }

  addVariableSearch(variableComponent) {
    let {key, variable, component, type, format} = variableComponent;
    let isRegExp = this.params.variableComponentFilters.isRegex;
    let value = this.search[key];
    this.params.variableComponentFilters = this.params.variableComponentFilters.filter(
        (c) =>
            c.variableComponentKey.variable !== variable ||
            c.variableComponentKey.component !== component
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
    if (this.showAdvancedSearch) {
      this.params.variableComponentFilters = [];
      for (var i = 0; i < this.variableSearch.length; i++) {
        if (this.variableSearch[i]) {
          this.params.variableComponentFilters.push(this.variableSearch[i]);
        }
      }
    } else {
      this.params.authorizationDescriptions = this.authorizationDescriptions.slice(0, this.authorizationScopesMenusCount)
    }
    this.initDatatype();
    this.showFilter = false;
  }

  async downloadResultSearch() {
    if (this.showAdvancedSearch) {
      this.params.variableComponentFilters = [];
      for (var i = 0; i < this.variableSearch.length; i++) {
        if (this.variableSearch[i]) {
          this.params.variableComponentFilters.push(this.variableSearch[i]);
        }
      }
    } else {
      this.params.authorizationDescriptions = this.authorizationDescriptions.slice(0, this.authorizationScopesMenusCount)
    }
    let param = {
      ...this.params,
      offset: 0,
      limit: 42,
      dataType: this.dataTypeId,
      applicationNameOrId: this.applicationName,
    };
    let csv = await this.dataService.getDataTypesCsv(this.applicationName, this.dataTypeId, param);
    var hiddenElement = document.createElement("a");
    hiddenElement.href = "data:text/csv;charset=utf-8," + encodeURI(csv);

    //provide the name for the CSV file to be downloaded
    hiddenElement.download = "export.csv";
    hiddenElement.click();
    return false;
  }

  async clearSearch() {
    if (this.showFilter){
      this.params = new DownloadDatasetQuery({
        application: null,
        applicationNameOrId: this.applicationName,
        dataType: this.dataTypeId,
        offset: 0,
        limit: 15,
        variableComponentSelects: this.params.variableComponentSelects,
        variableComponentFilters: this.params.variableComponentFilters,
        variableComponentOrderBy: this.params.variableComponentOrderBy,
        authorizationDescriptions:[]
      });
      this.authorizationScopesMenusCount = 1
      this.authorizationDescriptions = [authorizationDescriptionsModel]
      this.initDatatype();
    }else {
      for (var i = 0; i < this.variableSearch.length; i++) {
        this.params.variableComponentFilters = [];
        this.variableSearch = [];
      }
    }
    this.initDatatype();
  }

  clearOrder() {
    for (var i = 0; i < this.params.variableComponentOrderBy.length; i++) {
      this.params.variableComponentOrderBy = [];
    }
    this.initDatatype();
  }

  getDisplay(row, variable, component) {
    var key = variable + "_" + component;
    var value = row[variable][component];
    var lang = "__display_" + localStorage.getItem("lang");
    if (this.referenceLineCheckers[key]) {
      if (
          this.referenceLineCheckers[key].referenceValues &&
          this.referenceLineCheckers[key].referenceValues.refValues
      ) {
        var display =
            this.referenceLineCheckers[key].referenceValues.refValues.evaluationContext.datum[lang];
        return display ? display : value;
      }
    }
    return value;
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

.button.is-dark.is-outlined.active {
  background-color: $dark;
  color: #dbdbdb;
}

.ASC .asc,
.DESC .desc {
  background-color: $dark;
  color: white;
}

.numberInput {
  width: 3em;
}

.button.inTable {
  color: $dark;
  background-color: transparent;
  border: transparent;
}

.button.inTable:hover {
  color: $dark;
  background-color: transparent;
  border: transparent;
  text-decoration: underline;
}

.columns {
  margin: 0;
}

.icon.is-small {
  font-size: 5rem;
}
</style>
