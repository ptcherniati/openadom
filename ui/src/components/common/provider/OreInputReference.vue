<template>
  <ValidationProvider
    class="column is-12"
    :rules="rules"
    :name="vid"
    v-slot="{ errors, valid }"
    :vid="vid"
  >
    <b-field
      class="file is-primary column is-12"
      :type="{
        'is-danger': errors && errors.length > 0,
        'is-success': valid,
      }"
      :message="errors"
      :label="label"
    >
      <b-select
        v-if="references"
        :multiple="multiplicity"
        :required="required"
        @blur="updateValue"
        @input="updateValue"
        :value="val"
      >
        <option
          v-for="option in references"
          :key="option.naturalKey"
          :label="getFullName(option.naturalKey)"
          :value="option.naturalKey"
        ></option>
      </b-select>
    </b-field>
  </ValidationProvider>
</template>

<script>
import { extend, ValidationProvider } from "vee-validate";
import { LOCAL_STORAGE_LANG } from "@/services/Fetcher";
import { ref, watch } from "vue";

const defaultLanguage = localStorage.getItem(LOCAL_STORAGE_LANG);
export default {
  setup(props) {
    const val = ref(props.value);
    watch(
      () => props.value,
      () => {
        val.value = ref(props.value);
      }
    );
    return { val };
  },
  name: "OreInputReference",
  emits: ["update:value"],
  components: {
    ValidationProvider,
  },
  props: {
    references: {
      type: Array,
      required: false,
    },
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
      defaultLanguage,
    };
  },
  methods: {
    getFullName(naturalKey) {
      let currentNames = [];
      return (
        naturalKey
          .split("__")
          .map((key) => {
            currentNames.push(key);
            let currentName = currentNames.join("__");
            return this.references.find((reference) => reference.naturalKey == currentName);
          })
          .map(
            (reference) =>
              reference.values["__display_" + this.defaultLanguage] || reference.naturalKey
          )
          .join("/") || naturalKey
      );
    },
    extend,
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
        return this.checker && this.checker.params && this.checker.params.multiplicity == "MANY";
      },
    },
    rules: {
      get() {
        let rules = [];
        if (this.checker) {
          if (this.checker.params.required) {
            this.extend("selected", (value) => {
              return Object.keys(value).length > 0 || this.$t("rules.required");
            });
            rules.push("selected");
          }
        }
        return rules.join("|");
      },
    },
  },
};
</script>

<style scoped></style>