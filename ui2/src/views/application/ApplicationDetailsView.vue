<template>
  <PageView> </PageView>
</template>

<script>
import { Component, Vue } from "vue-property-decorator";
import PageView from "@/views/common/PageView.vue";

@Component({
  components: { PageView },
})
export default class ApplicationDetailsView extends Vue {}
</script>
