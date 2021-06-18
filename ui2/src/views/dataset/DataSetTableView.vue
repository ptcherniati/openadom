<template>
  <PageView>
    <h1 class="title main-title">{{ dataSetId }}</h1>
  </PageView>
</template>

<script>
import { Component, Prop, Vue } from "vue-property-decorator";
import PageView from "@/views/common/PageView.vue";

@Component({
  components: { PageView },
})
export default class DataSetTableView extends Vue {
  @Prop() applicationName;
  @Prop() dataSetId;
}
</script>
