<template>
  <div>
    <li
        v-if="authReference && !authReference.hierarchicalKey"
        class="card-content authorizationTable datepicker-row"
    >
      <slot class="row"></slot>
      <div v-for="(scope, index) of authReference" :key="index">
        <div class="columns" @mouseleave="upHere = false" @mouseover="upHere = true">
          <div
              v-for="(column, indexColumn) of columnsVisible"
              :key="indexColumn"
              :class="{ hover: upHere && scope.isLeaf }"
              class="column"
          >
            <a
                v-if="
                column.display &&
                indexColumn === 'label' &&
                (!scope.isLeaf || remainingOption.length)
              "
                :class="!scope.isLeaf || remainingOption.length ? 'leaf' : 'folder'"
                :field="indexColumn"
                @click="indexColumn === 'label' && toggle(index)"
            >{{ localName(scope) }}</a
            >
            <p
                v-else-if="
                column.display &&
                indexColumn === 'label' &&
                !(!scope.isLeaf || remainingOption.length)
              "
                :class="!scope.isLeaf || remainingOption.length ? 'leaf' : 'folder'"
                :field="indexColumn"
            >
              {{ localName(scope) }}
            </p>

            <b-field
                v-else-if="column.display && indexColumn !== 'extraction'"
                :field="indexColumn"
                class="column"
            >
              <b-icon
                  :icon="STATES[states[indexColumn][getPath(index)]] || STATES[0]"
                  size="is-medium"
                  type="is-primary"
                  @click.native="selectCheckbox($event, index, indexColumn)"
              />
            </b-field>
            <b-field
                v-else-if="column.display && indexColumn === 'extraction'"
                :field="indexColumn"
                class="columns"
                style="margin-top: 6px"
            >
              <div class="column">
                <b-icon
                    :icon="STATES[states[indexColumn][getPath(index)]] || STATES[0]"
                    size="is-medium"
                    type="is-primary"
                    @click.native="selectCheckbox($event, index, indexColumn)"
                />
                <!--
                                <div
                                  class="columns"
                                  v-if="
                                    states &&
                                    states[indexColumn] &&
                                    states[indexColumn][index] === 1 &&
                                    localAuthorizationsTree &&
                                    localAuthorizationsTree[indexColumn] &&
                                    localAuthorizationsTree[indexColumn][index]
                                  "
                                >
                                  <b-taginput
                                    v-model="localAuthorizationsTree[indexColumn][index].dataGroups"
                                    :data="dataGroups"
                                    :open-on-focus="true"
                                    :placeholder="$t('dataTypeAuthorizations.data-groups-placeholder')"
                                    :value="dataGroups.id"
                                    autocomplete
                                    class="column"
                                    field="label"
                                    type="is-primary"
                                    @input="selectCheckbox($event, index, indexColumn, scope)"
                                  >
                                  </b-taginput>
                                  <div class="column">
                                    <b-datepicker
                                      v-model="localAuthorizationsTree[indexColumn][index].from"
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
                                      v-model="localAuthorizationsTree[indexColumn][index].to"
                                      :date-parser="parseDate"
                                      :placeholder="
                                        $t('dataTypesRepository.placeholder-datepicker') +
                                        ' dd-MM-YYYY, dd-MM-YYYY hh, dd-MM-YYYY hh:mm, dd-MM-YYYY HH:mm:ss'
                                      "
                                      editable
                                      icon="calendar"
                                      @input="selectCheckbox($event, index, indexColumn, scope, 'to')"
                                    >
                                    </b-datepicker>
                                  </div>
                                </div>
                -->
              </div>
            </b-field>
          </div>
        </div>
        <ul
            v-if="authReference && (!scope.isLeaf || remainingOption.length) && open && open[index]"
            class="rows"
        >
          <AuthorizationTable
              v-if="authReference"
              :authReference="getNextAuthreference(scope)"
              :authorization="authorization"
              :columnsVisible="columnsVisible"
              :dataGroups="dataGroups"
              :path="getPath(index)"
              :remaining-option="getRemainingOption(scope)"
              :required-authorizations="{}"
              :current-authorization-scope="getCurrentAuthorizationScope(scope)"
              @set-indetermined="eventSetIndetermined($event, index)"
          />
        </ul>
      </div>
    </li>
  </div>
</template>

<script>
import {Component, Prop, Vue, Watch} from "vue-property-decorator";
import {FontAwesomeIcon} from "@fortawesome/vue-fontawesome";
//import { Authorization } from "@/model/authorization/Authorization";

