<template>
  <div>
    <li
        class="card-content authorizationTable datepicker-row"
    >
      <slot class="row"></slot>
      <div class="columns">
        <div
            v-for="(column, indexColumn) of columnsVisible"
            :key="indexColumn"
            :style="!column.display ? 'display : contents':''"
            class="column"
        >
          <a
              v-if="
                  column.display &&
                  indexColumn === 'label'
                "
              :field="indexColumn"
              class="folder"
              style="min-height: 10px; display: table-cell; vertical-align: middle"
              @click="indexColumn === 'label' && toggle()"
          >
                <span style="margin-right: 10px">
                  <FontAwesomeIcon
                      :icon="openChild ? 'caret-down' : 'caret-right'"
                      tabindex="0"
                  />
                </span>
            <span> {{ datatype.name }} </span>
          </a>

          <b-field v-else-if="column.display && indexColumn === 'admin'" :field="indexColumn" class="column">
            <b-tooltip
                :label="canShowWarning(indexColumn)?$t('validation.noRightsForThisOPeration'):$t('dataTypeAuthorizations.all-autorisation')"
                type="is-warning"
            >
              <b-icon
                  :disabled="canShowWarning(indexColumn)"
                  :icon="STATES[getState(indexColumn)]"
                  :type="
                      hasPublicStates(indexColumn) ||
                      canShowWarning( indexColumn)
                        ? 'is-light'
                        : 'is-warning'
                    "
                  class="clickable"
                  pack="far"
                  size="is-medium"
                  @click.native="!canShowWarning(indexColumn) && selectCheckboxAll( indexColumn)"
              />
            </b-tooltip>
          </b-field>


          <b-field v-else-if="column.display" :field="indexColumn" class="column">
            <b-tooltip
                :active="canShowWarning(indexColumn)"
                :label="$t('validation.noRightsForThisOPeration')"
                type="is-warning"
            >
              <b-icon
                  :disabled="canShowWarning(indexColumn)"
                  :icon="STATES[getState(indexColumn)]"
                  :type="
                      hasPublicStates(indexColumn) ||
                      canShowWarning( indexColumn)
                        ? 'is-light'
                        : 'is-primary'
                    "
                  class="clickable"
                  pack="far"
                  size="is-medium"
                  @click.native="!canShowWarning(indexColumn) && selectCheckboxAll( indexColumn)"
              />
            </b-tooltip>
          </b-field>
        </div>
      </div>
      <ul
          class="rows"
      >
        <AuthorizationTable
            v-if="openChild && dataGroups && authReferences && columnsVisible && authReferences[0]"
            :auth-reference="authReferences[0]"
            :authorization="authorization"
            :authorization-scopes="authorizationScopes"
            :columns-visible="columnsVisible"
            :current-authorization-scope="{}"
            :data-groups="dataGroups"
            :is-root="true"
            :isApplicationAdmin="isApplicationAdmin"
            :ownAuthorizations="ownAuthorizations"
            :ownAuthorizationsColumnsByPath="ownAuthorizationsColumnsByPath"
            :publicAuthorizations="publicAuthorizations"
            :remaining-option="authReferences.slice && authReferences.slice(1, authReferences.length)"
            class="rows"
            @modifyAuthorization="$emit('modifyAuthorization', {...$event, datatype:datatype.id})"
            @registerCurrentAuthorization="$emit('registerCurrentAuthorization', {...$event, datatype:datatype.id})"
        >
          <b-loading v-model="isLoading" :is-full-page="null"></b-loading>
        </AuthorizationTable>
      </ul>
    </li>
  </div>
</template>

<script>
import AuthorizationTable from "@/components/common/AuthorizationTable";
import {FontAwesomeIcon} from "@fortawesome/vue-fontawesome";
import {Authorization} from "@/model/authorization/Authorization";

