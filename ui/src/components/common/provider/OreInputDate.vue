<template>
  <ValidationProvider
      v-slot="{ errors, valid }"
      :name="vid"
      :rules="rules"
      :vid="vid"
      class="column is-12"
  >
    <b-field
        :label="label"
        :message="errors"
        :type="{
        'is-danger': errors && errors.length > 0,
        'is-success': valid,
      }"
        class="file is-primary column is-12"
    >
      <template v-slot:label>
        <span v-if="required" class="required">{{ $t("ponctuation.star") }}</span>
        <label>{{ label }}</label>
      </template>
      <b-taginput
          v-if="multiplicity === 'MANY'"
          v-model="val"
          required
          type="text"
          @blur="updateValue"
          @input="updateValue"
      />
      <b-field v-else>
        <b-input v-model="val" required type="text" @blur="updateValue" @input="updateValue"/>
        <b-datepicker v-model="val" :mobile-native="false">
          <template v-slot:trigger>
            <b-button
                icon-left="calendar"
                type="is-primary"
                disabled/>
          </template>
        </b-datepicker>
      </b-field>
    </b-field>
  </ValidationProvider>
</template>

<script>
import moment from "moment";
import {extend, ValidationProvider} from "vee-validate";
import {watch, ref} from "vue";

export default {
  setup(props) {
    const val = ref("");
    watch(
        () => props.value,
        () => {
          val.value = ref(props.value);
        }
    );
    return {val};
  },
  name: "OreInputDate",
  emits: ["update:value"],
  components: {
    ValidationProvider,
  },
  props: {
    checker: {
      type: Object,
      required: false,
    },
    value: {
      required: true,
    },
    label: {
      type: String,
      required: true,
    },
    vid: {
      type: String,
      required: false,
    },
  },
  data: () => {
    return {
      pattern: {
        "dd/MM/yyyy": {
          pattern: "DD-MM-YYYY",
          regexp: /(\d{2})\/(\d{2})\/(\d{4})/,
          replace: "$1 $2 $3",
        },
        "dd/MM/yy": {
          pattern: "DD-MM-YY",
          regexp: /(\d{2})\/(\d{2})\/(\d{2})/,
          replace: "$1 $2 $3",
        },
        "MM/yyyy": {
          pattern: "MM-YYYY",
          regexp: /(\d{2})\/(\d{4})/,
          replace: "$1 $2",
        },
        "MM/yy": {
          pattern: "MM-YY",
          regexp: /(\d{2})\/(\d{2})/,
          replace: "$1 $2",
        },
        yyyy: {
          pattern: "YYYY",
          regexp: /(\d{4})/,
          replace: "$1",
        },
        "hh:mm": {
          pattern: "hh:mm",
          regexp: /(\d{2}):(\d{2})/,
          replace: "$1 $2",
        },
        "hh:mm:ss": {
          pattern: "hh:mm:ss",
          regexp: /(\d{2}):(\d{2}):(\d{2})/,
          replace: "$1 $2 $3",
        },
        "dd/MM/yyyy hh:mm:ss": {
          pattern: "DD-MM-YYYY hh:mm:ss",
          regexp: /(\d{2})\/(\d{2})\/(\d{4}) (\d{2}):(\d{2}):(\d{2})/,
          replace: "$1 $2 $3 $4:$5:$6",
        },
        "dd/MM/yy hh:mm:ss": {
          pattern: "DD-MM-YY hh:mm:ss",
          regexp: /(\d{2})\/(\d{2})\/(\d{2}) (\d{2}):(\d{2}):(\d{2})/,
          replace: "$1 $2 $3 $4:$5:$6",
        },
      },
    };
  },
  methods: {
    extend,
    validDate(value) {
      let patternElement = this.pattern[this.checker.params.pattern];
      let formattedDate = moment(value, patternElement.pattern).format(patternElement.pattern);
      let parsedDate = formattedDate.replaceAll("-", "/");
      return parsedDate === value;
    },
    validateDate(value) {
      if (Array.isArray(value)) {
        return value.map(this.validDate).filter((v) => v === false).length === 0;
      } else {
        return this.validDate(value);
      }
    },
    validateRequired(value) {
      if (typeof value == "string") {
        return !!value;
      } else {
        return value.length > 0;
      }
    },
    updateValue(event) {
      if (typeof event == "object") {
        event = event.target.value;
      }
      this.$emit("update:value", event);
    },
  },
  computed: {
    required: {
      get() {
        return this.checker && this.checker.params && this.checker.params.required;
      },
    },
    multiplicity: {
      get() {
        return this.checker && this.checker.params && this.checker.params.multiplicity === "MANY";
      },
    },
    isValidDate: {
      get() {
        return this.parsedDate === this.value;
      },
    },
    rules: {
      get() {
        let rules = [];
        if (this.checker) {
          if (this.checker.name === "Date") {
            if (this.checker.params.pattern) {
              this.extend("date", (value) => {
                return this.validateDate(value) || this.$t("rules.date", this.checker.params);
              });
              rules.push("date");
            }
          }
          if (this.checker.params.required) {
            this.extend("required", (value) => {
              return !!value || this.$t("rules.required");
            });
            rules.push("required");
          }
        }
        return rules.join("|");
      },
    },
  },
};
</script>

<style scoped>
.required {
  color: red;
  padding-right: 5px;
  font-size: 150%;
}
</style>
