<template>
  <SidePanel
      :close-cb="closeCb"
      :left-align="leftAlign"
      :open="open"
      :title="reference && (reference.refNameLocal || reference.label)"
  >
    <div class="columns">
      <caption>
        {{ $t('tags.tag') }} {{ $t('ponctuation.colon')}}
      </caption>
      <div v-for="(tag) in reference.tags" :key="tag" style="margin: 5px">
        <b-tag class="is-primary" v-if="tags[tag].localName !== 'no-tag'">
          <span>
            {{ tags[tag].localName }}
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
    <div class="Panel-buttons">
      <b-button type="is-dark" icon-left="key" @click="consultAuthorization">{{
        $t("dataTypesManagement.consult-authorization")
      }}</b-button>
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
  @Prop() applicationName;
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

  consultAuthorization() {
    this.$router.push(
        `/applications/${this.applicationName}/references/authorizations`
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