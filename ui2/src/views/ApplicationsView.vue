<template>
  <div>
    <PageView>
      <h1 class="title main-title">{{ $t("titles.applications-page") }}</h1>
      <UploadApplication />
    </PageView>
  </div>
</template>

<script>
import UploadApplication from "@/components/applications/UploadApplication.vue";
import { Component, Vue } from "vue-property-decorator";
import PageView from "./common/PageView.vue";

@Component({
  components: { PageView, UploadApplication },
})
export default class ApplicationsView extends Vue {}
</script>
