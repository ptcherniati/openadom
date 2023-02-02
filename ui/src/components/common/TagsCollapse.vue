<template>
  <b-collapse
    class="card"
    :open="open"
    animation="slide"
    aria-id="contentIdForA11y3"
  >
    <template #trigger="props">
      <div
        class="card-header"
        role="button"
        aria-controls="contentIdForA11y3"
        :aria-expanded="props.open"
      >
        <p class="card-header-title">
          {{ $t("tags.tag") }}
        </p>
        <a class="card-header-icon">
          <b-icon :icon="props.open ? 'chevron-down' : 'chevron-up'"> </b-icon>
        </a>
      </div>
    </template>
    <div class="card-content">
      <div class="content columns" style="display: flex; flex-wrap: wrap;">
        <b-field class="column is-narrow" v-for="(tag, index) in tags" :key="index">
          <b-switch v-model="tag.selected" passive-type="is-light" type="is-dark">
            {{ tag.localName === "no-tag" ? $t("tags.no-tag") : tag.localName }}
          </b-switch>
        </b-field>
      </div>
    </div>
  </b-collapse>

</template>

<script>
import { Component,Prop, Vue } from "vue-property-decorator";
@Component({
  components: {},
})
export default class TagsCollapse extends Vue {
  @Prop() tags;
  open = false;
}
</script>

<style scoped>

</style>