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
                :icon="
                  (statesIcons && statesIcons[indexColumn] && statesIcons[indexColumn][index]) ||
                  'square'
                "
                size="is-medium"
                type="is-primary"
                @click.native="selectCheckbox($event, index, indexColumn, scope)"
              />
            </b-field>
            <b-field
              v-else-if="column.display && indexColumn === 'extraction'"
              :field="indexColumn"
              class="columns "
              style="margin-top: 6px"
            >
              <div class="column">
                <b-icon
                  :icon="
                    (statesIcons && statesIcons[indexColumn] && statesIcons[indexColumn][index]) ||
                    'square'
                  "
                  size="is-medium"
                  type="is-primary"
                  @click.native="selectCheckbox($event, index, indexColumn, scope)"
                />
                <div class="columns">
                  <b-taginput
                    v-if="
                      states &&
                      states[indexColumn] &&
                      states[indexColumn][index] === 1 &&
                      localAuthorizationsTree &&
                      localAuthorizationsTree[indexColumn] &&
                      localAuthorizationsTree[indexColumn][index]
                    "
                    v-model="localAuthorizationsTree[indexColumn][index].dataGroups"
                    :data="dataGroups"
                    :open-on-focus="true"
                    :placeholder="$t('dataTypeAuthorizations.data-groups-placeholder')"
                    :value="dataGroups.id"
                    autocomplete
                    class="column"
                    field="label"
                    type="is-primary"
                    @input.capture="selectCheckbox($event, index, indexColumn, scope)"
                  >
                  </b-taginput>
                  <div
                    v-if="
                      states &&
                      states[indexColumn] &&
                      states[indexColumn][index] === 1 &&
                      localAuthorizationsTree &&
                      localAuthorizationsTree[indexColumn] &&
                      localAuthorizationsTree[indexColumn][index]
                    "
                    class="column"
                  >
                    <b-datepicker
                      v-model="localAuthorizationsTree[indexColumn][index].from"
                      :date-parser="parseDate"
                      :placeholder="
                        $t('dataTypesRepository.placeholder-datepicker') +
                        ' dd-MM-YYYY, dd-MM-YYYY hh, dd-MM-YYYY hh:mm, dd-MM-YYYY hh:mm:ss'
                      "
                      editable
                      icon="calendar"
                      @remove.capture="() => selectCheckbox($event, index, indexColumn, scope)"
                      @input.capture="selectCheckbox($event, index, indexColumn, scope, 'from')"
                    >
                    </b-datepicker>
                  </div>
                  <div
                    v-if="
                      states &&
                      states[indexColumn] &&
                      states[indexColumn][index] === 1 &&
                      localAuthorizationsTree &&
                      localAuthorizationsTree[indexColumn] &&
                      localAuthorizationsTree[indexColumn][index]
                    "
                    class="column"
                  >
                    <b-datepicker
                      v-model="localAuthorizationsTree[indexColumn][index].to"
                      :date-parser="parseDate"
                      :placeholder="
                        $t('dataTypesRepository.placeholder-datepicker') +
                        ' dd-MM-YYYY, dd-MM-YYYY hh, dd-MM-YYYY hh:mm, dd-MM-YYYY hh:mm:ss'
                      "
                      editable
                      icon="calendar"
                      @input="selectCheckbox($event, index, indexColumn, scope, 'to')"
                    >
                    </b-datepicker>
                  </div>
                </div>
              </div>
            </b-field>
          </div>
        </div>
        <ul
          v-if="authReference && (!scope.isLeaf || remainingOption.length) && open && open[index]"
          class="rows"
        >
          <AuthorizationTable
            v-if="authorizationByScope && authReference"
            :authReference="getNextAuthreference(scope)"
            :authorization-scopes="remainingScopes[index]"
            :authorizations-tree="authorizationByScope && authorizationByScope[index]"
            :columnsVisible="columnsVisible"
            :dataGroups="dataGroups"
            :path="getPath(index)"
            :remaining-option="getRemainingOption(scope)"
            :required-authorizations="requiredAuthorizationByindex[index]"
            @add-authorization="emitAddAuthorization($event, index)"
            @delete-authorization="emitDeleteAuthorization($event, index)"
          />
        </ul>
      </div>
    </li>
  </div>
