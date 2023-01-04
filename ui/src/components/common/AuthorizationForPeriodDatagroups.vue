<template>
  <span>
    <b-tooltip position="is-right" multilined>
      <b-button
        v-if="(column.withDataGroups && dataGroups.length > 1) || column.withPeriods"
        class="show-check-details"
        :type="state.state !== 1 ? 'is-grey ' : 'is-primary '"
        @click="showDetail"
        style="border: none; background-color: transparent; margin: 0px"
        ><b-icon
          :type="disabled ? 'is-warning-light' : 'is-primary'"
          v-if="(column.withDataGroups && dataGroups.length > 1) || column.withPeriods"
          icon="ellipsis-h"
          size="fa-4x"
        ></b-icon
      ></b-button>
      <template v-slot:content>
        <div v-if="disabled" class="has-background-warning-light has-text-black-bis">
          {{ $t("validation.noRightsForThisOPeration") }}
        </div>
        <div
          v-else-if="state.state === 1"
          class="has-background-primary show-detail-for-selected"
          style="height: 175px"
        >
          {{ $t("dataTypeAuthorizations.info-limit-taginput") }}
          <b v-if="column.withPeriods">{{ $t("dataTypeAuthorizations.a-period") }}</b>
          <span v-if="(column.withDataGroups && dataGroups.length > 1) || column.withPeriods"
            >{{ $t("dataTypeAuthorizations.or") }}
          </span>
          <b v-if="column.withDataGroups && dataGroups.length > 1">{{
            $t("dataTypeAuthorizations.a-datagroup")
          }}</b>
          <div>
            <h3>
              {{ $t("dataTypesRepository.table-file-data-period") }} {{ $t("ponctuation.colon") }}
            </h3>
            <div v-if="state.from || state.to">
              <span v-if="state.from">
                {{ $t("dataTypeAuthorizations.from-date") }} {{ state.from.toDateString() }}
              </span>
              <span v-if="state.to">
                {{ $t("dataTypeAuthorizations.to-date") }} {{ state.to.toDateString() }}
              </span>
            </div>
            <div class="defaultValueTooltip" v-else>
              {{ $t("dataTypeAuthorizations.all-dates") }}
            </div>
          </div>
          <div>
            <h3>{{ $t("dataTypeAuthorizations.data-group") }} {{ $t("ponctuation.colon") }}</h3>
            <div class="defaultValueTooltip" v-if="state.dataGroups && state.dataGroups.length > 0">
              <span v-for="(datagroup, i) in state.dataGroups" class="defaultValueTooltip" :key="i">
                {{ dataGroups.find((dg) => dg.id == datagroup || dg.id == datagroup.id).label }}
                {{ $t("ponctuation.comma") }}
              </span>
            </div>
            <div class="defaultValueTooltip" v-else>
              {{ $t("dataTypeAuthorizations.all-variable") }}
            </div>
          </div>
        </div>
      </template>
    </b-tooltip>
    <b-modal
      v-if="currentAuthorization"
      v-model="showModal"
      class="modalCardRef"
      has-modal-card
      trap-focus
    >
      <div class="card">
        <header class="card-header">
          <p class="card-header-title">
            {{ $t("dataTypeAuthorizations.card-header-extraction") }}
          </p>
        </header>
        <div class="card-content">
          <b-field
            v-if="column.withDataGroups"
            :label="$t('dataTypeAuthorizations.label-tagInput')"
            label-position="on-border"
          >
            <b-taginput
              v-model="currentAuthorization.authorizations.dataGroups"
              :data="dataGroups"
              :open-on-focus="true"
              :placeholder="$t('dataTypeAuthorizations.data-groups-placeholder')"
              :value="dataGroups.id"
              autocomplete
              class="column"
              field="label"
              type="is-primary"
            >
            </b-taginput>
          </b-field>
          <b-field
            v-if="column.withPeriods"
            class="column"
            :label="$t('dataTypeAuthorizations.label-datePicker')"
            label-position="on-border"
          >
            <b-datepicker
              v-model="currentAuthorization.authorizations.from"
              :date-parser="parseDate"
              :placeholder="
                $t('dataTypesRepository.placeholder-datepicker') +
                ' dd-MM-YYYY, dd-MM-YYYY hh, dd-MM-YYYY hh:mm, dd-MM-YYYY HH:mm:ss'
              "
              editable
              icon="calendar"
              pack="far"
              @remove.capture="() => selectCheckbox($event, index, indexColumn, scope)"
              @input="selectCheckbox($event, index, indexColumn, scope, 'from')"
            >
            </b-datepicker>
          </b-field>
          <b-field
            v-if="column.withPeriods"
            class="column"
            :label="$t('dataTypeAuthorizations.label-datePicker')"
            label-position="on-border"
          >
            <b-datepicker
              v-model="currentAuthorization.authorizations.to"
              :date-parser="parseDate"
              :placeholder="
                $t('dataTypesRepository.placeholder-datepicker') +
                ' dd-MM-YYYY, dd-MM-YYYY hh, dd-MM-YYYY hh:mm, dd-MM-YYYY HH:mm:ss'
              "
              editable
              icon="calendar"
              pack="far"
            >
            </b-datepicker>
          </b-field>
          <div class="buttons">
            <b-button
              icon-left="check"
              type="is-dark"
              @click="registerCurrentAuthorization"
              style="margin-bottom: 10px"
            >
              {{ $t("dataTypesManagement.validate") }}
            </b-button>
          </div>
        </div>
      </div>
    </b-modal>
  </span>
</template>

<script>
import { Component, Prop, Vue } from "vue-property-decorator";
import { FontAwesomeIcon } from "@fortawesome/vue-fontawesome";

@Component({
  components: { FontAwesomeIcon },
})
export default class AuthorizationForPeriodDatagroups extends Vue {
  @Prop() column;
  @Prop() dataGroups;
  @Prop() state;
  @Prop() index;
  @Prop() indexColumn;
  @Prop() disabled;
  emits = ["registerCurrentAuthorization"];
  showModal = false;
  currentAuthorization = {};

  created() {
    this.init();
  }

  init() {
    var authorizations = this.state.fromAuthorization;
    if (authorizations) {
      authorizations.dataGroups = this.dataGroups.filter((dg) =>
        authorizations.dataGroups.find((dg2) => dg.id == dg2)
      );
      authorizations.from = authorizations.fromDay ? new Date(authorizations.fromDay) : null;
      authorizations.to = authorizations.toDay ? new Date(authorizations.toDay) : null;
      this.currentAuthorization = {
        index: this.index,
        indexColumn: this.indexColumn,
        authorizations,
      };
    }
  }

  registerCurrentAuthorization() {
    this.$emit("registerCurrentAuthorization", this.currentAuthorization);
    this.showModal = false;
    this.currentAuthorization = null;
  }

  showDetail() {
    this.showModal = this.state.state == 1;
  }

  parseDate(date) {
    date =
      date && date.replace(/(\d{2})\/(\d{2})\/(\d{4})(( \d{2})?(:\d{2})?(:\d{2})?)/, "$3-$2-$1$4");
    return new Date(date);
  }
}
</script>

<style lang="scss" scoped>
.show-check-details {
  margin-left: 0.6em;
}

.show-detail-for-selected {
  height: 60px;
}

.modalCardRef .modal-background {
  background-color: rgba(10, 10, 10, 0.5);
}

.modal .modal-content {
  height: 70%;
}
</style>
