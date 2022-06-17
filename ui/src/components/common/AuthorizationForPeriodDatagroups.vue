<template>
  <div>
    <b-tooltip position="is-left">
      <b-icon
        v-if="(column.withDataGroups && dataGroups.length > 1) || column.withPeriods"
        icon="eye"
        size="fa-4x"
        class="show-check-details"
        :type="state.state != 1 ? 'is-grey ' : 'is-primary '"
        @click.native="showDetail"
      />
      <template v-slot:content>
        <div v-if="state.state == 0">
          <p class="has-background-white-bis has-text-primary">
            Pour limiter le droit à
            <b v-if="column.withPeriods">une période </b>
            <span v-if="(column.withDataGroups && dataGroups.length > 1) || column.withPeriods"
              >ou
            </span>
            <b v-if="column.withDataGroups && dataGroups.length > 1">des groupes de données </b>
            <br />
            veuillez d'abord selectionner le droit.
          </p>
        </div>
        <div v-else-if="state.state == -1">
          <p class="has-background-white-bis has-text-primary">
            Pour limiter le droit à
            <b v-if="column.withPeriods">une période </b>
            <span v-if="(column.withDataGroups && dataGroups.length > 1) || column.withPeriods"
              >ou
            </span>
            <b v-if="column.withDataGroups && dataGroups.length > 1">des groupes de données </b>
            <br />
            veuillez d'abord selectionner le droit.
          </p>
          <p class="has-background-white-bis has-text-primary">
            Des limitations sont en cours sur des enfants, elles seront remplacées.
          </p>
        </div>
        <div v-else class="has-background-primary show-detail-for-selected">
          Permet de limiter le droit à
          <b v-if="column.withPeriods">une période </b>
          <span v-if="(column.withDataGroups && dataGroups.length > 1) || column.withPeriods"
            >ou
          </span>
          <b v-if="column.withDataGroups && dataGroups.length > 1">des groupes de données </b>
          <div>
            <div class="title2">Période</div>
            <div v-if="state.from || state.to">
              <span v-if="state.from"> du {{ state.from.toDateString() }} </span>
              <span v-if="state.to"> jusqu'au {{ state.to.toDateString() }} </span>
            </div>
            <div v-else>Toutes les dates</div>
          </div>
          <div>
            <div class="title2">Groupes de données</div>
            <div v-if="state.dataGroups && state.dataGroups.length > 0">
              <span v-for="(datagroup, i) in state.dataGroups" class="tag is-info" :key="i">
                {{ dataGroups.find((dg) => dg.id == datagroup || dg.id == datagroup.id).label }}
              </span>
            </div>
            <div v-else>Toutes les variables</div>
          </div>
        </div>
      </template>
    </b-tooltip>
    <b-modal
      v-if="currentAuthorization"
      v-model="showModal"
      class="modalCardRef"
      width="70%"
      @after-leave="registerCurrentAuthorization"
    >
      <div class="card">
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

        <div class="column">
          <b-datepicker
            v-model="currentAuthorization.authorizations.from"
            :date-parser="parseDate"
            :placeholder="
              $t('dataTypesRepository.placeholder-datepicker') +
              ' dd-MM-YYYY, dd-MM-YYYY hh, dd-MM-YYYY hh:mm, dd-MM-YYYY HH:mm:ss'
            "
            editable
            icon="calendar"
            @remove.capture="() => selectCheckbox($event, index, indexColumn, scope)"
            @input="selectCheckbox($event, index, indexColumn, scope, 'from')"
          >
          </b-datepicker>
        </div>
        <div class="column">
          <b-datepicker
            v-model="currentAuthorization.authorizations.to"
            :date-parser="parseDate"
            :placeholder="
              $t('dataTypesRepository.placeholder-datepicker') +
              ' dd-MM-YYYY, dd-MM-YYYY hh, dd-MM-YYYY hh:mm, dd-MM-YYYY HH:mm:ss'
            "
            editable
            icon="calendar"
          >
          </b-datepicker>
        </div>
      </div>
    </b-modal>
  </div>
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
</style>
