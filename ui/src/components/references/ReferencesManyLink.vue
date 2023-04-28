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
          </div>
        </div>
      </div>
    </b-modal>
  </div>
</template>

<script>
import {ReferenceService} from "@/services/rest/ReferenceService";
import ReferencesLink from "@/components/references/ReferencesLink.vue";

export default {
  name: "ReferencesManyLink",
  emits: ["changedRefValues"],
  components: {ReferencesLink},
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
    this.$options.components.ReferencesLink = require("./ReferencesLink.vue").default;
  },
  computed: {
    applicationName() {
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
      referenceService: ReferenceService.INSTANCE,
      refValues: {active: false},
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
          refValues = {refValue: refValue};
        }
      }
      const data = Object.entries(refValues.refValue)
          .map((entry) => ({colonne: entry[0], valeur: entry[1]}))
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
      this.refValues = {...refValues, ...result};
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
    showModal(/*columName, tablDynamicColumn*/) {
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
          this.modalArrayObj[j] = {...this.modalArrayObj[j], column: column, value: value};
        }
      }
      console.log(this.modalArrayObj);
      /*if (this.referencesDynamic) {
        for (let i = 0; i < this.referencesDynamic.referenceValues.length; i++) {
          let hierarchicalKey = this.referencesDynamic.referenceValues[i].hierarchicalKey;
          for (let j = 0; j < this.modalArrayObj.length; j++) {
            if (this.modalArrayObj[j][hierarchicalKey]) {
              let column = this.referencesDynamic.referenceValues[i].values[this.display]
                  ? this.referencesDynamic.referenceValues[i].values[this.display]
                  : hierarchicalKey;
              let value = this.modalArrayObj[j][hierarchicalKey];
              this.modalArrayObj[j] = {...this.modalArrayObj[j], column: column, value: value};
            }
          }
          for (let j = 0; j < tablDynamicColumn.length; j++) {
            if (tablDynamicColumn[j] === hierarchicalKey) {
              let column = this.referencesDynamic.referenceValues[i].values[this.display]
                  ? this.referencesDynamic.referenceValues[i].values[this.display]
                  : columName;
              this.modalArrayObj[j] = {...this.modalArrayObj[j], column: column, value: hierarchicalKey};
              /!*this.paramsForMany = { ...this.paramsForMany, row_id_:hierarchicalKey }
              console.log(this.paramsForMany)
              const reference = await this.referenceService.getReferenceValues(
                  this.applicationName,
                  columName,
                  this.paramsForMany
              );
              console.log(reference)*!/
            }
          }
        }
        return this.modalArrayObj;
      }*/
      /*return this.modalArrayObj;*/
    },
  },
};
</script>

<style scoped></style>
