<template>
  <div>
    <b-select
      v-model="chosenDataType"
      :placeholder="$t('datasetSelection.placeholder')"
      @input="loadDataset"
      expanded
      v-if="application"
    >
      <option v-for="type in application.dataType" :key="type" :value="type">
        {{ type }}
      </option>
    </b-select>
  </div>
</template>

<script>
import { ApplicationService } from "@/services/rest/ApplicationService";
import { Component, Prop, Vue } from "vue-property-decorator";

@Component({
  components: {},
})
export default class DatasetSelection extends Vue {
  @Prop() application;

  applicationService = ApplicationService.INSTANCE;

  chosenDataType = null;
  dataset = null;

  async loadDataset() {
    try {
      this.dataset = await this.applicationService.getDataset(
        this.chosenDataType,
        this.application.name
      );
    } catch (error) {
      this.alertService.toastServerError();
    }
  }
}
</script>
