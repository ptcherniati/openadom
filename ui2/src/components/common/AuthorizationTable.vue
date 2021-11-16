<template>
  <div>
    <li v-if="authReference && !authReference.hierarchicalKey"
        class="card-content authorizationTable datepicker-row">
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
            <b-field v-else-if="column.display && indexColumn!='extraction'"
                     :field="indexColumn">
              <b-icon :icon="(statesIcons && statesIcons[indexColumn] && statesIcons[indexColumn][index]) ||'square'"
                      size="is-medium" type="is-primary"
                      @click.native="selectCheckbox($event,index, indexColumn, scope)"/>
            </b-field>
            <b-field v-else-if="column.display && indexColumn=='extraction'" :field="indexColumn" class="row">
              <div class="columns">
                <b-icon :icon="(statesIcons && statesIcons[indexColumn] && statesIcons[indexColumn][index]) ||'square'"
                        class="column" size="is-medium"
                        type="is-primary"
                        @click.native="selectCheckbox($event,index, indexColumn, scope)"/>
                <b-taginput
                    v-if="states && states[indexColumn] && states[indexColumn][index]==1 &&
                    localAuthorizationsTree && localAuthorizationsTree[indexColumn] && localAuthorizationsTree[indexColumn][index]"
                    v-model="localAuthorizationsTree[indexColumn][index].dataGroups"
                    :data="dataGroups"
                    :open-on-focus="true"
                    :placeholder="$t('dataTypeAuthorizations.data-groups-placeholder')"
                    :value="dataGroups.id"
                    autocomplete
                    class="column"
                    field="label"
                    type="is-primary"
                    @remove.capture="()=>selectCheckbox($event,index, indexColumn, scope)"
                    @input.capture="selectCheckbox($event,index, indexColumn, scope)">
                </b-taginput>
                <div v-if="states && states[indexColumn] && states[indexColumn][index]==1&&
                    localAuthorizationsTree && localAuthorizationsTree[indexColumn] && localAuthorizationsTree[indexColumn][index]"
                     class="column">
                  <b-datepicker
                      v-model="localAuthorizationsTree[indexColumn][index].from"
                      :date-parser="parseDate"
                      :placeholder="
                            $t('dataTypesRepository.placeholder-datepicker') +
                            ' dd-MM-YYYY, dd-MM-YYYY hh, dd-MM-YYYY hh:mm, dd-MM-YYYY hh:mm:ss'
                          "
                      editable
                      icon="calendar"
                      @input="selectCheckbox($event,index, indexColumn, scope, 'from')"
                  >
                  </b-datepicker>
                </div>
                <div v-if="states && states[indexColumn] && states[indexColumn][index]==1&&
                    localAuthorizationsTree && localAuthorizationsTree[indexColumn] && localAuthorizationsTree[indexColumn][index]"
                     class="column">
                  <b-datepicker
                      v-model="localAuthorizationsTree[indexColumn][index].to"
                      :date-parser="parseDate"
                      :placeholder="
                            $t('dataTypesRepository.placeholder-datepicker') +
                            ' dd-MM-YYYY, dd-MM-YYYY hh, dd-MM-YYYY hh:mm, dd-MM-YYYY hh:mm:ss'
                          "
                      editable
                      icon="calendar"
                      @input="selectCheckbox($event,index, indexColumn, scope, 'from')"
                  >
                  </b-datepicker>
                </div>
              </div>
            </b-field>
          </div>
        </div>
        <ul v-if="(authReference && (!scope.isLeaf || remainingOption.length) && open && open[index])" class="rows">
          <AuthorizationTable
              v-if="authorizationByScope && authReference"
              :authReference="getNextAuthreference(scope)"
              :authorizations-tree="authorizationByScope && authorizationByScope[index]"
              :columnsVisible="columnsVisible"
              :dataGroups="dataGroups"
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
import {Component, Prop, Vue} from "vue-property-decorator";
import {FontAwesomeIcon} from "@fortawesome/vue-fontawesome";
import {Authorization} from "@/model/authorization/Authorization";

@Component({
  components: {FontAwesomeIcon},
})
export default class AuthorizationTable extends Vue {
  STATES = {'-1': 'minus-square', '0': 'square', '1': 'check-square'}
  EXTRACTION = 'extraction';
  @Prop() authReference;
  @Prop() remainingOption;
  @Prop() columnsVisible;
  @Prop({default: ''}) path;
  @Prop() authorizationsTree;
  @Prop() dataGroups;
  name = 'AuthorizationTable'
  localAuthorizationsTree = this.authorizationsTree || {}
  authorizationByScope = {}
  states = {}
  statesIcons = {}
  open = {}
  emits = ["add-authorization", "delete-authorization"];
  upHere = false;

