<template>
  <div>
    <li class="card-content authorizationTable datepicker-row">
      <slot class="row"></slot>
      <div v-for="(scope, index) of authReference" :key="index">
        <div class="columns">
          <div v-for="(column, indexColumn) of columnsVisible" :key="indexColumn" class="column">
            <b-button v-if="column.display && indexColumn=='label' && (!scope.isLeaf || remainingOption.length)" :class="(!scope.isLeaf || remainingOption.length)?'leaf':'folder'"
                     :field="indexColumn" :label="localName(scope)"
                      @click="indexColumn=='label' && toggle(index)"/>
            <b-field v-else-if="column.display && indexColumn=='label' && !(!scope.isLeaf || remainingOption.length)" :class="(!scope.isLeaf || remainingOption.length)?'leaf':'folder'"
                     :field="indexColumn" :label="localName(scope)" />
            <b-field v-else-if="column.display && indexColumn!='date'" :field="indexColumn">
              <b-checkbox @input="selectCheckbox($event, indexColumn, scope)"/>
            </b-field>
            <b-field v-else-if="column.display && indexColumn=='date'" :field="indexColumn">
              <b-radio/>
            </b-field>
          </div>
        </div>
        <ul v-show="(!scope.isLeaf || remainingOption.length) && open[index]" class="rows">
          <AuthorizationTable
              :authReference="getNextAuthreference(scope)"
              :columnsVisible="columnsVisible"
              :remaining-option="getRemainingOption(scope)"
              v-on:selected-checkbox="emitSelectedCheckbox($event,  scope)"/>
        </ul>
      </div>
    </li>
  </div>
</template>

<script>
import {Component, Prop, Vue} from "vue-property-decorator";
import {FontAwesomeIcon} from "@fortawesome/vue-fontawesome";

@Component({
  components: {FontAwesomeIcon},
})
export default class AuthorizationTable extends Vue {
  @Prop() authReference;
  @Prop() remainingOption;
  @Prop() columnsVisible;
  initialized = false;
  open = {}
  emits = ["selected-checkbox"];

  mounted() {
  }

  init() {
    if (this.initialized) {
      return;
    }
    if (this?.authReference && !this?.authReference?.hierarchicalKey) {
      for (const index in this.authReference) {
        if (!this.authReference[index].isLeaf || this.remainingOption.length)
        this.open[index] = false;
      }
    }
    this.initialized = !this.initialized;
  }
  localName(scope){
    return scope.localName || (this.authReference.authorizationScope && this.authReference.authorizationScope.localName) || 'pas trouve'
  }

  toggle(index) {
    this.init();
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
      return this.remainingOption.length?this.remainingOption[0] :scope.referenceValues ;
    }
  }

  getRemainingOption(scope) {
    if (scope.isLeaf) {
      return this.remainingOption.slice(1, this.remainingOption.length)
    } else {
      return this.remainingOption;
    }
  }

  selectCheckbox(event, indexColumn, scope) {
    var authorizationScope = {}
    let id = scope.authorizationScope;
    authorizationScope[id] = scope.key;
    {
      this.$emit('selected-checkbox',
          {
            checked: event,
            type: indexColumn,
            authorizationScope: authorizationScope
          }
      )
    }
  }

  emitSelectedCheckbox(event,scope) {
    let id = scope.authorizationScope;
    if (event.authorizationScope[id]==null){
      event.authorizationScope[id]=scope.key
    }else{
      event.authorizationScope[id] =scope.key+'.'+event.authorizationScope[id]
    }
        this.$emit('selected-checkbox', event);
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