</template>

<script>
import { Component, Prop, Vue } from "vue-property-decorator";
import { FontAwesomeIcon } from "@fortawesome/vue-fontawesome";
import { Authorization } from "@/model/authorization/Authorization";

@Component({
  components: { FontAwesomeIcon },
})
export default class AuthorizationTable extends Vue {
  STATES = { "-1": "minus-square", 0: "square", 1: "check-square" };
  EXTRACTION = "extraction";
  @Prop() authReference;
  @Prop() remainingOption;
  @Prop() columnsVisible;
  @Prop({ default: "" }) path;
  @Prop() authorizationsTree;
  @Prop() dataGroups;
  @Prop() requiredAuthorizations;
  @Prop() authorizationScopes;
  name = "AuthorizationTable";
  authorizationByScope = {};
  states = {};
  statesIcons = {};
  open = {};
  emits = ["add-authorization", "delete-authorization"];
  upHere = false;
  requiredAuthorizationByindex = {};
  remainingScopes = {};

  created() {
    this.updateAuthorizationTree();
  }

  getRequiredAuthorization() {
    var requiredAuthorizationByIndex = {};
    var remainingScopes = this.remainingScopes || {};
    for (const index in this.authReference) {
      remainingScopes[index] = this.getNextScope(this.authReference?.[index]);
      var requiredAuthorization = { ...(this.requiredAuthorizations || {}) };
      let scope = this.getScope();
      if (scope) {
        var requiredAuthorizationForIndex = requiredAuthorization[scope] || "";
        requiredAuthorizationForIndex =
          requiredAuthorizationForIndex + (requiredAuthorizationForIndex === "" ? "" : ".") + index;
        requiredAuthorization[this.authorizationScopes[0].id] = requiredAuthorizationForIndex;
      }
      requiredAuthorizationByIndex[index] = requiredAuthorization;
    }
    this.remainingScopes = remainingScopes;
    this.requiredAuthorizationByindex = requiredAuthorizationByIndex;
  }

  getScope() {
    return this.authorizationScopes?.[0]?.id;
  }

  updateAuthorizationTree() {
    this.initAuthorizationByScope();
    this.initStates();
    this.initOpen();
    this.$children
      .filter((child) => child.name === "AuthorizationTable")
      .forEach((child) => child.updateAuthorizationTree());
  }

  initAuthorizationByScope(authorizationsTree) {
    this.localAuthorizationsTree = authorizationsTree || this.authorizationsTree;
    let authorizationByScope = this.authorizationByScope;
    for (const reference in this.authReference) {
      for (const type in this.authorizationsTree) {
        if (this.authorizationsTree?.[type]?.[reference]) {
          authorizationByScope[reference] = authorizationByScope[reference] || {};
          if (this.authorizationsTree?.[type]?.[reference] instanceof Authorization) {
            let nextReference = this.getNextAuthreference(this.authReference[reference]);
            var auth = {};
            for (const ref in nextReference) {
              auth[ref] = new Authorization(
                this.authorizationsTree[type][reference],
                this.requiredAuthorizationByindex[reference]
              );
            }
            authorizationByScope[reference][type] = auth;
          } else {
            authorizationByScope[reference][type] = new Authorization(
              this.authorizationsTree?.[type]?.[reference],
              this.requiredAuthorizationByindex[reference]
            );
          }
        }
      }
      authorizationByScope[reference] = authorizationByScope[reference] || {};
      authorizationByScope[reference][this.EXTRACTION] =
        authorizationByScope[reference][this.EXTRACTION] ||
        (this.authorizationsTree?.[this.EXTRACTION]?.[reference] &&
          new Authorization(
            this.authorizationsTree[this.EXTRACTION][reference],
            this.requiredAuthorizationByindex[reference]
          ));
    }
    this.authorizationByScope = { ...authorizationByScope };
    this.getRequiredAuthorization();
  }

