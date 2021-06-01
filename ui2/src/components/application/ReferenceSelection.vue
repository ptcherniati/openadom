<template>
  <div>
    <b-select
      v-model="chosenReferenceType"
      :placeholder="$t('referenceSelection.placeholder')"
      @input="getReference"
      expanded
      v-if="application"
    >
      <option v-for="type in application.referenceType" :key="type" :value="type">
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
export default class ReferenceSelection extends Vue {
  @Prop() application;
  applicationService = ApplicationService.INSTANCE;

  chosenReferenceType = null;
  reference = null;

  async getReference() {
    try {
      this.reference = await this.applicationService.getReference(
        this.chosenReferenceType,
        this.application.name
      );
    } catch (error) {
      this.alertService.toastServerError();
    }
  }
}
</script>