export default {
  name: "AuthorizationTableForDatatype",
  emits: ['modifyAuthorization', 'registerCurrentAuthorization'],
  components: {
    AuthorizationTable,
    FontAwesomeIcon
  },
  props: {
    columnsVisible: Object,
    dataGroups: Array,
    authReferences: {},
    authorization: Object,
    authorizationScopes: Array,
    isApplicationAdmin: Boolean,
    isRoot: Boolean,
    ownAuthorizations: Array,
    ownAuthorizationsColumnsByPath: Object,
    publicAuthorizations: Array,
    datatype: Object
  },
  data: () => {
    return {
      openChild: false,
      isLoading: true,
      STATES: {"-1": "square-minus", 0: "square", 1: "square-check"}
    }
  },
  created() {
    this.isLoading = false;
  },
  methods: {
    getColumnTitle: function (column) {
      if (column.display) {
        return (
            (column.internationalizationName && column.internationalizationName[this.$i18n.locale]) ||
            column.title
        );
      }
    },
    toggle() {
      this.openChild = !this.openChild;
    },
    getState(indexColumn) {
      let nb = this.authorization?.authorizations?.[indexColumn]?.length;
      if (!nb) return 0;
      if (nb == Object.keys(this.authReferences[0]).length) return 1;
      return -1

    },
    hasPublicStates(indexColumn) {
      if (this.publicAuthorizations[indexColumn])
        return (this.publicAuthorizations[indexColumn] || [])
            .some(path => Object.keys(this.authReferences?.[0] || {}).some(authPath => authPath.startsWith(path)))
    },
    canShowWarning(indexColumn) {
      return (
          this.isApplicationAdmin ||
          this.hasPublicStates('admin') ||  indexColumn
          /*(this.ownAuthorizations.find((oa) => this.getPath(index).startsWith(oa)) &&
              this.isAuthorizedColumnForPath(index, column))*/
      )
          ? false
          : true;

    },
    selectCheckboxAll(indexColumn) {
      let state = this.getState(indexColumn);
      let auths = this.authReferences[0];
      for (const index in auths) {
        if (this.haveRightsOn(index, indexColumn)) {
          let requiredAuthorizations = {}
          requiredAuthorizations[auths[index].authorizationScope] = index;
          let authorizations = new Authorization([], requiredAuthorizations)
          let eventToEmit = {
            datatype: this.datatype.id,
            event: null,
            index,
            indexColumn,
            authorizations: (state ? {toDelete: [authorizations]} : {toAdd: [authorizations]})
          }
          if (indexColumn=='admin'){
            Object.keys(this.columnsVisible||[])
                .filter(label=>label!='label')
                .map(label=> {
                  return {
                    ...eventToEmit,
                    indexColumn: label
                  }
                })
                .forEach(event => this.$emit("modifyAuthorization", event))
          }else {
            this.$emit("modifyAuthorization", eventToEmit);
          }
        }
      }
    },
    haveRightsOn(index, column) {
      if (this.isApplicationAdmin) return true;
      return ((this.ownAuthorizations.find((oa) => index.startsWith(oa)) &&
          this.isAuthorizedColumnForPath(index, column))
          ? false
          : true);
    },
    isAuthorizedColumnForPath(index, column) {
      for (const path in this.ownAuthorizationsColumnsByPath) {
        if (
            this.getPath(index).startsWith(path) &&
            this.ownAuthorizationsColumnsByPath[path].indexOf(column) >= 0
        ) {
          return true;
        }
      }
      return false;
    }
  }
}
</script>


<style lang="scss" scoped>
.authorizationTable {
  margin-left: 10px;
  padding: 0 0 0 5px;

  button {
    opacity: 0.75;
  }

  .dropdown-menu .dropdown-content .dropDownMenu button {
    opacity: 0.5;
  }

  dgSelected {
    color: #007f7f;
  }
}

a {
  color: $dark;
  font-weight: bold;
  text-decoration: underline;
}

a:hover {
  color: $primary;
  text-decoration: none;
}

p {
  font-weight: bold;
}

::marker {
  color: transparent;
}

.column {
  padding: 6px;
}

.show-check-details {
  margin-left: 0.6em;
}

.show-detail-for-selected {
  height: 60px;
}
</style>