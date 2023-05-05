<template>
  <SidePanel
    :close-cb="closeCb"
    :left-align="leftAlign"
    :open="open"
    :title="reference && (reference.refNameLocal || reference.label)"
  >
    <div class="columns">
      <caption>
        {{
          $t("tags.tag")
        }}
        {{
          $t("ponctuation.colon")
        }}
      </caption>
      <div v-for="tag in reference.tags" :key="tag" style="margin: 5px">
        <b-tag class="is-primary" v-if="tags[tag].localName !== 'no-tag'">
          <span>
            {{ tags[tag].localName }}
          </span>
        </b-tag>
      </div>
    </div>
    <div class="Panel-buttons">
      <b-button
        icon-left="trash-alt"
        type="is-danger"
        @click="askDeletionConfirmation"
        :disabled="reference && !reference.canDelete"
        >{{ $t("referencesManagement.delete") }}
      </b-button>
    </div>
    <div class="Panel-buttons">
      <b-button
        type="is-dark"
        icon-left="key"
        @click="consultAuthorization"
        :disabled="reference && !reference.isAdmin"
        >{{ $t("dataTypesManagement.consult-authorization") }}</b-button
      >
    </div>
  </SidePanel>
</template>

<script>
import { AlertService } from "@/services/AlertService";
import { Component, Prop, Vue } from "vue-property-decorator";
import SidePanel from "../common/SidePanel.vue";
import { ReferenceService } from "@/services/rest/ReferenceService";
import { HttpStatusCodes } from "@/utils/HttpUtils";

@Component({
  components: { SidePanel },
})
export default class ReferencesDetailsPanel extends Vue {
  @Prop({ default: false }) leftAlign;
  @Prop({ default: false }) open;
  @Prop() reference;
  @Prop() applicationName;
  @Prop() closeCb;
  @Prop() tags;

  alertService = AlertService.INSTANCE;
  referenceService = ReferenceService.INSTANCE;

  askDeletionConfirmation() {
    this.alertService.dialog(
      this.$t("alert.warning"),
      this.$t("alert.purge-reference-msg", { label: this.reference.localName ? this.reference.localName : this.reference.label }),
      this.$t("alert.delete"),
      "is-danger",
      this.$t("alert.cancel"),
      () => this.deleteReference()
    );
  }

  async deleteReference() {
    console.log("DELETE", this.reference);
    try {
      await this.referenceService.deleteReference(this.applicationName, this.reference.label);
      this.alertService.toastSuccess(this.$t("alert.reference-updated"));
    } catch (errors) {
      await this.checkMessageErrors(errors);
    }
  }

  async checkMessageErrors(errors) {
    if (errors.httpResponseCode === HttpStatusCodes.BAD_REQUEST) {
      errors.content.then((value) => {
        for (let i = 0; i < value.length; i++) {
          this.errorsList[i] = value[i];
        }
        if (this.errorsList.length !== 0) {
          this.errorsMessages = this.errorsService.getCsvErrorsMessages(this.errorsList);
        } else {
          this.errorsMessages = this.errorsService.getErrorsMessages(errors);
        }
      });
    } else {
      this.alertService.toastError(this.$t("alert.delete-reference-error"), errors);
    }
  }

  consultAuthorization() {
    this.$router.push(`/applications/${this.applicationName}/references/authorizations`);
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
