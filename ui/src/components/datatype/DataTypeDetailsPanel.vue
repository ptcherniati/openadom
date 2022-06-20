<template>
  <SidePanel
    :open="open"
    :left-align="leftAlign"
    :title="dataType && (dataType.localName || dataType.label)"
    :close-cb="closeCb"
  >
    <div class="Panel-buttons">
      <b-button type="is-dark" icon-left="key" @click="consultAuthorization">{{
        $t("dataTypesManagement.consult-authorization")
      }}</b-button>
    </div>
  </SidePanel>
</template>

<script>
import { Component, Prop, Vue } from "vue-property-decorator";
import SidePanel from "../common/SidePanel.vue";

@Component({
  components: { SidePanel },
})
export default class DataTypeDetailsPanel extends Vue {
  @Prop({ default: false }) leftAlign;
  @Prop({ default: false }) open;
  @Prop() dataType;
  @Prop() closeCb;
  @Prop() applicationName;

  consultAuthorization() {
    this.$router.push(
      `/applications/${this.applicationName}/dataTypes/${this.dataType.id}/authorizations`
    );
  }
}
</script>

<style lang="scss" scoped>
.Panel-buttons {
  display: flex;
  flex-direction: column;

  .button {
    margin-bottom: 0.5rem;
  }
}
</style>