  addDataGroup(tag, index, indexColumn, scope) {
    console.log(tag, index, indexColumn, scope);
    var tree = this.localAuthorizationsTree;
    tree[indexColumn][index].dataGroups = [...(tree[indexColumn][index].dataGroups || [])]
    tree[indexColumn][index].dataGroups.includes(tag) || tree[indexColumn][index].dataGroups.push(tag)
    this.localAuthorizationsTree = {...tree}
    /*this.$emit('add-authorization',
        {
          state: 1,
          child: index,
          type: indexColumn,
          authorizationsTree: this.localAuthorizationsTree,
          authorizationScope: tree[indexColumn][index]
        }
    )*/
    return false;
  }

  created() {
    this.updateAuthorizationTree();
  }

  updateAuthorizationTree() {
    this.initAuthorizationByScope()
    this.initStates();
    this.initOpen()
    this.$children
        .filter(child => child.name == 'AuthorizationTable')
        .forEach(child => child.updateAuthorizationTree())
  }

  initAuthorizationByScope() {
    this.localAuthorizationsTree = this.authorizationsTree;
    let authorizationByScope = this.authorizationByScope;
    for (const reference in this.authReference) {
      for (const type in this.authorizationsTree) {
        if (this.authorizationsTree?.[type]?.[reference]) {
          authorizationByScope[reference] = authorizationByScope[reference] || {}
          if (this.authorizationsTree?.[type]?.[reference] instanceof Authorization) {
            let nextReference = this.getNextAuthreference(this.authReference[reference]);
            var auth = {}
            for (const ref in nextReference) {
              auth[ref] = new Authorization(this.authorizationsTree[type][reference])
            }
            authorizationByScope[reference][type] = auth
          } else {
            authorizationByScope[reference][type] = new Authorization(this.authorizationsTree?.[type]?.[reference]);
          }
        }
      }
    }
    this.authorizationByScope = {...authorizationByScope};
  }

