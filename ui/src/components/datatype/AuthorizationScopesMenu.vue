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
            {{key}}
            <SelectMenu
                v-for="(option) in authReference"
                :key="option.currentPath"
                :option="option"
                :selectedPathes="selectedPathes"
                :auth="key"
                @select-menu-item="selectAuthorization(key, $event.path, $event.selected)"
            />
          </b-dropdown>
        </b-field>
      </div>
    </div>
    <div class="column">
      dates
    </div>
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
      internationalisationService: InternationalisationService.INSTANCE,
      referenceService: ReferenceService.INSTANCE,
      localName: "",
      references: {},
      authorizationDescriptions: {
        timeScope: {
          from: null,
          to: null
        },
        requiredAuthorizations: []
      },
      selectedPathes:{}
    };
  },
  props: {
    application: Object,
    authReferences: Object
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
      if (selected) {
        //on cherche les authorization obsolètes et on les supprime
        this.authorizationDescriptions.requiredAuthorizations = this.authorizationDescriptions.requiredAuthorizations
            .filter(ra => !ra[auth].startsWith(path))
        let pathes = path.split('.');
        pathes.pop()
        let current = this.authReferences[auth]
        if (current) {
          for (const currentKey in pathes) {
            current = current[pathes[currentKey]].referenceValues;
          }
          let count = current ? Object.keys(current).length : 0;
          pathes = pathes.reduce((acc, p) => acc == ''?p: acc + '.' + p, "");
          let count2 = this.authorizationDescriptions.requiredAuthorizations
              .filter(ra => ra[auth].startsWith(pathes)).length
          if (count == count2+1){
            this.authorizationDescriptions.requiredAuthorizations = this.authorizationDescriptions.requiredAuthorizations
            .filter(ra => !ra[auth].startsWith(pathes))
            path = pathes;
          }
        }
        let authorizationScope = {}
        authorizationScope[auth] = path
        this.authorizationDescriptions.requiredAuthorizations.push(authorizationScope)
        console.log('selectAuthorization', {
          auth,
          path,
          selected,
          authorizationDescriptions: this.authorizationDescriptions
        });
      }
      let selectedPathes = this.selectedPathes
      selectedPathes[auth] = this.authorizationDescriptions.requiredAuthorizations.map(a=>a[auth])
      this.selectedPathes = selectedPathes
    },
  },
};
</script>

<style scoped></style>