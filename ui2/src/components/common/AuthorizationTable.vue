<template>
  <div>
    <li class="card-content authorizationTable datepicker-row">
      <slot class="row"></slot>
      <div v-for="(scope, index) of authReference" :key="index">
        <div class="columns"
             @mouseleave="upHere = false" @mouseover="upHere = true">
          <div v-for="(column, indexColumn) of columnsVisible" :key="indexColumn"
               :class="{hover : upHere && scope.isLeaf}"
               class="column">
            <b-button v-if="column.display && indexColumn=='label' && (!scope.isLeaf || remainingOption.length)"
                      :class="(!scope.isLeaf || remainingOption.length)?'leaf':'folder'"
                      :field="indexColumn"
                      :label="localName(scope)"
                      @click="indexColumn=='label' && toggle(index)"/>
            <b-field v-else-if="column.display && indexColumn=='label' && !(!scope.isLeaf || remainingOption.length)"
                     :class="(!scope.isLeaf || remainingOption.length)?'leaf':'folder'"
                     :field="indexColumn" :label="localName(scope)"/>
            <b-field v-else-if="column.display && indexColumn!='date'" :field="indexColumn">
              <b-icon :icon="(statesIcons && statesIcons[indexColumn] && statesIcons[indexColumn][index]) ||'square'"
                      size="is-medium" type="is-primary"
                      @click.native="selectCheckbox($event,index, indexColumn, scope)"/>
              <!--b-checkbox :checked=""
                          @input="selectCheckbox($event,index, indexColumn, scope)"/-->
            </b-field>
            <b-field v-else-if="column.display && indexColumn=='date'" :field="indexColumn">
              <b-radio/>
            </b-field>
          </div>
        </div>
        <ul v-show="(!scope.isLeaf || remainingOption.length) && open && open[index]" class="rows">
          <AuthorizationTable
              :authReference="getNextAuthreference(scope)"
              :authorizations-tree="authorizationByScope && authorizationByScope[index]"
              :columnsVisible="columnsVisible"
              :path="getPath(index)"
              :remaining-option="getRemainingOption(scope)"
              @add-authorization="emitAddAuthorization($event,  index)"
              @delete-authorization="emitDeleteAuthorization($event,  index)"/>
        </ul>
      </div>
    </li>
  </div>
</template>

<script>
import {Component, Prop, Vue, Watch} from "vue-property-decorator";
import {FontAwesomeIcon} from "@fortawesome/vue-fontawesome";
import {Authorization} from "@/model/authorization/Authorization";

@Component({
  components: {FontAwesomeIcon},
})
export default class AuthorizationTable extends Vue {
  STATES = {'-1': 'minus-square', '0': 'square', '1': 'check-square'}
  @Prop() authReference;
  @Prop() remainingOption;
  @Prop() columnsVisible;
  @Prop({default: ''}) path;
  @Prop() authorizationsTree;
  name = 'AuthorizationTable'
  localAuthorizationsTree = this.authorizationsTree || {}
  authorizationByScope = {}
  states = {}
  statesIcons = {}
  open = {}
  emits = ["add-authorization", "delete-authorization"];
  upHere = false;

  @Watch('authReference')
  updateAuthorizationTree() {
    this.initAuthorizationByScope()
    this.initStates();
    this.initOpen()
    this.$children
        .filter(child => child.name == 'AuthorizationTable')
        .filter(child => child.updateAuthorizationTree())
  }

  @Watch('authorizationsTree')
  changedAuthorizationsTree() {
    this.updateAuthorizationTree();
  }

  initAuthorizationByScope() {
    this.localAuthorizationsTree = this.authorizationsTree;
    let authorizationByScope = this.authorizationByScope;
    for (const reference in this.authReference) {
      for (const type in this.authorizationsTree) {
        if (this.authorizationsTree?.[type]?.[reference]) {
          authorizationByScope[reference] = {}
          authorizationByScope[reference][type] = this.authorizationsTree?.[type]?.[reference];
        }
      }
    }
    this.authorizationByScope = authorizationByScope;
  }

  initStates() {
    if (!this.authReference) return
    var states = {}
    var statesIcons = {}
    for (var index in this.columnsVisible) {
      states[index] = {};
      statesIcons[index] = {};
      for (var reference in this.authReference) {
        var state = 0;
        if (this.localAuthorizationsTree?.[index]?.[reference]) {
          if (Object.keys(this.localAuthorizationsTree?.[index]?.[reference]).find(v => 'datagroups' == v)) {
            state = 1;
          } else {
            state = -1;
          }
        }
        states[index][reference] = state
        statesIcons[index][reference] = this.STATES[state];
      }
    }
    this.states = states;
    this.statesIcons = statesIcons;
  }

  getStateIcon(index, indexColumn) {
    this.states[indexColumn] || this.initStates()
    let states = this.states[indexColumn];
    if (states && Object.values(states).every(value => value)) {
      return 'check-square'
    } else if (!states || !Object.values(this.states).reduce((acc, value) => acc || value, false)) {
      return 'square'
    }
    return 'minus-square'
  }

  updateState(type, reference, value) {
    this.states[type] || this.initStates()
    var states = this.states
    var statesIcons = this.statesIcons
    states[type][reference] = value;
    statesIcons[type][reference] = this.STATES[value];
    this.states = states;
    this.statesIcons = statesIcons;
    if (value == -1) return
    if (this.authReference[reference].isLeaf) {
      return
    }
    this.$children
        .filter(child => child.name == 'AuthorizationTable')
        .forEach(child => {
          child.states[type] || child.initStates()
          if (child.states[type]) {
            for (const childType in child.states[type]) {
              child.updateState(type, childType, value)
            }
          }
        })
  }

