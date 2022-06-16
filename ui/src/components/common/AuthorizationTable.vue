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
                v-else-if="column.display"
                :field="indexColumn"
                class="column"
            >
              <b-icon
                  :icon="STATES[states[indexColumn][getPath(index)].state] || STATES[0]"
                  size="is-medium"
                  type="is-primary"
                  @click.native="selectCheckbox($event, index, indexColumn)"
              />
              <AuthorizationForPeriodDatagroups
                  v-if="states[indexColumn][getPath(index)].fromAuthorization"
                  :column="column"
                  :dataGroups="dataGroups"
                  :state="states[indexColumn][getPath(index)]"
                  :index="index"
                  :indexColumn="indexColumn"
                  @registerCurrentAuthorization="$emit('registerCurrentAuthorization',$event)"
                />
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
              :authorization-scopes="authorizationScopes"
              :current-authorization-scope="getCurrentAuthorizationScope(scope)"
              @setIndetermined="eventSetIndetermined($event, index)"
              @modifyAuthorization="$emit('modifyAuthorization',$event)"
              @registerCurrentAuthorization="$emit('registerCurrentAuthorization',$event)"
          />
        </ul>
      </div>
    </li>
  </div>
</template>

<script>
import {Component, Prop, Vue, Watch} from "vue-property-decorator";
import {FontAwesomeIcon} from "@fortawesome/vue-fontawesome";
import {Authorization} from "@/model/authorization/Authorization";
import AuthorizationForPeriodDatagroups from "@/components/common/AuthorizationForPeriodDatagroups.vue";

@Component({
  components: {FontAwesomeIcon, AuthorizationForPeriodDatagroups},
})
export default class AuthorizationTable extends Vue {
  STATES = {"-1": "minus-square", 0: "square", 1: "check-square"};
  EXTRACTION = "extraction";
  @Prop() authReference;//informations about nodes
  @Prop() remainingOption;//array of next nodes
  @Prop() columnsVisible;// infos for columns
  @Prop({default: ""}) path;
  @Prop({default: false}) isRoot;
  @Prop() dataGroups; // array of the datagroups in  authorization configuration
  @Prop() authorization; //the authorizations scope from authorization configuration
  @Prop() authorizationScopes; //the authorizationsscope from authorization configuration
  @Prop() currentAuthorizationScope; //the current authorizations scope
  emits = ['modifyAuthorization', 'set-indetermined', 'registerCurrentAuthorization']
  name = "AuthorizationTable";
  open = {};
  upHere = false;
  remainingScopes = {};
  states = {}
  currentAuthorization = null
  showModal = false

  @Watch('authorization')
  onAuthorizationChanged(auth) {
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
        let currentPath = this.getPath(authReferenceKey)
        let state = this.authorization.getState(column, currentPath)
        states[column][currentPath] = state
      }
    }
    this.states = states
  }

  getScope() {
    return this.authorizationScopes?.[0]?.id;
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
    let eventToEmit, checkedAuthorization, authorization, requiredAuthorizations, authReference
    if (!indexColumn || !event) {
      return
    }
    var stateElement = this.states[indexColumn][this.getPath(index)]
    var currentPath = this.getPath(index)
    authorizations = authorizations || {toDelete: [], toAdd: []}
    if (stateElement.state == 1) {
      checkedAuthorization = this.authorization.getCheckedAuthorization(indexColumn, currentPath)
      if (checkedAuthorization.scopeKey == currentPath) {
        authorizations.toDelete.push(checkedAuthorization.auth)
        eventToEmit = {currentPath, authorizations, index, indexColumn};
        this.$emit("modifyAuthorization", eventToEmit)
      } else {
        var indetermined = false
        var count = 0;
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
        if (indetermined || !count) {
          this.$emit("setIndetermined", eventToEmit)
        } else {
          this.$emit("modifyAuthorization", eventToEmit)
        }
      }
    } else {
      let reference = this.authReference[index]
      requiredAuthorizations = this.currentAuthorizationScope || {}
      requiredAuthorizations[reference.authorizationScope] = reference.currentPath
      let currentAuthorization = new Authorization({requiredAuthorizations})
      let currentPath = currentAuthorization.getPath(this.authorizationScopes.map(as => as.id))
      let dependants = this.authorization.getDependants(indexColumn, currentPath)
      authorizations.toDelete = [...authorizations.toDelete, ...dependants]
      if ((Object.values(this.states[indexColumn]).filter(s => s.state != 1).length - 1) || this.isRoot) {
        authorizations.toAdd.push(currentAuthorization)
        eventToEmit = {event, index, indexColumn, authorizations};
        this.$emit("modifyAuthorization", eventToEmit)
      } else {
        eventToEmit = {event, index, indexColumn, authorizations};
        this.$emit("setIndetermined", eventToEmit)
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

.show-check-details {
  margin-left: .6em;
}

.show-detail-for-selected {
  height: 60px;
}
</style>