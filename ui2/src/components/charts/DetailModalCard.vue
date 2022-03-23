<template>
  <ModalCard
    v-show="open"
    :title="dataType && (dataType.localName || dataType.label)"
    :closeCb="closeCb"
  >
    <div class="modal-card" style="width: auto; height: auto">
      <div v-if="options && options.synthesis">
        <b-collapse
          class="card"
          :aria-id="key"
          v-for="(option, key) in options.synthesis"
          :key="key"
          style="width: auto"
          :open="open"
          :id="'collapse.' + key"
        >
          <template #trigger="props">
            <div
              class="card-header columns"
              v-show="key !== 'minmax' && key !== 'ranges'"
              style="margin: 0; cursor: auto"
            >
              <p class="card-header-title column is-1" style="margin-left: 10px">
                {{ key }}
              </p>
              <availiblity-chart
                class="column is-10"
                :minmax="option.minmax"
                :ranges="option.ranges"
                :id="key"
                style="padding-bottom: 0"
              />
              <a
                class="card-header-icon column is-1"
                :aria-controls="key"
                role="button"
                v-if="Object.values(option).length > 4"
                style="text-align: center"
              >
                <b-icon class="btnCard" :icon="props.open ? 'chevron-down' : 'chevron-up'"> </b-icon>
              </a>
            </div>
          </template>
          <div
            class="card-content"
            v-for="(option2, key2) in Object.values(option)"
            :key="key2"
            v-show="option2.aggregation"
          >
            <b-field class="columns" v-if="option2.aggregation">
              <div class="column is-1">{{ option2.aggregation }}</div>
              <availiblity-chart
                class="column is-10"
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
  collapse = true;
  mounted() {
    for(let i = 1; i<=document.getElementsByClassName("btnCard").length-1; i++) {
      document.getElementsByClassName("btnCard").item(i).click();
    }
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
.modal-dialog-scrollable {
  .modal-card {
    width: 90%;
    margin-bottom: 10px;
    overflow-y: initial !important;
    .card-content {
      height: auto;
      overflow-y: auto;
      padding: 15px;
    }
  }
  .card-header-title div {
    width: 90%;
  }
}
.animation-content.modal-content {
  max-width: 1400px;
}
</style>
