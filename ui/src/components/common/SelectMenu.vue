<template>
  <div>
    <details v-if="!option.isLeaf" :open="open" class="selectMenu" @toggle="toggle">
      <summary class="">{{ option.localName }}
        <b-checkbox v-model="checkbox"
                    :selected="selectedForCurrentpath"
            @input="select(option.currentPath, $event)"/>
      </summary>
      <div v-show="open">
        <select-menu
            v-for="(opt, itemKey) in option.referenceValues"
            :key="itemKey"
            :option="opt"
            :selected-pathes="selectedPathes"
            :auth = "auth"
            @select-menu-item="select($event.path, $event.selected)"
        />
      </div>
    </details>
    <div v-else class="selectMenu">
      {{ option.localName }}
      <b-checkbox v-model="checkbox"
                    :selected="selectedForCurrentpath"
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
    selectedPathes:Object,
    auth: String
  },
  data() {
    return {
      emits: ["select-menu-item"],
      open: null,
      checkbox: false,
    };
  },
  methods: {
    select: function (key, option) {
      this.$emit("select-menu-item", {path: key, selected: option});
    },
    toggle() {
      this.open = this.open ? null : "open";
    },
  },
  computed:{
    selectedForCurrentpath(){
      return this.selectedPathes[this.auth]  && this.selectedPathes[this.auth].find(a=>this.option.currentPath.startsWith(a))
    }
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