@Component({
  components: {FontAwesomeIcon},
})
export default class AuthorizationTable extends Vue {
  STATES = {"-1": "minus-square", 0: "square", 1: "check-square"};
  EXTRACTION = "extraction";
  @Prop() authReference;//informations about nodes
  @Prop() remainingOption;//array of next nodes
  @Prop() columnsVisible;// infos for columns
  @Prop({default: ""}) path;
  @Prop() dataGroups; // array of the datagroups in  authorization configuration
  @Prop() authorization; //the authorizations scope from authorization configuration
  @Prop() currentAuthorizationScope; //the authorizations scope from authorization configuration
  emits = ['modifyAuthorization', 'set-indetermined']
  name = "AuthorizationTable";
  open = {};
  upHere = false;
  remainingScopes = {};
  states = {}

  @Watch('authorization')
  onAuthorizationChanged(auth) {
    console.log('authorization changed', auth)
    this.authorization = auth
    this.updateStates()

  }

  created() {
    this.updateStates();
  }

  updateStates() {
    var states = {};
    for (const column in this.columnsVisible) {
      if (column == 'label') {
        continue;
      }
      states[column] = {}
      for (const authReferenceKey in this.authReference) {
        var currentPath = this.getPath(authReferenceKey)
        states[column][currentPath] = this.authorization.getState(column, currentPath)
      }
    }
    this.states = states
  }

  getScope() {
    return this.authorizationScopes?.[0]?.id;
  }

  parseDate(date) {
    date =
        date && date.replace(/(\d{2})\/(\d{2})\/(\d{4})(( \d{2})?(:\d{2})?(:\d{2})?)/, "$3-$2-$1$4");
    return new Date(date);
  }

  initOpen() {
    if (this?.authReference && !this?.authReference?.hierarchicalKey) {
      for (const index in this.authReference) {
        if (!this.authReference[index].isLeaf || this.remainingOption.length)
          this.open[index] = false;
      }
    }
    this.localAuthorizationsTree = this.authorizationsTree;
  }

  getPath(index) {
    return this.path + (this.path ? "." : "") + index;
  }

  localName(states) {
    return (
        states.localName ||
        (this.authReference.authorizationScope && this.authReference.authorizationScope.localName) ||
        "pas trouve"
    );
  }

  toggle(index) {
    var open = {};
    open[index] = !this.open[index];
    this.open = {...this.open, ...open};
  }

  select(option) {
    this.$emit("select-menu-item", option || this.option);
  }

  eventSetIndetermined(event, index) {
    this.selectCheckbox(event.event, index, event.indexColumn, event.authorizations)
  }

  selectCheckbox(event, index, indexColumn, authorizations) {
    var eventToEmit
      let checkedAuthorization,authorization,requiredAuthorizations,authReference
    if (!indexColumn || !event) {
      return
    }
    var stateElement = this.states[indexColumn][this.getPath(index)]
    var currentPath = this.getPath(index)
      authorizations = authorizations || {toDelete:[], toAdd:[]}
    if (stateElement) {
      checkedAuthorization = this.authorization.getCheckedAuthorization(indexColumn, currentPath)
      if (checkedAuthorization.scopeKey == currentPath) {
        authorizations.toDelete.push(checkedAuthorization.auth)
        eventToEmit = {currentPath, authorizations, index, indexColumn};
        this.$emit("modifyAuthorization", eventToEmit)
      } else {
        var indetermined = false
        for (const authReferenceKey in this.authReference) {
          if (authReferenceKey != index) {
            authorization = {...checkedAuthorization.auth}
            requiredAuthorizations = {...this.currentAuthorizationScope}
            authReference = this.authReference[authReferenceKey]
            requiredAuthorizations[authReference.authorizationScope] = authReference.currentPath
            authorization.requiredAuthorizations = requiredAuthorizations
             authorizations.toAdd.push(authorization)
            //this.$emit("addAuthorization", eventToEmit)
            indetermined = true
          }
        }
        eventToEmit = {event, index, indexColumn, authorizations};
        if (indetermined) {
          this.$emit("set-indetermined", eventToEmit)
        }else{
          this.$emit("addAuthorization", eventToEmit)
        }
      }
    }
  }

  getCurrentAuthorizationScope(scope) {
    var authorizationScope = {}
    authorizationScope[scope.authorizationScope] = scope.currentPath
    return {...this.currentAuthorizationScope, ...authorizationScope}
  }

  getNextAuthreference(states) {
    if (!states.isLeaf) {
      return states.referenceValues;
    } else {
      return this.remainingOption.length ? this.remainingOption[0] : states.referenceValues;
    }
  }

  getNextScope(states) {
    if (!states.isLeaf) {
      return this.authorizationScopes;
    } else {
      return (this.authorizationScopes || []).slice(1, (this.authorizationScopes || []).length);
    }
  }

  getRemainingOption(states) {
    if (states.isLeaf) {
      return this.remainingOption.slice(1, this.remainingOption.length);
    } else {
      return this.remainingOption;
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
</style>