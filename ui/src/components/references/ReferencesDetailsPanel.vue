<template>
  <SidePanel
      :close-cb="closeCb"
      :left-align="leftAlign"
      :open="open"
      :title="reference && (reference.refNameLocal || reference.label)"
  >
    <div v-if="tags" class="columns">
      <h3 class="column">{{ $t('tags.tag') }}</h3>
      <div class="column">
        <b-tag v-for="(tag) in reference.tags" :key="tag" class="is-dark">
          <span>
            {{(tags[tag].localName === 'no-tag' ? $t('tags.no-tag') : tags[tag] && tags[tag].localName) || tag}}
          </span>
        </b-tag>
      </div>
    </div>
    <div class="Panel-buttons">
      <b-button icon-left="trash-alt" type="is-danger" @click="askDeletionConfirmation">{{
          $t("referencesManagement.delete")
        }}
      </b-button>
    </div>
  </SidePanel>
</template>

<script>
import {AlertService} from "@/services/AlertService";
import {Component, Prop, Vue} from "vue-property-decorator";
import SidePanel from "../common/SidePanel.vue";

@Component({
  components: {SidePanel},
})
export default class ReferencesDetailsPanel extends Vue {
  @Prop({default: false}) leftAlign;
  @Prop({default: false}) open;
  @Prop() reference;
  @Prop() closeCb;
  @Prop() tags;

  alertService = AlertService.INSTANCE;

  askDeletionConfirmation() {
    this.alertService.dialog(
        this.$t("alert.warning"),
        this.$t("alert.reference-deletion-msg", {label: this.reference.label}),
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