  initOpen() {
    if (this?.authReference && !this?.authReference?.hierarchicalKey) {
      for (const index in this.authReference) {
        if (!this.authReference[index].isLeaf || this.remainingOption.length)
          this.open[index] = false;
      }
    }
    this.localAuthorizationsTree = this.authorizationsTree
    //console.log("mounted")
  }

  getPath(index) {
    return this.path + (this.path ? '.' : '') + index
  }

  localName(scope) {
    return scope.localName || (this.authReference.authorizationScope && this.authReference.authorizationScope.localName) || 'pas trouve'
  }

  toggle(index) {
    if (!this.open[index]) {
      this.updateAuthorizationTree()
    }
    var open = {}
    open[index] = !this.open[index];
    this.open = {...this.open, ...open}
  }

  select(option) {
    this.$emit("select-menu-item", option || this.option);
  }

  getNextAuthreference(scope) {
    if (!scope.isLeaf) {
      return scope.referenceValues
    } else {
      return this.remainingOption.length ? this.remainingOption[0] : scope.referenceValues;
    }
  }

  getRemainingOption(scope) {
    if (scope.isLeaf) {
      return this.remainingOption.slice(1, this.remainingOption.length)
    } else {
      return this.remainingOption;
    }

  }

  emitDeleteAuthorization(event, index) {
    let localAuthorizationsTree = this.localAuthorizationsTree || {};
    localAuthorizationsTree[event.type] = localAuthorizationsTree?.[event.type] || {}
    localAuthorizationsTree[event.type][index] = localAuthorizationsTree?.[event.type][index] || {}
    delete localAuthorizationsTree[event.type][index][event.child]
    this.localAuthorizationsTree = localAuthorizationsTree;
    this.updateState(event.type, index, event.state)
    this.$emit('delete-authorization',
        {
          type: event.type,
          child: index,
          state: event.state,
          authorizationsTree: this.localAuthorizationsTree,
          authorizationScope: localAuthorizationsTree[event.type][index]
        }
    )
  }

  emitAddAuthorization(event, index) {
    let localAuthorizationsTree = this.localAuthorizationsTree || {};
    localAuthorizationsTree[event.type] = localAuthorizationsTree?.[event.type] || {}
    localAuthorizationsTree[event.type][index] = localAuthorizationsTree?.[event.type][index] || {}
    localAuthorizationsTree[event.type][index][event.child] = localAuthorizationsTree?.[event.type][index][event.child] || event.authorizationScope
    this.localAuthorizationsTree = localAuthorizationsTree;
    this.updateState(event.type, index, event.state)
    this.$emit('add-authorization',
        {
          type: event.type,
          child: index,
          state: event.state,
          authorizationsTree: this.localAuthorizationsTree,
          authorizationScope: localAuthorizationsTree[event.type][index]
        }
    )
  }

  selectCheckbox(event, index, indexColumn, scope) {
    this.states[indexColumn] || this.initStates()
    let localAuthorizationsTree = this.localAuthorizationsTree || {};
    var states, state
    if (!(this.states && this.states[indexColumn] && this.states[indexColumn][index] != 1)) {
      if (localAuthorizationsTree?.['type']?.[indexColumn]) {
        delete localAuthorizationsTree?.['type']?.[indexColumn]
      }
      this.updateState(indexColumn, index, 0)
      states = this.states[indexColumn];
      if (states) {
        state = {}
        for (const stateKey in states) {
          state[states[stateKey]] = true
        }
      }
      if (state[-1] || (state[0] && state[1])) {
        state = -1
      } else if (state[1]) {
        state = 1
      } else {
        state = 0
      }
      this.$emit('delete-authorization',
          {
            state: state,
            type: indexColumn,
            authorizationsTree: this.localAuthorizationsTree,
            child: index
          }
      )
    } else {
      localAuthorizationsTree[indexColumn] = localAuthorizationsTree?.[indexColumn] || {}
      localAuthorizationsTree[indexColumn][index] = localAuthorizationsTree[indexColumn]?.[index] || new Authorization([], null, null);
      var authorizationScope = {}
      let id = scope.authorizationScope;
      authorizationScope[id] = scope.key;
      this.updateState(indexColumn, index, 1)
      states = this.states[indexColumn];
      if (states) {
        state = {}
        for (const stateKey in states) {
          state[states[stateKey]] = true
        }
      }
      if (state[-1] || (state[0] && state[1])) {
        state = -1
      } else if (state[1]) {
        state = 1
      } else {
        state = 0
      }
      this.$emit('add-authorization',
          {
            type: indexColumn,
            child: index,
            state: state,
            authorizationsTree: this.localAuthorizationsTree,
            authorizationScope: localAuthorizationsTree[indexColumn][index]
          }
      )
    }
    this.localAuthorizationsTree = localAuthorizationsTree
  }
}
</script>

<style lang="scss" scoped>
.authorizationTable {
  margin-left: 10px;
  margin-right: 10px;
  padding: 5px;

  button {
    opacity: 0.75;
  }

  .dropdown-menu .dropdown-content .dropDownMenu button {
    opacity: 0.5;
  }
}
</style>