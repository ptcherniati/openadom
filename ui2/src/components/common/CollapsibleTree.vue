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
      <b-field class="file is-primary" v-if="onUploadCb">
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

  displayChildren = false;
  refFile = null;
}
</script>

<style lang="scss" scoped>
.CollapsibleTree-header {
  display: flex;
  align-items: center;
  height: 40px;
  padding: 0.75rem;
  justify-content: space-between;

  .file-icon {
    margin-right: 0;
  }

  .file-name {
    border-top-style: none;
    border-right-style: none;
    border-bottom-style: none;
    border-left-width: 2px;
    border-radius: 0px;
    opacity: 0.8;
    background-color: rgba(250, 250, 250, 1);

    &:hover {
      opacity: 1;
    }
  }
}

.CollapsibleTree-header-infos {
  display: flex;
  align-items: center;
}
</style>
