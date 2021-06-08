<template>
  <PageView>
    <h1 class="title main-title">{{ application.title }}</h1>
    <b-tabs type="is-boxed" expanded class="mt-4">
      <b-tab-item :label="$t('applicationDetailsView.application')" icon="wrench"> </b-tab-item>
      <b-tab-item :label="$t('applicationDetailsView.references')" icon="drafting-compass">
        <ReferencesManagement :application="application" />
      </b-tab-item>
      <b-tab-item :label="$t('applicationDetailsView.dataset')" icon="poll"> </b-tab-item>
    </b-tabs>
  </PageView>
</template>

<script>
import { Component, Prop, Vue } from "vue-property-decorator";
import PageView from "@/views/common/PageView.vue";
import { ApplicationResult } from "@/model/ApplicationResult";
import { ApplicationService } from "@/services/rest/ApplicationService";
import { AlertService } from "@/services/AlertService";
import ReferencesManagement from "@/components/application/ReferencesManagement.vue";

@Component({
  components: { PageView, ReferencesManagement },
})
export default class ApplicationDetailsView extends Vue {
  @Prop() applicationName;

  applicationService = ApplicationService.INSTANCE;
  alertService = AlertService.INSTANCE;
  application = new ApplicationResult();

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
