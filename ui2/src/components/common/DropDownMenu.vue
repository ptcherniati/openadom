<template>
  <div>
    <div class="dropDownMenu" >
      <b-dropdown-item
          v-if="option.isLeaf"
          :value="option.referenceValues"
          @click="select()">
        {{ option.localName }}
      </b-dropdown-item>
      <b-dropdown v-else v-on:select-menu-item="select" :ref="option.key">
        <template #trigger="{ active }">
          <b-button
              :icon-right="active ? 'chevron-up' : 'chevron-down'"
              :label="option.localName"
              type="is-primary"/>
        </template>
        <DropDownMenu
            v-for="(opt, itemKey) in option.referenceValues"
            :key="itemKey" :option="opt"
            v-on:select-menu-item="select"/>
      </b-dropdown>
    </div>
  </div>
</template>

<script>

import {Component, Prop, Vue} from "vue-property-decorator";
import {FontAwesomeIcon} from "@fortawesome/vue-fontawesome";

@Component({
  components: {FontAwesomeIcon},
})
export default class DropDownMenu extends Vue {
  @Prop() option;
  emits = ['select-menu-item'];

  select(option) {
    this.$emit("select-menu-item", option || this.option);
  }
}
</script>

<style lang="scss" scoped>
.dropDownMenu {
  margin-left: 10px;
  background-color: rgba(206, 245, 245, 0.45);
  border: solid rgba(206, 245, 245, 1);
}
</style>