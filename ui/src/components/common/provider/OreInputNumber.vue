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
        :label="label">
      <b-taginput type="number"
                  v-if="multiplicity"
                  required
                  @blur="updateValue"
                  @input="updateValue"
                  v-model="val"/>
      <b-input type="number"
               v-else
               required
               @blur="updateValue"
               @input="updateValue"
               v-model="val"/>
    </b-field>
  </ValidationProvider>
</template>

<script>
import {extend, ValidationProvider} from "vee-validate";
import {ref, watch} from "vue";

export default {
  setup(props) {
    const val=ref("");
    watch(() => props.value, () => {
      val.value = ref(props.value)
    });
    return {val}
  },
  name: "OreInputNumber",
  emits: ['update:value'],
  components: {
    ValidationProvider
  },
  props: {
    checker: {
      type: Object,
      required: false,
    },
    value: {
      type: [String, Array],
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
      if (typeof event == "object") {
        event = event.target.value
      }
      this.$emit('update:value', event)
    },
    validateRegExp(value, type) {
      if (Array.isArray(value)){
        return value
            .map(v => this.regexp(v))
            .filter(v => v == false)
            .length == 0
      } else {
        return type == 'integer' ? this.regexpInteger(value) : this.regexpFloat(value)
      }
    },
    validateRequired(value) {
      let method = !!value
      if (typeof value == 'string') {
        return method(value)
      } else {
        return value
            .map(v => !method(v))
            .filter(v => v == false)
            .length == 0
      }
    },
    regexpInteger(value) {
      return new RegExp('^[-+]?\\d+$', 'g').test(value)
    },
    regexpFloat(value) {
      return new RegExp('^[+-]?([0-9]*[.])?[0-9]+$', 'g').test(value)
    },
  },
  computed: {
    required: {
      get() {
        return this.checker && this.checker.params && this.checker.params.required
      }
    },
    multiplicity: {
      get() {
        return this.checker && this.checker.params && this.checker.params.multiplicity == 'MANY'
      }
    },
    rules: {
      get() {
        let rules = []
        if (this.checker) {
          if (this.checker.name == 'Integer') {
            this.extend('integer', value => {
              return this.validateRegExp(value, 'integer') || this.$t('rules.integer')
            })
            rules.push('integer')
          }
          if (this.checker.name == 'Float') {
            this.extend('float', value => {
              return  this.validateRegExp(value, 'float')  || this.$t('rules.float')
            })
            rules.push('float')
          }
          if (this.checker.params.required) {
            this.extend('required', value => {
              return this.validateRequired(value) || this.$t('rules.required')
            })
            rules.push('required')
          }
        }
        return rules.join('|')
      }
    }
  }
}
</script>

<style scoped>

</style>