<template>
  <div>
    <!-- section pour visualisation une Dynamique colonne-->
    <span v-if="info && showBtnTablDynamicColumn(infoValues)">
      <b-button
        icon-left="eye"
        rounded
        size="is-small"
        style="height: inherit"
        type="is-dark"
        @click="showModal()"
      >
        <b-loading v-model="isLoading" :is-full-page="null"></b-loading>
      </b-button>
    </span>
    <!-- modal de multiplicity et dynamique colonne -->
    <b-modal v-model="isCardModalActive" class="modalCardRef" width="70%">
      <div class="card">
        <div class="card-header">
          <div class="title card-header-title">
            <p field="name" style="font-size: 1.5rem">
              {{ columnTitle }}
            </p>
          </div>
        </div>
        <div class="card-content">
          <div v-for="key in modalArrayObj" :key="key.id" class="columns modalArrayObj">
            <ReferencesLink
              v-if="key.columnName"
              :application="application"
              :reference-type="referenceType"
              :value="key.column"
              :loaded-references-by-key="{}"
              :column-id="key.columnName"
            ></ReferencesLink>
            <ReferencesManyLink
                v-else-if="Array.isArray(key.column)"
                :multiplicity="true"
                :info-values="key.column"
                :application="application"
                :reference-type="referenceType"
                :loaded-references-by-key="{}"
                :column-id="key.columnName"
            ></ReferencesManyLink>
            <div v-else>
              <p class="column">{{ key.column }} {{ $t("ponctuation.colon") }}</p>
            </div>

            <p v-if="key.value" class="column">
              {{ key.value }}
            </p>
          </div>
        </div>
      </div>
    </b-modal>
  </div>
</template>

<script>
import { ReferenceService } from "@/services/rest/ReferenceService";
import { InternationalisationService } from "@/services/InternationalisationService";
import ReferencesLink from "@/components/references/ReferencesLink.vue";
import ReferencesManyLink from "@/components/references/ReferencesManyLink.vue";

export default {
  name: "ReferencesDynamicLink",
  emits: ["changedRefValues"],
  components: {ReferencesManyLink, ReferencesLink},
  props: {
    application: Object,
    referenceType: String,
    value: String,
    infoValues: {},
    referenceValue: {},
    loadedReferencesByKey: {
      type: Object,
    },
    info: Boolean,
    columnId: String,
    // loadedReferencesById: {
    //   type: Object,
    // },
  },
  beforeCreate() {
    this.$options.components.ReferencesLink = require("./ReferencesLink.vue").default;
    this.$options.components.ReferencesManyLink = require("./ReferencesManyLink.vue").default;
  },
  computed: {
    applicationName(){
      return this.application.name;
    },
    columnTitle() {
      let displayRef = this.internationalisationService.localeReferenceName(
          {label: this.referenceType},
          this.application
      )
      return displayRef;
    },
  },
  data() {
    return {
      internationalisationService: InternationalisationService.INSTANCE,
      referenceService: ReferenceService.INSTANCE,
      refValues: { active: false },
      isCardModalActive: false,
      modalArrayObj: {},
      isLoading: false,
      display: "__display_" + window.localStorage.lang,
    };
  },
  watch: {
    innerOptionChecked(value) {
      return this.$emit("optionChecked", value);
    },
  },
  methods: {
    async getReferenceValuesByKey(applicationName, referenceType, value) {
      return this.referenceService.getReferenceValuesByKey(applicationName, referenceType, value);
    },

    showBtnTablDynamicColumn(tablDynamicColumn) {
      let showModal = Object.entries(tablDynamicColumn)
        .filter((a) => a[1])
        .map(function (a) {
          let obj = {};
          obj[a[0]] = a[1];
          return obj;
        });
      return showModal.length !== 0;
    },

    async showModal() {
      this.isLoading = true;
      if (this.currentReferenceDetail?.active) {
        this.currentReferenceDetail.active = false;
      }
      let columnName;
      let modalArrayObj = Object.entries(this.infoValues)
        .filter((a) => a[1])
        .map(function (a) {
          let obj = {};
          obj[a[0]] = a[1];
          return obj;
        });
      for (let value in this.infoValues) {
        const reference = await this.getReferenceValuesByKey(
          this.applicationName,
          this.referenceType,
          value
        );
        columnName = reference.referenceValues[0].values[this.display];
        for (let j = 0; j < modalArrayObj.length; j++) {
          if (Object.keys(modalArrayObj[j])[0] === reference.referenceValues[0].hierarchicalKey) {
            let column = Object.keys(modalArrayObj[j])[0];
            let value = Object.values(modalArrayObj[j])[0];
            modalArrayObj[j] = {
              ...modalArrayObj[j],
              column: column,
              value: value,
              columnName: columnName,
            };
            this.modalArrayObj[j] = modalArrayObj[j];
          }
        }
      }
      this.$emit("changedRefValues", {
        key: this.value,
        refType: this.referenceType,
        // hierarchicalKey,
        modalArrayObj: this.modalArrayObj,
      });
      this.isCardModalActive = true;
      this.isLoading = false;
      return this.modalArrayObj;
    },

    dynamicColumnInfo(refType) {
      console.log(this.reference.dynamicColumns)
      let dynamicColumns = Object.entries(this.reference.dynamicColumns).filter((a) => a[1]);
      //console.log(dynamicColumns)
      for (let i = 0; i < dynamicColumns.length; i++) {
        if (dynamicColumns[i][0] === refType) return true;
      }
      return false;
    }
  },
};
</script>

<style scoped></style>
