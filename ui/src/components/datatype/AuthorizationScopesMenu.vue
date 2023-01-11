<template>
  <div class="columns">
    <div v-for="(authReference, key) in authReferences" :key="key">
      <div class="column">
        <b-field>
          <b-dropdown :ref="key" expanded>
            <template #trigger="{ active }">
              <b-button
                  :icon-right="active ? 'chevron-up' : 'chevron-down'"
                  expanded
                  type="is-primary"
              >
                {{ internationalisationService.localeReferenceName({label: key}, application) }}
              </b-button>
            </template>
            {{ key }}
            <SelectMenu
                v-for="(option) in authReference"
                :key="option.currentPath"
                v-model="selectedPathes"
                :auth="key"
                :option="option"
                @select-menu-item="selectAuthorization(key, $event.path, $event.selected)"
            />
          </b-dropdown>
        </b-field>
      </div>
    </div>
    <b-field
        :label="$t('dataTypeAuthorizations.label-datePicker')"
        class="column"
        label-position="on-border"
    >
      <b-datepicker
          v-model="startDate"
          :date-parser="parseDate"
          :placeholder="
                $t('dataTypesRepository.placeholder-datepicker') +
                'dd-MM-YYYY'"
          editable
          icon="calendar"
          pack="far"
          @input="updateDate($event, 'from')"
      >
      </b-datepicker>
    </b-field>
    <b-field
        :label="$t('dataTypeAuthorizations.label-datePicker')"
        class="column"
        label-position="on-border"
    >
      <b-datepicker
          v-model="endDate"
          :date-parser="parseDate"
          :placeholder="
                $t('dataTypesRepository.placeholder-datepicker') +
                'dd-MM-YYYY'"
          editable
          icon="calendar"
          pack="far"
          @input="updateDate($event, 'to')"
      >
      </b-datepicker>
    </b-field>
  </div>
</template>

<script>
import {InternationalisationService} from "@/services/InternationalisationService";
import {ReferenceService} from "@/services/rest/ReferenceService";
import SelectMenu from "@/components/common/SelectMenu";

export default {
  name: "AuthorizationScopesMenu",
  components: {SelectMenu},
  data() {
    return {
      emits: ['input'],
      internationalisationService: InternationalisationService.INSTANCE,
      referenceService: ReferenceService.INSTANCE,
      localName: "",
      references: {},
      selectedPathes: {},
      startDate: null,
      endDate: null,
    };
  },
  props: {
    application: Object,
    authReferences: Object,
    value: Object
  },
  watch: {
    value: {
      immediate: true,
      deep: true,
      handler(val) {
        if (val && val.timeScope && val.timeScope.from) {
          this.startDate = new Date(val.timeScope.from)
        }
        if (val && val.timeScope && val.timeScope.to) {
          this.endDate = new Date(val.timeScope.to)
        }
      }
    }
  },
  methods: {
    async loadedReferences(reference) {
      if (!this.references[reference]) {
        this.references[reference] = await this.referenceService.getReferenceValues(
            this.application.name,
            reference
        );
      }
      return this.references[reference];
    },
    selectAuthorization(auth, path, selected) {
      this.findDescriptionForPath(this.authReferences[auth], 'bassin_versant.oir')
      let authorizationDescriptions = this.value;
      if (selected) {
        //on cherche les authorization obsolètes et on les supprime
        authorizationDescriptions.requiredAuthorizations = (authorizationDescriptions.requiredAuthorizations || [])
            .filter(ra => !(ra[auth] || '').startsWith(path))
        let pathes = path.split('.');
        pathes.pop()
        let current = this.authReferences[auth]
        let parentpath = path.split('.')
        parentpath.pop()
        parentpath = parentpath.join('.')
        if (current) {
          current = this.findDescriptionForPath(current, parentpath)
          let count = current ? Object.keys(current.referenceValues).length : 0;
          pathes = pathes.reduce((acc, p) => acc == '' ? p : acc + '.' + p, "");
          let count2 = authorizationDescriptions.requiredAuthorizations
              .filter(ra => (ra[auth] || '').startsWith(pathes)).length
          if (count == count2 + 1 && pathes != '') {
            authorizationDescriptions.requiredAuthorizations = (authorizationDescriptions.requiredAuthorizations || [])
                .filter(ra => !(ra[auth] || '').startsWith(pathes))
            path = pathes;
          }
        }
        let authorizationScope = {}
        authorizationScope[auth] = path
        path != '' && authorizationDescriptions.requiredAuthorizations.push(authorizationScope)
      } else {
        let authorizationtoDelete = (authorizationDescriptions.requiredAuthorizations || [])
            .find(ra => ra[auth] == path)
        if (authorizationtoDelete) {
          authorizationDescriptions.requiredAuthorizations = (authorizationDescriptions.requiredAuthorizations || [])
              .filter(ra => ra[auth] != path)
        } else {
          let parents = path.split('.');
          parents.pop();//je supprime le nœud courant
          while (parents.length) {
            let current = this.findDescriptionForPath(this.authReferences[auth], parents.join('.'))
            Object.values(current.referenceValues)
                .map(v => v.currentPath)
                .filter(v => !path.startsWith(v))
                .forEach(v => {
                  let authorizationScope = {}
                  authorizationScope[auth] = v
                  v != '' && authorizationDescriptions.requiredAuthorizations.push(authorizationScope)
                })
            if (path.startsWith(current.currentPath)) {
              authorizationDescriptions.requiredAuthorizations = (authorizationDescriptions.requiredAuthorizations || [])
                  .filter(ra => (ra[auth] || '') != current.currentPath)
              break;
            }
            parents.pop();
          }
        }
      }
      //let selectedPathes = this.selectedPathes
      this.$set(this.selectedPathes, auth, (authorizationDescriptions.requiredAuthorizations || []).map(a => a[auth]))
      /*selectedPathes[auth] = (authorizationDescriptions.requiredAuthorizations || []).map(a => a[auth])
      this.selectedPathes = selectedPathes*/
      this.$emit('input', authorizationDescriptions)
    },
    findDescriptionForPath(requiredAuthorizations, path) {
      if (!requiredAuthorizations) {
        return null;
      }
      return Object.values(requiredAuthorizations).map(v => {
        if (!v.currentPath) {
          return null;
        } else if (v.currentPath == path) {
          return v;
        }
        return this.findDescriptionForPath(v.referenceValues, path)
      })
          .flat()
          .find(vv => !!vv)
    },
    updateDate(event, type) {
      let date = event.toISOString().substring(0, 10)
      let newValue = this.value;
      let timescope = newValue.timeScope || {}
      timescope[type] = date;
      this.$emit('input', newValue)
    },
    parseDate(date) {
      date =
          date && date.replace(/(\d{2})\/(\d{2})\/(\d{4})(( \d{2})?(:\d{2})?(:\d{2})?)/, "$3-$2-$1$4");
      return new Date(date);
    }
  },
};
</script>

<style scoped></style>