  initStates() {
    if (!this.authReference) return;
    var states = {};
    var statesIcons = {};
    for (var index in this.columnsVisible) {
      if ("label" === index) {
        continue;
      }
      states[index] = {};
      statesIcons[index] = {};
      for (var reference in this.authReference) {
        var state = 0;
        if (!this.localAuthorizationsTree) {
          state = 1;
        } else if (this.localAuthorizationsTree?.[index] instanceof Authorization) {
          state = 1;
        } else if (this.localAuthorizationsTree?.[index]?.[reference]) {
          if (this.localAuthorizationsTree?.[index]?.[reference] instanceof Authorization) {
            state = 1;
          } else {
            state = -1;
          }
        }
        states[index][reference] = state;
        statesIcons[index][reference] = this.STATES[state];
        //this.updateState()
      }
    }
    this.states = states;
    this.statesIcons = statesIcons;
  }

  getStateIcon(index, indexColumn) {
    this.states[indexColumn] || this.initStates();
    let states = this.states[indexColumn];
    if (states && Object.values(states).every((value) => value)) {
      return "check-square";
    } else if (!states || !Object.values(this.states).reduce((acc, value) => acc || value, false)) {
      return "square";
    }
    return "minus-square";
  }

  updateState(type, reference, value, updateChildren) {
    this.states[type] || this.initStates();
    var states = this.states;
    var statesIcons = this.statesIcons;
    states[type][reference] = value;
    statesIcons[type][reference] = this.STATES[value];
    this.states = states;
    this.statesIcons = statesIcons;
    if (value === -1) return;
    if (this.remainingOption.length === 0 && this.authReference[reference].isLeaf) {
      return;
    }
    if (updateChildren) {
      this.getChildAuthorizationTable().forEach((child) => {
        child.states[type] || child.initStates();
        if (child.states[type]) {
          for (const childType in child.states[type]) {
            child.updateState(type, childType, value, updateChildren);
          }
        }
      });
    }
  }