  initStates() {
    if (!this.authReference) return
    var states = {}
    var statesIcons = {}
    for (var index in this.columnsVisible) {
      if ('label' == index) {
        continue;
      }
      states[index] = {};
      statesIcons[index] = {};
      for (var reference in this.authReference) {
        var state = 0;
        if (!this.localAuthorizationsTree) {
          state = 0
        } else if (this.localAuthorizationsTree?.[index] instanceof Authorization) {
          state = 1
        } else if (this.localAuthorizationsTree?.[index]?.[reference]) {
          if (this.localAuthorizationsTree?.[index]?.[reference] instanceof Authorization) {
            state = 1;
          } else {
            state = -1;
          }
        }
        states[index][reference] = state
        statesIcons[index][reference] = this.STATES[state];
        //this.updateState()
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
    if (this.remainingOption.length == 0 && this.authReference[reference].isLeaf) {
      return
    }
    this.getChildAuthorizationTable()
        .forEach(child => {
          child.states[type] || child.initStates()
          if (child.states[type]) {
            for (const childType in child.states[type]) {
              child.updateState(type, childType, value)
            }
          }
        })
  }

  getChildAuthorizationTable() {
    return this.$children
        .filter(child => child.name == 'AuthorizationTable');
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
    this.localAuthorizationsTree = this.authorizationsTree
  }

  getPath(index) {
    return this.path + (this.path ? '.' : '') + index
  }

  localName(scope) {
    return scope.localName || (this.authReference.authorizationScope && this.authReference.authorizationScope.localName) || 'pas trouve'
  }

  toggle(index) {
    if (!this.open[index]) {
      this.initAuthorizationByScope()
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
    this.localAuthorizationsTree = {...localAuthorizationsTree};
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
    if (localAuthorizationsTree[event.type][index] instanceof Authorization) {
      this.changeChildrenAuthorization(event.authorizationScope, index);
    } else {
      localAuthorizationsTree[event.type][index][event.child] = new Authorization(event.authorizationScope)
    }
    this.localAuthorizationsTree = {...localAuthorizationsTree};
    this.updateState(event.type, index, event.state)
    if (this.EXTRACTION == event.type) {
      this.testAllChildrenEquals(index)
    }
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

  changeChildrenAuthorization(authorization, exceptIndex) {
    this.getChildAuthorizationTable()
        .filter(child => {
          return !(exceptIndex && child.path.endsWith(exceptIndex));
        })
        .forEach(child => {
          var authorizationTree = child.localAuthorizationsTree || {};
          for (const index in authorizationTree[child.EXTRACTION] || {}) {
            if (!authorization && authorizationTree?.[child.EXTRACTION]?.[index]) {
              delete authorizationTree?.[child.EXTRACTION]?.[index]
            } else {
              authorizationTree[child.EXTRACTION] = authorizationTree[child.EXTRACTION] || {}
              authorizationTree[child.EXTRACTION][index] = new Authorization(authorization)
            }
          }
          child.localAuthorizationsTree = authorizationTree;
          child.changeChildrenAuthorization(authorization, child.EXTRACTION, exceptIndex);
          child.initAuthorizationByScope();
        })
  }

  selectCheckbox(event, index, indexColumn, scope, fromOrTo) {
    var eventType = 'add-authorization'
    let localAuthorizationsTree = this.localAuthorizationsTree || {};
    var actualState = this.states && this.states[indexColumn] && this.states[indexColumn][index] || 0
    if (event instanceof PointerEvent) { //cliock sur checkbox
      this.states[indexColumn] || this.initStates()
      var states, state
      if (actualState == 1) { //je supprime l'authorization et eventuellement son contenant
        delete localAuthorizationsTree?.[indexColumn]?.[index]
        if (!Object.keys(localAuthorizationsTree?.[indexColumn]).length) {
          delete localAuthorizationsTree?.[indexColumn]
          delete this.authorizationByScope?.[index]?.[indexColumn]
        }
        eventType = 'delete-authorization'
        state = 0;
      } else { //création ou modification
        localAuthorizationsTree[indexColumn] = localAuthorizationsTree?.[indexColumn] || {}
        localAuthorizationsTree[indexColumn][index] = new Authorization([], null, null);
        var authorizationScope = {}
        let id = scope.authorizationScope;
        authorizationScope[id] = scope.key;
        state = 1
      }

    }
    if (this.EXTRACTION == indexColumn) {
      if (event instanceof Array) { //c'est un datagroup
        state = event.length ? 1 : 0
        eventType = event.length ? 'add-authorization' : 'delete-authorization'
        localAuthorizationsTree[indexColumn][index].dataGroups = event

        // si indeterminate alors je ne supprime les enfants que
      } else if (event instanceof Date) {//c'est une date
        state = event ? 1 : 0
        eventType = event ? 'add-authorization' : 'delete-authorization'
        localAuthorizationsTree[indexColumn][index][fromOrTo] = event
      }
      //si je veux restreindre les enfants je dois le faire après avoir défini le parent
      this.changeChildrenAuthorization(localAuthorizationsTree?.[indexColumn]?.[index]);//si je selectionne alors c'est cette authorization qui s'applique aux enfants (ils n'ont plus leur propre authorization
    }
    this.updateState(indexColumn, index, state)
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
    this.$emit(eventType,
        {
          state: state,
          child: index,
          type: indexColumn,
          authorizationsTree: this.localAuthorizationsTree,
          authorizationScope: localAuthorizationsTree?.[indexColumn]?.[index]
        }
    )
    this.localAuthorizationsTree = localAuthorizationsTree
    this.authorizationsTree = localAuthorizationsTree
    this.initAuthorizationByScope()
  }

  testAllChildrenEquals(index) {
    var isEqual = {equal: true}
    var childSize = Object.keys(this.getRemainingOption(index)?.[0] || {}).length
    var localAuthorizationsTree = this.localAuthorizationsTree || {}
    this.getChildAuthorizationTable()
        .filter(child => child.path.endsWith(index))
        .forEach(child => {
          if (Object.keys(child.localAuthorizationsTree?.[child.EXTRACTION] || {}).length != childSize) {
            isEqual.equal = false
            delete isEqual.auth
          } else {
            for (const i in child.localAuthorizationsTree?.[child.EXTRACTION]) {
              var auth = child.localAuthorizationsTree[child.EXTRACTION][i]
              if (isEqual.equal) {
                if (isEqual.auth) {
                  isEqual.equal = auth &&
                      JSON.stringify(isEqual.auth.dataGroups) == JSON.stringify(auth.dataGroups) &&
                      isEqual.auth.from?.toString() == auth.from?.toString() &&
                      isEqual.auth.to?.toString() == auth.to?.toString()

                } else {
                  isEqual.auth = auth;
                }
              }
            }
          }
        })
    if (isEqual.equal && isEqual.auth) {
      localAuthorizationsTree[this.EXTRACTION][index] = new Authorization(isEqual.auth)
      this.changeChildrenAuthorization(localAuthorizationsTree[this.EXTRACTION][index])
    }
    this.localAuthorizationsTree = localAuthorizationsTree
    console.log(isEqual)
    return isEqual
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

  dgSelected {
    color: #007F7F;
  }
}
</style>