<template>
  <div>
    <div
      :class="`CollapsibleTree-header ${
        option.children && option.children.length !== 0 ? 'clickable' : ''
      } ${option.children && option.children.length !== 0 && displayChildren ? '' : 'mb-1'}`"
      :style="`background-color:rgba(240, 245, 245, ${1 - level / 2})`"
      @click="displayChildren = !displayChildren"
    >
      <div class="CollapsibleTree-header-infos">
        <div class="CollapsibleTree-header-infos" :style="`transform:translate(${level * 50}px);`">
          <FontAwesomeIcon
            v-if="option.children && option.children.length !== 0"
            :icon="displayChildren ? 'caret-down' : 'caret-right'"
            class="clickable mr-3"
          />

          <b-radio
            v-if="withRadios"
            v-model="innerOptionChecked"
            :name="radioName"
            @click.native="stopPropagation"
            :native-value="option.id"
          >
            {{ option.localName || option.label }}
          </b-radio>
          <div
            v-else
            :class="onClickLabelCb ? 'link' : ''"
            @click="(event) => onClickLabelCb && onClickLabelCb(event, option.label)"
          >
            {{ option.localName || option.label }}
          </div>
        </div>
      </div>
      <div class="CollapsibleTree-buttons">
        <div class="file button is-small is-info" v-if="onUploadCb">
          <b-upload
            v-model="refFile"
            class="file-label"
            accept=".csv"
            @input="() => onUploadCb(option.label, refFile) && showChildren()"
          >
            <span class="file-name" v-if="refFile">
              {{ refFile.name }}
            </span>
            <span class="file-cta">
              <b-icon class="file-icon" icon="upload"></b-icon>
            </span>
          </b-upload>
        </div>
        <div v-else>
          <b-button
            size="is-small"
            class="ml-1"
            label="Gérer les jeux de données"
            @click="repositoryRedirect(option.label)"
            type="is-dark"
            outlined
          >
          </b-button>
        </div>
        <div v-for="button in buttons" :key="button.id">
          <b-button
            :icon-left="button.iconName"
            size="is-small"
            @click="button.clickCb(option.label)"
            class="ml-1"
            :type="button.type"
          >
            {{ button.label }}</b-button
          >
        </div>
      </div>
    </div>
    <CollapsibleTree
      v-for="child in option.children"
      :key="child.id"
      :option="child"
      :level="level + 1"
      :onClickLabelCb="onClickLabelCb"
      :onUploadCb="onUploadCb"
      :buttons="buttons"
      :class="displayChildren ? '' : 'hide'"
      :withRadios="withRadios"
      :radioName="radioName"
      @optionChecked="onInnerOptionChecked"
    />
  </div>
</template>

<script>
import { Component, Prop, Vue, Watch } from "vue-property-decorator";
import { FontAwesomeIcon } from "@fortawesome/vue-fontawesome";

@Component({
  components: { FontAwesomeIcon },
})
export default class CollapsibleTree extends Vue {
  @Prop() option;
  @Prop({ default: 0 }) level;
  @Prop() onClickLabelCb;
  @Prop() onUploadCb;
  @Prop() buttons;
  @Prop({ default: false }) withRadios;
  @Prop() radioName;
  @Prop() repository;
  @Prop() repositoryRedirect;

  displayChildren = false;
  refFile = null;
  innerOptionChecked = null;

  @Watch("innerOptionChecked")
  onInnerOptionChecked(value) {
    this.$emit("optionChecked", value);
  }

  stopPropagation(event) {
    event.stopPropagation();
  }
  showChildren() {
    this.displayChildren = true;
  }
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
    padding-right: 0.5em;
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
