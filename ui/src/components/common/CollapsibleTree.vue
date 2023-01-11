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
      <div
        class="CollapsibleTree-header-infos column is-variable is-half-desktop is-three-quarters-widescreen"
      >
        <div
          class="CollapsibleTree-header-infos column"
          :style="`transform:translate(${level * 50}px);`"
        >
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
            <b-tooltip
              type="is-primary is-light"
              :label="$t('dataTypesManagement.tooltip_show_secondary_menu')"
            >
              <b-button
                class="is-small"
                tabindex="0"
                type="is-primary"
                outlined
                style="margin: 10px"
              >
                <b-icon icon="ellipsis-h"></b-icon>
              </b-button>
            </b-tooltip>
          </div>
          <FontAwesomeIcon
            v-if="option.children && option.children.length !== 0"
            :icon="displayChildren ? 'caret-down' : 'caret-right'"
            class="clickable mr-3"
            tabindex="0"
          />
          <p>{{ option.localName || option.label }}</p>
          <span class="file-name" v-if="refFile">
            {{ refFile.name }}
          </span>
          <span class="file-name" v-else-if="lineCount > 0">
            {{ $t("validation.count-line") }} {{ lineCount }}
          </span>
          <span v-else-if="!option.synthesisMinMax" class="nodata has-text-danger">
            {{ $t("validation.data-empty") }}
          </span>
        </div>
        <div
          :class="
            option.synthesisMinMax && onClickLabelSynthesisDetailCb
              ? 'tile synthesis-details link column is-variable is-10-desktop is-8-widescreen'
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
      <div class="CollapsibleTree-buttons column is-2">
        <div class="file button is-small is-info" v-if="onUploadCb">
          <b-upload
            v-model="refFile"
            class="file-label ml-1"
            accept=".csv"
            @input="() => onUploadCb(option.label, refFile) && showChildren()"
          >
            <span class="file-cta">
              <b-icon icon="upload"></b-icon>
            </span>
          </b-upload>
        </div>
        <div v-else>
          <b-button
            size="is-small"
            class="ml-1"
            @click="repositoryRedirect(option.label)"
            type="is-info"
          >
            <span class="file-cta" style="border-color: transparent; background-color: transparent">
              <b-icon class="file-icon" icon="archive" style="color: white"></b-icon>
            </span>
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
      :on-click-label-cb="onClickLabelCb"
      :on-upload-cb="onUploadCb"
      :buttons="buttons"
      :class="displayChildren ? '' : 'hide'"
      :with-radios="withRadios"
      :radio-name="radioName"
      @optionChecked="onInnerOptionChecked"
      :application-title="applicationTitle"
      :line-count="child.lineCountChild"
    />
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
    border-left: solid 2px;
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
