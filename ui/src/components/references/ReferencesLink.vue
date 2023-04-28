<template>
  <div>
    <!-- section pour visualisation un lien de référence -->
    <a v-if="referenceType" @click="openReferenceDetail()">{{ columnId ? columnId : value }}</a>
    <p v-else>{{ value }}</p>

    <!-- modal de visualisation d'une donnée de référence -->
    <b-modal v-model="refValues.active" custom-class="referenceDetails">
      <div class="card">
        <header class="card-header is-align-content-center">
          <p class="card-header-title" field="name" style="font-size: 1.5rem; color: #007f7f">
            {{ columnTitle }}
          </p>
        </header>
        <div class="card-content">
          <div class="content is-align-content-center">
            <b-table :data="refValues.data">
              <b-table-column v-for="column in refValues.columns" :key="column.id" v-slot="props">
                <span>
                  <ReferencesDynamicLink
                      v-if="!props.row[column.field].length && props.row[column.field].length!==0"
                      :info="!props.row[column.field].length && props.row[column.field].length!==0"
                      :info-values="props.row[column.field]"
                      :application="application"
                      :reference-type="dynamicColumnReferences(props.row.colonne)"
                      :loaded-references-by-key="{}"
                      :column-id="column.id"
                      :column-title="column.title"
                  ></ReferencesDynamicLink>
                  <ReferencesLink
                      v-else-if="
                        (column.field === 'valeur') &&
                        refValues.referenceTypeForReferencingColumns[props.row.colonne]"
                      :application="application"
                      :reference-type="refValues.referenceTypeForReferencingColumns[props.row.colonne]"
                      :value="props.row.valeur"
                      :loaded-references-by-key="{}"
                  ></ReferencesLink>
                  <p v-else>{{ props.row[column.field] }}</p>
                </span>
              </b-table-column>
            </b-table>
          </div>
        </div>
      </div>
    </b-modal>
  </div>
</template>

<script>
import {ReferenceService} from "@/services/rest/ReferenceService";
import { InternationalisationService } from "@/services/InternationalisationService";

export default {
  name: "ReferencesLink",
  emits: ["changedRefValues"],
  components: {},
  props: {
    application: Object,
    referenceType: String,
    value: String,
    infoValues: {},
    referenceValue: {},
    loadedReferencesByKey: {
      type: Object,
    },
    columnId: String,
    // loadedReferencesById: {
    //   type: Object,
    // },
  },
  beforeCreate() {
    this.$options.components.ReferencesDynamicLink = require("./ReferencesDynamicLink.vue").default;
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
      refValues: {active: false},
      isCardModalActive: false,
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
          refValues = {
            referenceTypeForReferencingColumns,
            refValue
          };
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
      this.isCardModalActive = false;
      this.refValues = {...refValues, ...result};
      this.$emit("changedRefValues", {
        key: this.value,
        refType: this.referenceType,
        // hierarchicalKey,
        refValues,
      });
      return refValues;
    },
    dynamicColumnReferences(nameId) {
      return this.application.references[this.referenceType].dynamicColumns[nameId].reference;
    },
  },
};
</script>

<style scoped></style>
