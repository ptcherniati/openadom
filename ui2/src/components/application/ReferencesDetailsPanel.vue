<template>
  <SidePanel :open="open" :leftAlign="leftAlign" :title="reference && reference.label">
    <div class="Panel-buttons">
      <b-button type="is-primary" icon-left="eye">{{
        $t("referencesManagement.consult")
      }}</b-button>
      <b-button icon-left="download">{{ $t("referencesManagement.download") }}</b-button>
      <b-button type="is-danger" icon-left="trash-alt" @click="askDeletionConfirmation">{{
        $t("referencesManagement.delete")
      }}</b-button>
    </div>
  </SidePanel>
</template>

<script>
import { AlertService } from "@/services/AlertService";
import { Component, Prop, Vue } from "vue-property-decorator";
import SidePanel from "../common/SidePanel.vue";

@Component({
  components: { SidePanel },
})
export default class ReferencesDetailsPanel extends Vue {
  @Prop({ default: false }) leftAlign;
  @Prop({ default: false }) open;
  @Prop() reference;

  alertService = AlertService.INSTANCE;

  askDeletionConfirmation() {
    this.alertService.dialog(
      this.$t("alert.warning"),
      this.$t("alert.reference-deletion-msg", { label: this.reference.label }),
      this.$t("alert.delete"),
      "is-danger",
      () => this.deleteReference()
    );
  }

  deleteReference() {
    console.log("DELETE", this.reference);
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
