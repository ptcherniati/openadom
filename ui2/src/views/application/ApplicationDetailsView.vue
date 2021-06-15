<template>
  <PageView>
    <h1 class="title main-title">{{ application.title }}</h1>
    <b-tabs
      v-model="currentTabIndex"
      type="is-boxed"
      expanded
      class="mt-4 ApplicationDetailsView-tabs"
      :animated="false"
      @input="onTabSelection"
    >
      <b-tab-item :label="$t('applicationDetailsView.references')" icon="drafting-compass">
        <ReferencesManagement :application="application" :key="application.id" />
      </b-tab-item>
      <b-tab-item :label="$t('applicationDetailsView.dataset')" icon="poll">À venir</b-tab-item>
    </b-tabs>
  </PageView>
</template>

<script>
import { Component, Prop, Vue, Watch } from "vue-property-decorator";
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
  @Prop() tabIndex;

  applicationService = ApplicationService.INSTANCE;
  alertService = AlertService.INSTANCE;
  application = new ApplicationResult();

  currentTabIndex = 0;

  created() {
    this.currentTabIndex = parseInt(this.tabIndex);
    this.init();
  }

  async init() {
    try {
      this.application = await this.applicationService.getApplication(this.applicationName);
    } catch (error) {
      this.alertService.toastServerError();
    }
  }

  @Watch("tabIndex")
  onExternalTabIndexChanged(newVal) {
    this.currentTabIndex = parseInt(newVal);
  }

  onTabSelection() {
    const params = this.$router.currentRoute.params;
    if (this.currentTabIndex.toString() !== params["tabIndex"]) {
      this.$router.push({ params: { tabIndex: this.currentTabIndex } });
    }
  }
}
</script>

<style lang="scss">
.ApplicationDetailsView-tabs.b-tabs {
  .tab-content {
    position: initial;
  }
}
</style>
