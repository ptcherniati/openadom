<template>
  <div style="margin-bottom: 10px; border: 1px solid white">
    <div
      :class="`columns CollapsibleTree-header ${
        option.children && option.children.length !== 0 ? 'clickable' : ''
      } ${option.children && option.children.length !== 0 && displayChildren ? '' : 'mb-1'}`"
      :style="`background-color:rgba(240, 245, 245, ${1 - level / 2})`"
      @click="displayChildren = !displayChildren"
      @keypress.enter="displayChildren = !displayChildren"
    >
      <div class="CollapsibleTree-header-infos column is-variable is-half-desktop is-three-quarters-widescreen">
        <div class="CollapsibleTree-header-infos column is-variable is-12-desktop is-5-widescreen" :style="`transform:translate(${level * 50}px);`">
          <FontAwesomeIcon
            v-if="option.children && option.children.length !== 0"
            :icon="displayChildren ? 'caret-down' : 'caret-right'"
            class="clickable mr-3"
            tabindex="0"
          />

          <b-checkbox
            v-if="withRadios"
            v-model="innerOptionChecked"
            :name="radioName"
            @click.native="stopPropagation"
            :native-value="option.id"
          >
            {{ option.localName || option.label }}
          </b-checkbox>
          <div
            v-else
            :class="onClickLabelCb ? 'link' : ''"
            @click="(event) => onClickLabelCb && onClickLabelCb(event, option.label)"
            @keypress.enter="(event) => onClickLabelCb && onClickLabelCb(event, option.label)"
            tabindex="0"
          >
            {{ option.localName || option.label }}
          </div>
          <span v-if="!option.synthesisMinMax" class="nodata has-text-danger">
            Pas de données
          </span>
        </div>
        <div
          :class="
            option.synthesisMinMax && onClickLabelSynthesisDetailCb
              ? 'tile synthesis-details link column is-variable is-12-desktop is-8-widescreen'
              : 'tile synthesis-details column'
          "
          @click="
            (event) =>
              option.synthesisMinMax &&
              onClickLabelSynthesisDetailCb &&
              onClickLabelSynthesisDetailCb(event, option)
          "
        >
          <span v-if="option.synthesisMinMax" class="synthesis-infos has-text-info-dark">
            <b-field v-show="false">
              {{
                new Date(option.synthesisMinMax[0]).toLocaleDateString("fr") +
                " - " +
                new Date(option.synthesisMinMax[1]).toLocaleDateString("fr")
              }}
            </b-field>
            <availiblity-chart
              class="tile availiblity-chart"
              :show-dates="false"
              :minmax="option.synthesis.minmax"
              :ranges="option.synthesis.ranges"
              :id="option.label"
            />
          </span>
        </div>
      </div>
      <div class="CollapsibleTree-buttons column">
        <div class="file button is-small is-info" v-if="onUploadCb">
          <b-upload
            v-model="refFile"
            class="file-label ml-1"
            accept=".csv"
            @input="() => onUploadCb(option.label, refFile) && showChildren()"
          >
            <span class="file-name" v-if="refFile">
              {{ refFile.name }}
            </span>
            <span class="file-cta">
              <b-icon class="file-icon" icon="upload" style="font-size: 0.75rem"></b-icon>
            </span>
          </b-upload>
        </div>
        <div class="column is-5" v-else>
          <b-button
            icon-left="archive"
            size="is-small"
            class="ml-1"
            :label="$t('dataTypesManagement.manage-datasets')"
            @click="repositoryRedirect(option.label)"
            type="is-info"
          >
          </b-button>
        </div>
        <div class="column is-3" v-for="button in buttons" :key="button.id">
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
import AvailiblityChart from "../charts/AvailiblityChart.vue";

@Component({
  components: { FontAwesomeIcon, AvailiblityChart },
})
export default class CollapsibleTree extends Vue {
  @Prop() option;
  @Prop({ default: 0 }) level;
  @Prop() onClickLabelCb;
  @Prop() onClickLabelSynthesisDetailCb;
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
.synthesisDetails {
  margin-left: 10px;
}
.availiblity-chart canvas {
  width: 900px;
}
.synthesis-infos {
  width: 900px;
  display: flex;
  align-items: center;
  padding: 0.75rem;
}
.synthesis-details {
  width: auto;
  display: flex;
  align-items: center;
  padding: 0.75rem;
}
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
  width: 100%;
}

.CollapsibleTree-buttons {
  display: flex;
  height: $row-height;
  align-items: center;
}

.file {
  margin-bottom: 0;
}

.file-cta {
  height: 100%;
  border-color: transparent;
}
.nodata {
  margin-left: auto;
  margin-right: 50px;
}
</style>
