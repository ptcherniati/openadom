<template>
  <div>
    <div
      :class="`columns CollapsibleTree-header ${
        option.children && option.children.length !== 0 ? 'clickable' : ''
      } ${option.children && option.children.length !== 0 && displayChildren ? '' : 'mb-1'}`"
      :style="`margin:0px;`"
      @click="displayChildren = !displayChildren"
      @keypress.enter="displayChildren = !displayChildren"
    >
      <div class="CollapsibleTree-header-infos column is-two-thirds">
        <div
          :style="`transform:translate(${level * 50}px);`"
          class="CollapsibleTree-header-infos column is-narrow"
        >
          <slot name="secondaryMenu" v-bind:onClickLabelCb="onClickLabelCb" v-bind:option="option">
            <div
              :class="onClickLabelCb ? 'link' : ''"
              tabindex="0"
              @click="(event) => onClickLabelCb && onClickLabelCb(event, option.label)"
              @keypress.enter="(event) => onClickLabelCb && onClickLabelCb(event, option.label)"
            >
              <b-tooltip
                :label="$t('dataTypesManagement.tooltip_show_secondary_menu')"
                type="is-primary is-light"
              >
                <b-button
                  class="is-small"
                  outlined
                  style="margin: 10px"
                  tabindex="0"
                  type="is-primary"
                >
                  <b-icon icon="ellipsis-h"></b-icon>
                </b-button>
              </b-tooltip>
            </div>
          </slot>
          <slot name="openSubMenu" v-bind:displayChildren="displayChildren">
            <FontAwesomeIcon
              v-if="option.children && option.children.length !== 0"
              :icon="displayChildren ? 'caret-down' : 'caret-right'"
              class="clickable mr-3"
              tabindex="0"
            />
          </slot>
          <slot name="label" v-bind:option="option">
            <p>{{ option.localName || option.label }}</p>
          </slot>
          <slot name="refFile" v-bind:refFile="refFile">
            <span v-if="refFile" class="file-name">
              {{ refFile.name }}
            </span>
          </slot>
          <slot name="tags" v-bind:option="option">
            <div v-if="option.localtags" class="column">
              <span v-for="tag in option.localtags" :key="tag" style="margin-left: 5px">
                <b-tag v-if="tag !== 'no-tag'" class="is-primary is-light">
                  {{ tag }}
                </b-tag>
              </span>
            </div>
          </slot>
        </div>
        <slot
          name="synthesisDetail"
          v-bind:lineCount="lineCount"
          v-bind:onClickLabelSynthesisDetailCb="onClickLabelSynthesisDetailCb"
          v-bind:option="option"
        >
          <div
            :class="
              option.synthesisMinMax && onClickLabelSynthesisDetailCb
                ? 'tile synthesis-details link column is-four-fifths'
                : 'tile synthesis-details column is-full'
            "
            @click="
              (event) =>
                option.synthesisMinMax &&
                onClickLabelSynthesisDetailCb &&
                onClickLabelSynthesisDetailCb(event, option)
            "
          >
            <span
              v-if="option.synthesisMinMax"
              class="synthesis-infos has-text-info-dark column is-full"
            >
              <b-field v-show="false">
                {{
                  new Date(option.synthesisMinMax[0]).toLocaleDateString("fr") +
                  " - " +
                  new Date(option.synthesisMinMax[1]).toLocaleDateString("fr")
                }}
              </b-field>
              <div v-if="isLoading" class="loader-wrapper">
                <div class="loader is-loading"></div>
              </div>
              <availiblity-chart
                v-else
                :id="option.label"
                :minmax="option.synthesis.minmax"
                :ranges="option.synthesis.ranges"
                :show-dates="false"
                class="tile availiblity-chart"
              />
            </span>
            <span v-else-if="lineCount > 0" class="file-name">
              {{ $tc("validation.count-line", lineCount) }}
            </span>
            <span v-else class="nodata has-text-danger" style="margin-left: 50px">
              {{ $t("validation.data-empty") }}
            </span>
          </div>
        </slot>
      </div>
      <div class="CollapsibleTree-buttons column is-narrow">
        <slot
          name="upload"
          v-bind:onUploadCb="onUploadCb"
          v-bind:option="option"
          v-bind:refFile="refFile"
          v-bind:repositoryRedirect="repositoryRedirect"
        >
          <div
            v-if="onUploadCb"
            :class="'file button is-small' + (option.canUpload ? ' is-info' : 'is-light')"
            :disabled="!option.canUpload"
          >
            <b-upload
              v-model="refFile"
              :disabled="!option.canUpload"
              accept=".csv"
              class="file-label ml-1"
              style="padding: 0px"
              @input="() => onUploadCb(option.label, refFile) && showChildren()"
            >
              <span :disabled="!option.canUpload" class="file-cta">
                <b-icon icon="upload"></b-icon>
              </span>
            </b-upload>
          </div>
          <div v-else>
            <b-button
              :disabled="!(option.canUpload || option.canPublish || option.canDelete)"
              :type="
                option.canUpload || option.canPublish || option.canDelete ? 'is-info' : 'is-light'
              "
              class="ml-1"
              size="is-small"
              @click="repositoryRedirect(option.label)"
            >
              <span
                :disabled="!(option.canUpload || option.canPublish || option.canDelete)"
                class="file-cta"
                style="border-color: transparent; background-color: transparent"
              >
                <b-icon class="file-icon" icon="archive" style="color: white"></b-icon>
              </span>
            </b-button>
          </div>
        </slot>
        <slot name="buttons" v-bind:buttons="buttons" v-bind:option="option">
          <div v-for="button in buttons" :key="button.id">
            <b-button
              :disabled="button.disabled"
              :icon-left="button.iconName"
              :type="button.type"
              class="ml-1"
              size="is-small"
              @click="button.clickCb(option.label)"
            >
              {{ button.label }}
            </b-button>
          </div>
        </slot>
      </div>
    </div>
    <slot v-bind:displayChildren="displayChildren" v-bind:option="option">
      <CollapsibleTree
        v-for="child in option.children"
        :key="child.id"
        :application-title="applicationTitle"
        :buttons="buttons"
        :class="displayChildren ? '' : 'hide'"
        :level="level + 1"
        :line-count="child.lineCountChild"
        :on-click-label-cb="onClickLabelCb"
        :on-upload-cb="onUploadCb"
        :option="child"
        :radio-name="radioName"
        :with-radios="withRadios"
        @optionChecked="onInnerOptionChecked"
      />
    </slot>
  </div>
</template>

<script>
import { FontAwesomeIcon } from "@fortawesome/vue-fontawesome";
import AvailiblityChart from "../charts/AvailiblityChart.vue";

export default {
  name: "CollapsibleTree",
  components: { FontAwesomeIcon, AvailiblityChart },
  props: {
    applicationName: String,
    canUpload: {
      type: Boolean,
      default: true,
    },
    option: Object,
    level: {
      type: Number,
      default: 0,
    },
    onClickLabelCb: Function,
    onClickLabelSynthesisDetailCb: Function,
    onUploadCb: Function,
    buttons: Array,
    withRadios: {
      type: Boolean,
      default: false,
    },
    radioName: Object,
    repository: Object,
    repositoryRedirect: Function,
    lineCount: {
      type: Number,
      default: 0,
    },
    isLoading: Boolean,
    applicationTitle: {
      type: String,
    },
  },
  data() {
    return {
      displayChildren: false,
      refFile: null,
      innerOptionChecked: null,
    };
  },
  watch: {
    innerOptionChecked(value) {
      return this.$emit("optionChecked", value);
    },
  },
  methods: {
    onInnerOptionChecked: function (value) {
      this.$emit("optionChecked", value);
    },
    stopPropagation: function (event) {
      event.stopPropagation();
    },
    showChildren: function () {
      this.displayChildren = true;
    },
  },
};
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

.CollapsibleTree-header.clickable {
  border-bottom: 0.35rem solid white;
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
    //border-left: solid 2px;
    border-radius: 0;
    padding-left: 0.5em;
    margin-left: 0.5em;
    background-color: rgba(255, 255, 255, 0.2);
    color: $primary;

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
