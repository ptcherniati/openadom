<template>
  <div>
    <li class="card-content authorizationTable datepicker-row">
      <slot class="row"></slot>
      <div v-for="(scope, index) of option" :key="index">
        <div class="columns" @click="deploy(scope)">
          <b-field field="label" :label="index" class="column"></b-field>
          <b-field class="column" field="admin">
            <b-checkbox > </b-checkbox></b-field>
          <b-field class="column" field="depot">
            <b-checkbox > </b-checkbox></b-field>
          <b-field class="column" field="publication">
            <b-checkbox > </b-checkbox></b-field>
          <b-field class="column" field="extraction">
            <b-checkbox > </b-checkbox></b-field>
          <b-field class="column" field="periodes"><b-radio></b-radio></b-field>
        </div>
        <ul v-show="showChildrens()" class="rows">
          <AuthorizationTable :option="scope.referenceValues" :remaining-option="getRemainingOption(scope)"/>
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
  @Prop() option;
  @Prop() remainingOption;
  open = false
  emits = ["select-menu-item"];

  showChildrens() {
    return this.open && this.remainingOption.length;
  }

  select(option) {
    this.$emit("select-menu-item", option || this.option);
  }

  deploy(scope) {
    if (!scope.isLeaf) {
      this.open = !this.open
    } else {
      alert("c'est une feuille")
    }
  }

  getRemainingOption(scope) {
    if (scope.isLeaf) {
      return this.remainingOption.slice(1, this.remainingOption.length)
    } else {
      return this.remainingOption;
    }
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