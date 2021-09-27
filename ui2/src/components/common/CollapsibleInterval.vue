<template>
  <div>
    <div>
      <b-button
        size="is-small"
        type="is-dark is-light"
        icon-left="plus"
        outlined
        @click="isCardModalActive = true"
      />
      <b-modal v-model="isCardModalActive" outlined scroll="keep">
        <div class="card">
          <div class="rows">
            <div class="row">
              <div class="columns">
                <div class="column is-one-fifth">
                  <label class="label">{{ $t("dataTypeAuthorizations.from") }}</label>
                </div>
                <div class="column is-four-fifth">
                  <b-input
                    :type="inputType"
                    :placeholder="format"
                    :validation-message="format"
                    :pattern="pattern"
                    :format="format"
                    v-model="from"
                  />
                </div>
              </div>
            </div>

            <div class="row">
              <div class="columns">
                <div class="column is-one-fifth is-right">
                  <label class="label">{{ $t("dataTypeAuthorizations.to") }}</label>
                </div>
                <div class="column is-four-fifth">
                  <b-input
                    label="et"
                    :type="inputType"
                    :placeholder="format"
                    :validation-message="format"
                    :pattern="pattern"
                    :format="format"
                    v-model="to"
                  />
                </div>
              </div>
            </div>
            <div class="row">
              <div class="columns">
                <div class="column is-4"></div>
                <div class="column is-4">
                  <b-button
                    icon-left="filter"
                    type="is-dark"
                    expanded
                    @click="submit"
                    outlined
                  ></b-button>
                </div>
              </div>
            </div>
          </div>
        </div>
      </b-modal>
    </div>
  </div>
</template>

<script>
import { Component, Prop, Vue } from "vue-property-decorator";
import { FontAwesomeIcon } from "@fortawesome/vue-fontawesome";

@Component({
  components: { FontAwesomeIcon },
})
export default class CollapsibleInterval extends Vue {
  @Prop() variableComponent;
  type = null;
  format = null;
  variable = null;
  component = null;
  key = null;

  isCardModalActive = false;
  from = "";
  to = "";
  dateTimeFormat = {
    d: { pattern: "\\d", type: "date" },
    h: { pattern: "\\d", type: "time" },
    m: { pattern: "\\d", type: "time" },
    s: { pattern: "\\d", type: "time" },
    n: { pattern: "\\d", type: "time" },
    a: { pattern: "[AP]M]", type: "time" },
    y: { pattern: "\\d", type: "date" },
    M: { pattern: "\\d", type: "date" },
    Z: { pattern: "[+-]\\d{4}", type: "date" },
    G: { pattern: "[AB]D", type: "date" },
  };
  pattern = /.*/;
  inputType = "text";
  created() {
    if (this.variableComponent) {
      this.type = this.variableComponent.type;
      this.format = this.variableComponent.format;
      this.variable = this.variableComponent.variable;
      this.component = this.variableComponent.component;
      this.key = this.variableComponent.key;
    }
    let p = this.format;
    let t = { date: false, time: false, isNumeric: false };
    if (this.type == "date") {
      Object.keys(this.dateTimeFormat).forEach((search) => {
        if (p.match(search)) {
          t[this.dateTimeFormat[search].type] = true;
          p = p.replaceAll(search, this.dateTimeFormat[search].pattern);
        }
      });
      this.pattern = "^" + p + "$";
    }
    if (this.type == "numeric") {
      if (this.format == "integer") {
        t.isNumeric = true;
        this.pattern = /(?<=\s|^)[-+]?\d+(?=\s|$)/;
      }
      if (this.format == "float") {
        t.isNumeric = true;
        this.pattern = /^[+-]?([0-9]+([.][0-9]*)?|[.][0-9]+)$/;
      }
    }
    if (t.date && t.time) {
      this.inputType = "datetime";
    } else if (t.date) {
      this.inputType = "date";
    } else if (t.time) {
      this.inputType = "time";
    } else if (t.isNumeric) {
      this.inputType = "number";
    }
  }
  submit() {
    this.$emit("setting_interval", {
      type: this.type,
      format: this.format,
      key: this.key,
      variable: this.variable,
      component: this.component,
      variableComponent: {
        variable: this.variable,
        component: this.component,
      },
      intervalValues: {
        from: this.from,
        to: this.to,
      },
    });
    this.isCardModalActive = false;
  }
}
</script>
<style lang="scss" scoped>
.label {
  text-align: right;
  margin: 3px;
}

.card {
  margin: 1em;
}
.column {
  padding: 1.5em;
}
</style>
