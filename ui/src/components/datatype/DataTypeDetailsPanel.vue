<template>
  <SidePanel
    :open="open"
    :left-align="leftAlign"
    :title="dataType && (dataType.localName || dataType.label)"
    :close-cb="closeCb"
  >
    <div class="columns">
      <caption>
        {{ $t('tags.tag') }} {{ $t('ponctuation.colon')}}
      </caption>
      <div v-for="(tag) in dataType.tags" :key="tag" style="margin: 5px">
        <b-tag class="is-primary">
          <span>
            {{(tags[tag].localName === 'no-tag' ? $t('tags.no-tag') : tags[tag] && tags[tag].localName) || tag}}
          </span>
        </b-tag>
      </div>
    </div>
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
  @Prop() tags;

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
