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
              <th colspan="2">Variable 1</th>
            </tr>
            <tr>
              <th colspan="1">Composant 1</th>
              <th colspan="1">Composant 2</th>
            </tr>
          </thead>
          <tbody>
            <tr>
              <td>Donnée 1.a</td>
              <td>Donnée 1.b</td>
            </tr>
            <tr>
              <td>Donnée 2.a</td>
              <td>Donnée 2.b</td>
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

@Component({
  components: { PageView, SubMenu },
})
export default class DataTypeTableView extends Vue {
  @Prop() applicationName;
  @Prop() dataTypeId;

  applicationService = ApplicationService.INSTANCE;

  application = new ApplicationResult();
  subMenuPaths = [];

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
    try {
      this.application = await this.applicationService.getApplication(this.applicationName);
    } catch (error) {
      this.alertService.toastServerError();
    }
  }
}
</script>

<style lang="scss" scoped>
.DataSetTableView-wrapper {
  margin-bottom: 1.5rem;
}
</style>
