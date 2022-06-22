<template>
  <div>
    <div class="dropDownMenu">
      <b-dropdown-item v-if="option.isLeaf" :value="option.referenceValues" @click="select()">
        {{ option.localName }}
      </b-dropdown-item>
      <b-dropdown v-else @select-menu-item="select" :ref="option.key" expanded>
        <template #trigger="{ active }">
          <b-button
            expanded
            :icon-right="active ? 'chevron-up' : 'chevron-down'"
            :label="option.localName"
            type="is-dark"
          />
        </template>
        <DropDownMenu
          v-for="(opt, itemKey) in option.referenceValues"
          :key="itemKey"
          :option="opt"
          @select-menu-item="select"
        />
      </b-dropdown>
    </div>
  </div>
</template>

<script>
//import { FontAwesomeIcon } from "@fortawesome/vue-fontawesome";

export default {

  name: "DropDownMenu",
  //components: {FontAwesomeIcon},
  props: {
    option: {}
  },
  data() {
    return {
      emits : ["select-menu-item"],
    }
  },
  methods:{
    select: function(option) {
      this.$emit("select-menu-item", option || this.option);
    }
  }
}
</script>

<style lang="scss" scoped>
.dropDownMenu {
  margin-left: 10px;
  margin-right: 10px;
  padding: 5px;
  button {
    background-color: rgba(0, 100, 100, 0.85);
  }
  .dropdown-menu .dropdown-content .dropDownMenu {
    button {
      background-color: rgba(0, 100, 100, 0.7);
    }
    .dropdown-menu .dropdown-content .dropDownMenu {
      button {
        background-color: rgba(0, 100, 100, 0.55);
        .dropdown-menu .dropdown-content .dropDownMenu {
          button {
            background-color: rgba(0, 100, 100, 0.4);
            .dropdown-menu .dropdown-content .dropDownMenu {
              button {
                background-color: rgba(0, 100, 100, 0.25);
                color: black;
              }
            }
          }
        }
      }
    }
  }
}
</style>