<template>
  <ModalCard
    v-show="open"
    :title="dataType && (dataType.localName || dataType.label)"
    :closeCb="closeCb"
  >
    <div class="modal-card" style="width: auto">
      <div v-if="options && options.synthesis">
        <b-collapse
          class="card"
          animation="slide"
          :aria-id="key"
          v-for="(option, key) in options.synthesis"
          :key="key"
          style="width: auto"
        >
          <template #trigger="props">
            <div class="card-header" v-show="key != 'minmax' && key != 'ranges'">
              <p class="card-header-title">
                {{ key }}
                <availiblity-chart :minmax="option.minmax" :ranges="option.ranges" :id="key" />
              </p>
              <a class="card-header-icon" :aria-controls="key" role="button">
                <b-icon :icon="props.open ? 'menu-down' : 'menu-up'"> </b-icon>
              </a>
            </div>
          </template>
          <div
            class="card-content"
            v-for="(option2, key2) in Object.values(option)"
            :key="key2"
            v-show="option2.aggregation"
          >
            <b-field v-if="option2.aggregation">
              {{ option2.aggregation }}
              <availiblity-chart
                :minmax="option2.minmax"
                :ranges="
                  option2.ranges.reduce((acc, r) => {
                    acc.push(r.range);
                    return acc;
                  }, [])
                "
                :id="key2"
              />
            </b-field>
          </div>
        </b-collapse>
      </div>
    </div>
  </ModalCard>
</template>

<script>
import { Component, Prop, Vue } from "vue-property-decorator";
import AvailiblityChart from "../charts/AvailiblityChart.vue";
import ModalCard from "@/components/charts/ModalCard";

@Component({
  components: { ModalCard, AvailiblityChart },
})
export default class DetailModalCard extends Vue {
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
.modal-overlay {
  .modal-card {
    width: 90%;
  }
  .card-header-title div {
    width: 90%;
  }
}
.animation-content.modal-content {
  max-width: 1400px;
}
</style>
