<template>
  <div>
    <div
      :class="`CollapsibleTree-header ${children && children.length !== 0 ? 'clickable' : ''} ${
        children && children.length !== 0 && displayChildren ? '' : 'mb-1'
      }`"
      :style="`background-color:rgba(240, 245, 245, ${1 - level / 2})`"
      @click="displayChildren = !displayChildren"
    >
      <div class="CollapsibleTree-header-infos">
        <FontAwesomeIcon
          v-if="children && children.length !== 0"
          :icon="displayChildren ? 'caret-down' : 'caret-right'"
          class="clickable mr-3"
        />
        <div
          class="link"
          :style="`transform:translate(${level * 50}px);`"
          @click="(event) => onClickLabelCb(event, label)"
        >
          {{ label }}
        </div>
      </div>
      <div class="CollapsibleTree-buttons">
        <b-field class="file button is-small is-info" v-if="onUploadCb">
          <b-upload
            v-model="refFile"
            class="file-label"
            accept=".csv"
            @input="() => onUploadCb(label, refFile)"
          >
            <span class="file-name" v-if="refFile">
              {{ refFile.name }}
            </span>
            <span class="file-cta">
              <b-icon class="file-icon" icon="upload"></b-icon>
            </span>
          </b-upload>
        </b-field>
        <div v-for="button in buttons" :key="button.id">
          <b-button
            :icon-left="button.iconName"
            size="is-small"
            @click="button.clickCb(label)"
            class="ml-1"
            :type="button.type"
          >
            {{ button.label }}</b-button
          >
        </div>
      </div>
    </div>
    <div v-if="displayChildren">
      <CollapsibleTree
        v-for="child in children"
        :key="child.id"
        :label="child.label"
        :children="child.children"
        :level="level + 1"
        :onClickLabelCb="onClickLabelCb"
        :onUploadCb="onUploadCb"
        :buttons="buttons"
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
  @Prop() onClickLabelCb;
  @Prop() onUploadCb;
  @Prop() buttons;

  displayChildren = false;
  refFile = null;
}
</script>

<style lang="scss" scoped>
$row-height: 40px;

.CollapsibleTree-header {
  display: flex;
  align-items: center;
  height: $row-height;
  padding: 0.75rem;
  justify-content: space-between;

  .file-icon {
    margin-right: 0;
  }

  .file-name {
    border: none;
    height: 100%;
    display: inherit;
    border-right: solid 2px;
    border-radius: 0;
    padding-right: 0 0.5em;
    background-color: rgba(255, 255, 255, 0.2);

    &:hover {
      opacity: 1;
    }
  }
}

.CollapsibleTree-header-infos {
  display: flex;
  align-items: center;
}

.CollapsibleTree-buttons {
  display: flex;
  height: $row-height;
  align-items: center;

  .file {
    margin-bottom: 0;

    .file-cta {
      height: 100%;
      border-color: transparent;
    }
  }
}
</style>
