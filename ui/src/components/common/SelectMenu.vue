<template>
  <div>
    <details v-if="!option.isLeaf" :open="open" class="selectMenu" @toggle="toggle">
      <summary class="">{{ option.localName }}
        <b-checkbox v-model="selected"
                    :indeterminate="!selected && indeterminate"
                    @input="select(option.currentPath, $event)"/>
      </summary>
      <div v-show="open">
        <select-menu
            v-for="(opt, itemKey) in option.referenceValues"
            :key="itemKey"
            :selected-pathes="value"
            :auth="auth"
            :option="opt"
            @select-menu-item="select($event.path, $event.selected)"
        />
      </div>
    </details>
    <div v-else class="selectMenu">
      {{ option.localName }}
      <b-checkbox v-model="selected"
                  :indeterminate="!selected && indeterminate"
                  @input="select(option.currentPath, $event)"/>
    </div>
  </div>
</template>

<script>
import SelectMenu from "@/components/common/SelectMenu";
//import { FontAwesomeIcon } from "@fortawesome/vue-fontawesome";

export default {
  name: "selectMenu",
  components: {SelectMenu},
  props: {
    option: {},
    auth: String,
    value: Object,
  },

  watch: {
    value: {
      immediate: true,
      deep: true,
      handler(value) {
        this.updateIsSelected(value)
      }
    }
  },
  data() {
    return {
      emits: ["select-menu-item"],
      open: null,
      checkbox: false,
      selected: false,
      indeterminate: false,
    };
  },
  methods: {
    select: function (key, option) {
      this.$emit("select-menu-item", {path: key, selected: option});
    },
    toggle() {
      this.open = this.open ? null : "open";
    },
    updateIsSelected(value) {
      let selected, indeterminate;
      if (value && value[this.auth]) {
        selected = value[this.auth].some(a => this.option.currentPath.startsWith(a))
        indeterminate = value[this.auth].some(a => a != this.option.currentPath && a.startsWith(this.option.currentPath))
      }
      let updated = false;
      if (!!selected != !!this.selected) {
        this.selected = !!selected;
        this.indeterminate = !!indeterminate;
        updated=true;
      }
      if (!!this.indeterminate != !!indeterminate) {
        this.indeterminate = indeterminate
        updated=true;
      }
      if (updated){
        this.$children
            .filter(child=>child.updateIsSelected)
            .forEach(child=>child.updateIsSelected(value))
      }
    },
  }
};
</script>

<style lang="scss" scoped>
span.check {
  margin: 5px;
}

.selectMenu {
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
