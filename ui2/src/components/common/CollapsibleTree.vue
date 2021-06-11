<template>
  <div>
    <div>
      <FontAwesomeIcon
        v-if="children && children.length !== 0"
        @click="displayChildren = !displayChildren"
        :icon="displayChildren ? 'caret-up' : 'caret-down'"
        class="clickable"
      />
      <div :style="`transform:translate(${level * 50}px);`">{{ label }}</div>
    </div>
    <div v-if="displayChildren">
      <CollapsibleTree
        v-for="child in children"
        :key="child.id"
        :label="child.label"
        :children="child.children"
        :level="level + 1"
      />
    </div>
  </div>
</template>

<script>
import { Component, Prop, Vue } from "vue-property-decorator";
import { FontAwesomeIcon } from "@fortawesome/vue-fontawesome";

@Component({
  components: { FontAwesomeIcon },
})
export default class CollapsibleTree extends Vue {
  @Prop() label;
  @Prop() children;
  @Prop() level;

  displayChildren = false;
}
</script>
