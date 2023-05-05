<template>
  <div>
    <!-- section pour visualisation un MANY-->
    <span v-if="multiplicity">
      <div v-for="val in infoValues" :key="val.id">
        <ReferencesLink
          v-if="referenceType"
          :application="application"
          :column-id="val.columnName"
          :loaded-references-by-key="{}"
          :reference-type="referenceType"
          :value="val"
        ></ReferencesLink>
        <p v-else-if="val.indexOf('date:') !== -1">{{ /.{25}(.*$)/.exec(val)[1] }}</p>
        <p v-else class="column">{{ val }}</p>
      </div>
    </span>
    <!-- modal de multiplicity et dynamique colonne -->
    <b-modal v-model="isCardModalActive" class="modalCardRef" width="70%">
      <div class="card">
        <div class="card-header">
          <div class="title card-header-title">
            <p field="name" style="font-size: 1.5rem">
              {{ columnId }}
            </p>
          </div>
        </div>
        <div class="card-content">
          <div v-for="key in modalArrayObj" :key="key.id" class="columns modalArrayObj">
            <ReferencesLink
              v-if="key.value"
              :application="application"
              :column-id="key.columnName"
              :loaded-references-by-key="{}"
              :reference-type="referenceType"
              :value="key.value"
            ></ReferencesLink>
            <ReferencesDynamicLink
              v-if="!key.value.length && key.value.length !== 0"
              :info="!key.value.length && key.value.length !== 0"
              :info-values="key.value"
              :application="application"
              :reference-type="dynamicColumnReferences(key.value)"
              :loaded-references-by-key="{}"
              :column-id="key.value"
            ></ReferencesDynamicLink>
          </div>
        </div>
      </div>
    </b-modal>
  </div>
</template>

<script>
import { ReferenceService } from "@/services/rest/ReferenceService";
import ReferencesLink from "@/components/references/ReferencesLink.vue";
import ReferencesDynamicLink from "@/components/references/ReferencesDynamicLink.vue";

export default {
  name: "ReferencesManyLink",
  emits: ["changedRefValues"],
  components: { ReferencesLink, ReferencesDynamicLink },
  props: {
    application: Object,
    referenceType: String,
    value: String,
    infoValues: {},
    referenceValue: {},
    loadedReferencesByKey: {
      type: Object,
    },
    multiplicity: Boolean,
    columnId: String,
    // loadedReferencesById: {
    //   type: Object,
    // },
  },
  beforeCreate() {
    this.$options.components.ReferencesDynamicLink = require("./ReferencesDynamicLink.vue").default;
    this.$options.components.ReferencesLink = require("./ReferencesLink.vue").default;
  },
  computed: {
    applicationName() {
      return this.application.name;
    },
    columnTitle() {
      let displayRef = this.internationalisationService.localeReferenceName(
        { label: this.referenceType },
        this.application
      );
      return displayRef;
    },
  },
  data() {
    return {
      referenceService: ReferenceService.INSTANCE,
      refValues: { active: false },
      isCardModalActive: false,
      modalArrayObj: {},
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
    dynamicColumnReferences(nameId) {
      return this.application.references[this.referenceType].dynamicColumns[nameId].reference;
    },
    async openReferenceDetail() {
      this.isCardModalActive = false;
      let refValues =
        this.refValues &&
        this.loadedReferencesByKey[this.referenceType] &&
        this.loadedReferences[this.referenceType][this.value];
      if (!refValues) {
        const reference = await this.getReferenceValuesByKey(
          this.applicationName,
          this.referenceType,
          this.value
        );
        let referenceTypeForReferencingColumns = reference.referenceTypeForReferencingColumns;
        let refValue = reference.referenceValues[0].values;
        if (referenceTypeForReferencingColumns.length) {
          refValues = {
            referenceTypeForReferencingColumns: referenceTypeForReferencingColumns,
            refValue: refValue,
          };
        } else {
          refValues = { refValue: refValue };
        }
      }
      const data = Object.entries(refValues.refValue)
        .map((entry) => ({ colonne: entry[0], valeur: entry[1] }))
        .reduce((acc, entry) => {
          acc.push(entry);
          return acc;
        }, []);
      const result = {
        data: data,
        columns: [
          {
            field: "colonne",
            label: "Colonne",
          },
          {
            field: "valeur",
            label: "Valeur",
          },
        ],
        active: true,
        reference: this.referenceType,
      };
      this.refValues = { ...refValues, ...result };
      console.log(this.refValues);
      this.$emit("changedRefValues", {
        key: this.value,
        refType: this.referenceType,
        // hierarchicalKey,
        refValues,
      });
      return refValues;
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
    showModal() {
      this.isCardModalActive = true;
      if (this.currentReferenceDetail?.active) {
        this.currentReferenceDetail.active = false;
      }
      this.modalArrayObj = Object.entries(this.infoValues)
        .filter((a) => a[1])
        .map(function (a) {
          let obj = {};
          obj[a[0]] = a[1];
          return obj;
        });
      for (let j = 0; j < this.modalArrayObj.length; j++) {
        if (this.modalArrayObj[j]) {
          let column = Object.keys(this.modalArrayObj[j])[0];
          let value = Object.values(this.modalArrayObj[j])[0];
          this.modalArrayObj[j] = { ...this.modalArrayObj[j], column: column, value: value };
        }
      }
      console.log(this.modalArrayObj);
      /*return this.modalArrayObj;*/
    },
  },
};
</script>

<style scoped></style>
