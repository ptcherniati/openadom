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
        type="textarea"
        @blur="updateValue"
        @input="updateValue"
      />
      <b-input
        v-else
        v-model="val"
        required
        type="textarea"
        @blur="updateValue"
        @input="updateValue"
      />
    </b-field>
  </ValidationProvider>
</template>

<script>
import { extend, ValidationProvider } from "vee-validate";
import { ref, watch } from "vue";

export default {
  setup(props) {
    const val = ref("");
    watch(
      () => props.value,
      () => {
        val.value = ref(props.value);
      }
    );
    return { val };
  },
  name: "OreInputText",
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
  methods: {
    extend,
    updateValue(event) {
      if (typeof event == "object" && !Array.isArray(event)) {
        event = event.target.value;
      }
      this.$emit("update:value", event);
    },
    regexp(value) {
      return new RegExp("^" + this.checker.params.pattern + "$", "g").test(value);
    },
    validateRegExp(value) {
      if (typeof value == "string") {
        return this.regexp(value);
      } else {
        return value && value.map((v) => this.regexp(v)).filter((v) => v === false).length === 0;
      }
    },
    validateRequired(value) {
      if (typeof value == "string") {
        return !!value;
      } else {
        return value && value.length > 0;
      }
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
    rules: {
      get() {
        let rules = [];
        if (this.checker) {
          if (this.checker.name === "RegularExpression") {
            if (this.checker.params.pattern) {
              this.extend("regexp", (value) => {
                return this.validateRegExp(value) || this.$t("rules.regexp", this.checker.params);
              });
              rules.push("regexp");
            }
          }
          if (this.checker.params.required) {
            this.extend("required", (value) => {
              return this.validateRequired(value) || this.$t("rules.required");
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