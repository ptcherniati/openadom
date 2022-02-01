<template>
  <SidePanel
      :open="open"
      :leftAlign="leftAlign"
      :title="dataType && (dataType.localName || dataType.label)"
      :closeCb="closeCb"
  >
    <div v-if="options && options.synthesis">
      <div class="panel-synthesis-detail" v-for="(option, key) in options.synthesis" :key="key">
        <b-field v-show="key!='minmax' && key!='ranges'">
          {{key}}
          <availiblity-chart
            :minmax = "option.minmax"
            :ranges="option.ranges"
            :id="key"/>
        </b-field>
        <div v-for="(option2, key2) in Object.values(option)" :key="key2">
          <b-field v-if="option2.aggregation">
            {{option2.aggregation}}
            <availiblity-chart
                :minmax = "option2.minmax"
                :ranges="option2.ranges.reduce((acc,r)=>{acc.push(r.range); return acc;},[])"
                :id="key2"/>
          </b-field>
        </div>
      </div>
    </div>
  </SidePanel>
</template>

<script>
import { Component, Prop, Vue } from "vue-property-decorator";
import SidePanel from "../charts/SidePanel.vue";
import AvailiblityChart from "../charts/AvailiblityChart.vue";

@Component({
  components: { SidePanel, AvailiblityChart},
})
export default class SynthesisDetailPanel extends Vue {
  @Prop({ default: false }) leftAlign;
  @Prop({ default: false }) open;
  @Prop() dataType;
  @Prop({ default: false }) options;
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
.panel-synthesis-detail {
  display: flex;
  flex-direction: column;

  .button {
    margin-bottom: 0.5rem;
  }
}
</style>