  getChildAuthorizationTable() {
    return this.$children.filter((child) => child.name === "AuthorizationTable");
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

  localName(scope) {
    return (
      scope.localName ||
      (this.authReference.authorizationScope && this.authReference.authorizationScope.localName) ||
      "pas trouve"
    );
  }

  toggle(index) {
    if (!this.open[index]) {
      this.initAuthorizationByScope();
    }
    var open = {};
    open[index] = !this.open[index];
    this.open = { ...this.open, ...open };
  }

  select(option) {
    this.$emit("select-menu-item", option || this.option);
  }

  getNextAuthreference(scope) {
    if (!scope.isLeaf) {
      return scope.referenceValues;
    } else {
      return this.remainingOption.length ? this.remainingOption[0] : scope.referenceValues;
    }
  }

  getNextScope(scope) {
    if (!scope.isLeaf) {
      return this.authorizationScopes;
    } else {
      return (this.authorizationScopes || []).slice(1, (this.authorizationScopes || []).length);
    }
  }

  getRemainingOption(scope) {
    if (scope.isLeaf) {
      return this.remainingOption.slice(1, this.remainingOption.length);
    } else {
      return this.remainingOption;
    }
  }

  emitDeleteAuthorization(event, index) {
    let localAuthorizationsTree = this.localAuthorizationsTree || {};
    localAuthorizationsTree[event.indexColumn] = localAuthorizationsTree?.[event.indexColumn] || {};
    localAuthorizationsTree[event.indexColumn][index] =
      localAuthorizationsTree?.[event.indexColumn][index] || {};
    delete localAuthorizationsTree[event.indexColumn][index][event.child];
    this.localAuthorizationsTree = { ...localAuthorizationsTree };
    this.updateState(event.indexColumn, index, event.state, false);
    this.$emit("delete-authorization", {
      indexColumn: event.indexColumn,
      child: index,
      state: this.buildState(event.indexColumn, index).state,
      authorizationsTree: this.localAuthorizationsTree,
      authorizationScope: localAuthorizationsTree?.[event.type]?.[index],
    });
  }

  emitAddAuthorization(event, index) {
    let localAuthorizationsTree = this.localAuthorizationsTree;
    var isEqual = event.isEqual;
    if (isEqual.state === 1) {
      localAuthorizationsTree = localAuthorizationsTree || {};
      localAuthorizationsTree[event.indexColumn] = localAuthorizationsTree[event.indexColumn] || {};
      localAuthorizationsTree[event.indexColumn][index] =
        localAuthorizationsTree[event.indexColumn][index] || {};
      isEqual.auth.requiredAuthorization = this.requiredAuthorizationByindex[index];
      localAuthorizationsTree[event.indexColumn][index] = isEqual.auth;
      isEqual = this.buildState(event.indexColumn, index);
    } else if (isEqual.state === -1) {
      localAuthorizationsTree = localAuthorizationsTree || {};
      localAuthorizationsTree[event.indexColumn] = localAuthorizationsTree[event.indexColumn] || {};
      localAuthorizationsTree[event.indexColumn][index] =
        localAuthorizationsTree[event.indexColumn][index] || {};
      localAuthorizationsTree[event.indexColumn][index] =
        event.authorizationsTree[event.indexColumn];
    } else {
      delete localAuthorizationsTree?.[event.indexColumn]?.[index];
      if (
        localAuthorizationsTree?.[event.indexColumn] &&
        Object.keys(localAuthorizationsTree[event.indexColumn]).length
      ) {
        delete localAuthorizationsTree[event.indexColumn];
      }
    }
    this.localAuthorizationsTree = localAuthorizationsTree
      ? { ...(localAuthorizationsTree || {}) }
      : localAuthorizationsTree;
    this.updateState(event.indexColumn, index, event.state, false, this.localAuthorizationsTree);
    this.authorizationByScope = this.localAuthorizationsTree[event.indexColumn];
    this.$emit("add-authorization", {
      isEqual,
      state: this.buildState(event.indexColumn, index).state,
      child: index,
      indexColumn: event.indexColumn,
      authorizationsTree: this.localAuthorizationsTree,
      authorizationScope: { ...localAuthorizationsTree?.[event.indexColumn]?.[index] },
    });
  }

  changeChildrenAuthorization(authorization, onlyIndex) {
    var returnAuthorizationTree = {};
    this.getChildAuthorizationTable()
      .filter((child) => {
        return child.path.endsWith(onlyIndex);
      })
      .forEach((child) => {
        var authorizationTree = child.localAuthorizationsTree
          ? { ...(child.localAuthorizationsTree || {}) }
          : child.localAuthorizationsTree;
        for (const index in authorizationTree[child.EXTRACTION] || {}) {
          if (!authorization && authorizationTree?.[child.EXTRACTION]?.[index]) {
            delete authorizationTree?.[child.EXTRACTION]?.[index];
          } else {
            authorizationTree[child.EXTRACTION] = authorizationTree[child.EXTRACTION] || {};
            authorizationTree[child.EXTRACTION][index] = new Authorization(
              authorization,
              this.requiredAuthorizationByindex[index]
            );
          }
          returnAuthorizationTree[index] = authorizationTree[child.EXTRACTION][index];
        }
        child.localAuthorizationsTree = authorizationTree;
        child.changeChildrenAuthorization(authorization, onlyIndex);
      });
    return returnAuthorizationTree
      ? { ...(returnAuthorizationTree || {}) }
      : returnAuthorizationTree;
  }

  selectCheckbox(event, index, indexColumn, scope, fromOrTo) {
    var eventType = "add-authorization";
    let localAuthorizationsTree = this.localAuthorizationsTree
      ? { ...(this.localAuthorizationsTree || {}) }
      : this.localAuthorizationsTree;
    var actualState =
      (this.states && this.states[indexColumn] && this.states[indexColumn][index]) || 0;
    if (event instanceof PointerEvent) {
      //cliock sur checkbox
      this.states[indexColumn] || this.initStates();
      var states, state;
      if (actualState === 1) {
        //je supprime l'authorization et eventuellement son contenant
        delete localAuthorizationsTree?.[indexColumn]?.[index];
        if (
          localAuthorizationsTree?.[indexColumn] &&
          !Object.keys(localAuthorizationsTree[indexColumn]).length
        ) {
          delete localAuthorizationsTree?.[indexColumn];
          delete this.authorizationByScope?.[index]?.[indexColumn];
        }
        eventType = "delete-authorization";
        state = 0;
      } else {
        //création ou modification
        localAuthorizationsTree[indexColumn] = localAuthorizationsTree?.[indexColumn] || {};
        localAuthorizationsTree[indexColumn][index] = new Authorization(
          [],
          this.requiredAuthorizationByindex[index],
          null,
          null
        );
        var authorizationScope = {};
        let id = scope.authorizationScope;
        authorizationScope[id] = scope.key;
        state = 1;
      }
    } else if (event instanceof Array) {
      state = event.length ? 1 : 0;
      eventType = event.length ? "add-authorization" : "delete-authorization";
      localAuthorizationsTree[indexColumn][index].dataGroups = event;

      // si indeterminate alors je ne supprime les enfants que
    } else if (event instanceof Date) {
      state = event ? 1 : 0;
      eventType = event ? "add-authorization" : "delete-authorization";
      localAuthorizationsTree[indexColumn][index][fromOrTo] = event;
    }
    if (this.EXTRACTION === indexColumn) {
      if (event instanceof Array) {
        //c'est un datagroup
        state = event.length ? 1 : 0;
        eventType = event.length ? "add-authorization" : "delete-authorization";
        localAuthorizationsTree[indexColumn][index].dataGroups = event;

        // si indeterminate alors je ne supprime les enfants que
      } else if (event instanceof Date) {
        //c'est une date
        state = event ? 1 : 0;
        eventType = event ? "add-authorization" : "delete-authorization";
        localAuthorizationsTree[indexColumn][index][fromOrTo] = event;
      }
      //si je veux restreindre les enfants je dois le faire après avoir défini le parent
      this.changeChildrenAuthorization(localAuthorizationsTree?.[indexColumn]?.[index], index); //si je selectionne alors c'est cette authorization qui s'applique aux enfants (ils n'ont plus leur propre authorization
    }
    this.localAuthorizationsTree = localAuthorizationsTree
      ? { ...(localAuthorizationsTree || {}) }
      : localAuthorizationsTree;
    this.updateState(indexColumn, index, state, false);
    this.authorizationByScope = this.localAuthorizationsTree[indexColumn];

    states = this.states[indexColumn];
    if (states) {
      state = {};
      for (const stateKey in states) {
        state[states[stateKey]] = true;
      }
    }
    if (state[-1] || (state[0] && state[1])) {
      state = -1;
    } else if (state[1]) {
      state = 1;
    } else {
      state = 0;
    }
    var isEqual = this.buildState(indexColumn, index);
    this.$emit(eventType, {
      isEqual,
      state,
      child: index,
      indexColumn,
      authorizationsTree: localAuthorizationsTree
        ? { ...(localAuthorizationsTree || {}) }
        : localAuthorizationsTree,
      authorizationScope: localAuthorizationsTree?.[indexColumn]?.[index],
    });
  }

  buildState(indexColumn, index) {
    var isEqual = {
      equal: true,
      state: 0,
    };
    var localAuthorizationsTree = { ...this.localAuthorizationsTree };
    if (
      !localAuthorizationsTree[indexColumn] ||
      Object.keys(localAuthorizationsTree[indexColumn]).length === 0
    ) {
      isEqual.equal = true;
      isEqual.state = 0;
      isEqual.auth == null;
      return isEqual;
    }
    isEqual.auth = localAuthorizationsTree[indexColumn][index];
    for (const reference in this.authReference) {
      var auth = localAuthorizationsTree[indexColumn][reference];
      if (isEqual.equal) {
        if (isEqual.auth) {
          isEqual.equal =
            auth &&
            JSON.stringify(isEqual.auth.dataGroups) === JSON.stringify(auth.dataGroups) &&
            isEqual.auth.from?.toString() === auth.from?.toString() &&
            isEqual.auth.to?.toString() === auth.to?.toString();
        }
      }
    }
    if (isEqual.equal && isEqual.auth) {
      //tous les noeuds sont semblables
      isEqual.state = 1;
    } else if (isEqual.auth) {
      isEqual.state = -1;
    }
    this.localAuthorizationsTree = localAuthorizationsTree
      ? { ...(localAuthorizationsTree || {}) }
      : localAuthorizationsTree;
    return isEqual;
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
.column{
  padding: 6px;
}
</style>
