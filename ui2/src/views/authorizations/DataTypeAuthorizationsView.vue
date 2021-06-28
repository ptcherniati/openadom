<template>
  <PageView class="with-submenu">
    <h1 class="title main-title">
      {{ dataTypeId }}
    </h1>
  </PageView>
</template>

<script>
import { Component, Prop, Vue } from "vue-property-decorator";
import PageView from "../common/PageView.vue";

@Component({
  components: { PageView },
})
export default class DataTypeAuthorizationsView extends Vue {
  @Prop() dataTypeId;
  @Prop() applicationName;
}
</script>
