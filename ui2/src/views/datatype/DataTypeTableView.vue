<template>
  <PageView class="with-submenu">
    <SubMenu :root="application.title" :paths="subMenuPaths" />

    <h1 class="title main-title">{{ dataTypeId }}</h1>

    <div class="b-table">
      <div
        class="DataSetTableView-wrapper table-wrapper has-mobile-cards has-sticky-header"
        style="height: 100%"
      >
        <table class="table is-striped">
          <thead>
            <tr>
              <th
                v-for="variable in variables"
                :key="variable.id"
                :colspan="Object.values(variable.components).length"
              >
                {{ variable.label }}
              </th>
            </tr>
            <tr>
              <th v-for="(comp, index) in variableComponents" :key="`${comp.label}-${index}`">
                {{ comp.label }}
              </th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="(row, rowIndex) in rows" :key="`row_${rowIndex}`">
              <td
                v-for="(component, index) in variableComponents"
                :key="`row_${rowIndex}-${index}`"
              >
                {{ row[variables[getVariableIndex(index)].id][component.id] }}
              </td>
            </tr>
          </tbody>
        </table>
      </div>
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
    const dataTypes = await this.dataService.getDataType(this.applicationName, this.dataTypeId);

    this.rows = dataTypes.rows.map((r) => {
      return { ...r.values };
    });

    const variablesModels = this.application.dataTypes[this.dataTypeId].variables;
    this.variables = dataTypes.variables.map((v) => variablesModels[v]);
    this.variableComponents = this.variables
      .map((v) => {
        return Object.values(v.components);
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
}
</script>

<style lang="scss" scoped>
.DataSetTableView-wrapper {
  margin-bottom: 1.5rem;
}
</style>
