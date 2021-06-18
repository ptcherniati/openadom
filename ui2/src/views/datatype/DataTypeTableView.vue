<template>
  <PageView>
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

@Component({
  components: { PageView },
})
export default class DataTypeTableView extends Vue {
  @Prop() applicationName;
  @Prop() dataTypeId;
}
</script>

<style lang="scss" scoped>
.DataSetTableView-wrapper {
  margin-bottom: 1.5rem;
}
</style>
