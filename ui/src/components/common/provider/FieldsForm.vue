<template>
  <ValidationObserver ref="form" v-slot="{ }" tag="form"
                      @submit.prevent="onSubmit"
  >
    <div>
      {{ description }}
    </div>
    <div v-for="(item,key) in format" :key="key">
      <OreInputText
          v-if="fields && !item.checker ||  item.checker.name == 'RegularExpression' ||  item.checker.name == 'GroovyExpression'"
          :checker="item.checker"
          :label="internationalisationService.getLocaleforPath(application,pathForKey+'.'+key, key)"
          :value="fields[key]"
          :vid="key"
          @update:value="updateValue(key,$event)"/>
      <OreInputNumber
          v-else-if="item.checker.name == 'Integer' || item.checker.name == 'Float' "
          :checker="item.checker"
          :label="internationalisationService.getLocaleforPath(application,pathForKey+'.'+key, key)"
          :value="fields[key]"
          :vid="key"
          @update:value="updateValue(key,$event)"/>
      <OreInputDate
          v-else-if="item.checker.name == 'Date' "
          :checker="item.checker"
          :label="internationalisationService.getLocaleforPath(application,pathForKey+'.'+key, key)"
          :value="fields[key]"
          :vid="key"
          @update:value="updateValue(key,$event)"/>
      <OreInputReference
          v-else-if="item.checker.name == 'Reference'  && refValues[key]"
          :checker="item.checker"
          :label="internationalisationService.getLocaleforPath(application,pathForKey+'.'+key, key)"
          :references="refValues[key].referenceValues"
          :value="fields[key]"
          :vid="key"
          @update:value="updateValue(key,$event)"/>
    </div>
  </ValidationObserver>

</template>

<script>
import OreInputText from "@/components/common/provider/OreInputText";
import OreInputNumber from "@/components/common/provider/OreInputNumber";
import OreInputDate from "@/components/common/provider/OreInputDate";
import OreInputReference from "@/components/common/provider/OreInputReference";
import {ValidationObserver} from "vee-validate";
import {InternationalisationService} from "@/services/InternationalisationService";
//import {FieldFilters} from "@/model/FieldFilters";

export default {
  name: "FieldsForm",
  emits: ['update:fields'],
  data: () => {
    return {
      internationalisationService: InternationalisationService.INSTANCE
    }
  },
  components: {
    OreInputDate,
    OreInputReference,
    OreInputNumber,
    OreInputText,
    ValidationObserver
  },
  props: {
    application: Object,
    pathForKey: String,
    format: Object,
    description: String,
    refValues: Object,
    fields: Object,
  },
  methods: {
    onSubmit(event) {
      console.log('submit', event)
    },
    updateValue(key, event){
      let fields = this.fields
      fields[key]=event;
      this.$refs.form && this.$refs.form.validate().then(v => {
        if (v) {
          this.$emit('update:fields', {fields, valid:true});
        } else {
          this.$emit('update:fields', {fields, valid:false});
        }
      });
    }
  }
}
</script>

<style scoped>

</style>