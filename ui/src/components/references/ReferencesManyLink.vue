<template>
  <div>
    <!-- section pour visualisation un MANY-->
    <span v-if="multiplicity">
      <b-button
        v-if="referenceType"
        icon-left="eye"
        rounded
        size="is-small"
        style="height: inherit"
        type="is-dark"
        @click="showModal(columnId, infoValues)"
      />
      <div v-else>
        <p v-for="val in infoValues" :key="val.id">{{ val }}</p>
      </div>
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
            <a v-if="key.column" class="column" @click="openReferenceDetail()">
              {{ key.column }} {{ $t("ponctuation.colon") }}
            </a>
            <p v-else class="column">{{ key.column }} {{ $t("ponctuation.colon") }}</p>
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

export default {
  name: "ReferencesManyLink",
  emits: ["changedRefValues"],
  components: {},
  props: {
    applicationName: String,
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
    columnTitle: String,
    // loadedReferencesById: {
    //   type: Object,
    // },
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
          this.modalArrayObj[j] = { ...this.modalArrayObj[j], column: column, value: value };
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
