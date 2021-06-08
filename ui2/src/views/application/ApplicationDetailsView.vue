<template>
  <PageView>
    <h1 class="title main-title">{{ applicationName }}</h1>
    <b-tabs type="is-boxed" expanded class="mt-4">
      <b-tab-item :label="$t('applicationDetailsView.application')" icon="wrench"> </b-tab-item>
      <b-tab-item :label="$t('applicationDetailsView.references')" icon="drafting-compass">
      </b-tab-item>
      <b-tab-item :label="$t('applicationDetailsView.dataset')" icon="poll"> </b-tab-item>
    </b-tabs>
  </PageView>
</template>

<script>
import { Component, Prop, Vue } from "vue-property-decorator";
import PageView from "@/views/common/PageView.vue";
import { Application } from "@/model/Application";
import { ApplicationService } from "@/services/rest/ApplicationService";
import { AlertService } from "@/services/AlertService";

@Component({
  components: { PageView },
})
export default class ApplicationDetailsView extends Vue {
  @Prop() applicationName;

  applicationService = ApplicationService.INSTANCE;
  alertService = AlertService.INSTANCE;
  application = new Application();

  created() {
    this.init();
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
