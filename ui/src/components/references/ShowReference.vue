<template>
  <div>
    <v-layout
      v-if="referenceType!=null"
      wrap
      align-center
    >
      <v-flex
        md7
        offset-md2
      >
        <v-select
          v-model="referenceName"
          solo
          name="references"
          :items="referenceType"
          item-text="name"
          label="Choisissez une donnée de référence"
        />
      </v-flex>
      <v-flex
        md1
        offset-md1
        fluid
        v-if="referenceName"
      >
        <v-tooltip bottom>
          <template v-slot:activator="{ on }">
            <v-btn
              @click="pickFile"
              color="primary"
              flat
              v-on="on"
            >
              {{ referenceName }}
              <v-icon
                right
                x-large
                color="success"
              >
                mdi-upload
              </v-icon>
            </v-btn>
          </template>
          <span>Charger des données pour {{ referenceName }}</span>
        </v-tooltip>
        <input
          style="display:none"
          type="file"
          id="referencefileUpload"
          ref="file"
          @change="handleFileUpload()"
        >
      </v-flex>
    </v-layout>
    <v-layout
      v-if="referenceName!=null"
      wrap
      align-center
    >
      <v-flex
        xs12
        sm6
        d-flex
      >
        <v-data-table
          v-model="selected"
          :custom-sort="customSort"
          :headers="headers"
          :items="referenceValue"
          :pagination.sync="pagination"
          select-all
          item-key="id"
          class="elevation-1"
          :search="filters"
          :custom-filter="customFilter"
        >
          <template v-slot:headers="props">
            <tr>
              <th>
                <v-checkbox
                  :input-value="props.all"
                  :indeterminate="props.indeterminate"
                  primary
                  hide-details
                  @click.native="toggleAll"
                />
              </th>
              <th
                v-for="header in props.headers"
                :key="header.text"
                :class="['column sortable', pagination.descending ? 'desc' : 'asc', header.value === pagination.sortBy ? 'active' : '']"
              >
                <v-icon
                  small
                  @click="changeSort(header.value)"
                >
                  arrow_upward
                </v-icon>
                <v-card>{{ header.text }}</v-card>
                
                <v-text-field 
                  append-icon="search"
                  single-line
                  solo
                  hide-details
                  @keyup="filterSearch($event, header)"
                />
              </th>
            </tr>
          </template>
          <template v-slot:items="props">
            <tr
              :active="props.selected"
              @click="props.selected = !props.selected"
            >
              <td>
                <v-checkbox
                  :input-value="props.selected"
                  primary
                  hide-details
                />
              </td>
              <td
                v-for="key in headers"
                :key="key.value"
              >
                {{ props.item.refValues[key.value] }}
              </td>
            </tr>
          </template>
        </v-data-table>
      </v-flex>
    </v-layout>
    <v-snackbar
      top
      vertical
      v-model="snackbar.visible"
    >
      {{ snackbar.text }}
      <v-btn
        flat
        @click="snackbar.visible=false"
        class="red--text"
      >
        fermer
        <v-icon color="red">
          mdi-close
        </v-icon>
      </v-btn>
    </v-snackbar>
  </div>
</template>

<script>
import EventBus from "@/eventBus";
export default {
  name: "ShowReferences",
  mounted() {
    EventBus.$on("reference:uploaded", event => {
      if (event.id) {
        this.snackbar.text = "la donnée de référence a été chargée";
        this.snackbar.visible = true;
        if (this.referenceName != null) {
          this.setReference(this.referenceName);
        }
      } else {
        let message = "la donnée de référence n'a pas pu être chargée.";
        if (event.status == 400) {
          event.text().then(text => {
            message += text;
            this.snackbar.text = message;
            this.snackbar.visible = true;
          });
        } else if (event.status == 401) {
          message +=
            " Vous n'êtes pas autorisés à charger cette donnée de référence";
        } else if (event.status == 403) {
          message +=
            " Vous n'avez pas les droits pour charger cette donnée de référence";
        } else if (event.status == 404) {
          message = "Le service est indisponible pour le moment.";
        } else if (event.status == 500) {
          message = "Le service est indisponible pour le moment.";
        }
        this.snackbar.text = message;
        this.snackbar.visible = true;
      }
    });
  },
  updated() {},
  computed: {
    references: {
      get() {
        return this.$store.state.configuration != null
          ? this.$store.state.configuration.references
          : null;
      }
    },
    referenceType: {
      get() {
        return this.$store.state.referenceType == null
          ? []
          : this.$store.state.referenceType;
      }
    },
    referenceDescription: {
      get() {
        return this.$store.state.referenceDescription;
      }
    },
    referenceName: {
      get() {
        return this.$store.state.referenceName;
      },
      set(referenceName) {
        this.setReference(referenceName);
      }
    },
    referenceValue: {
      get() {
        return this.$store.state.referenceValue;
      }
    },
    headers: {
      get() {
        if (this.$store.state.referenceDescription == null) {
          return [];
        }
        let headers =  Object.keys(this.$store.state.referenceDescription.columns).map(a => {
          return { text: a, align: "center", value: a };
        });
        /*Object.keys(headers).map(a => {
          this.filters[headers[a].text]='';
        });*/
        return headers
      }
    }
  },
  data() {
    return {
      filters: {},
      snackbar: {
        visible: false,
        text: ""
      },
      pagination: {
        sortBy: "value"
      },
      selected: [],
      //sheet: false,
      file: null
    };
  },
  methods: {
    setReference(referenceName) {
      this.$store.dispatch("loadReference", {
        referenceName: referenceName,
        referenceDescription: this.references[referenceName]
      });
      this.filters={}
    },
    toggleAll() {
      if (this.selected.length) this.selected = [];
      else this.selected = this.referenceValue;
    },
    changeSort(column) {
      if (this.pagination.sortBy === column) {
        this.pagination.descending = !this.pagination.descending;
      } else {
        this.pagination.sortBy = column;
        this.pagination.descending = !this.pagination.descending;
      }
    },

    /**
     * Handler when user input something at the "Filter" text field.
     */
    filterSearch(event, header) {
      let params = {};
      params[header.text]=event.currentTarget.value;
      this.filters = this.$MultiFilters.updateFilters(this.filters, params);
    },
    customFilter(items, filters, filter, headers) {
      // Init the filter class.
      const cf = new this.$MultiFilters(items, filters, filter, headers);
      headers.forEach(header=>{
        cf.registerFilter(header.text, function (searchWord, items) {
          if (searchWord==null || searchWord.trim() === '') return items;
          return items.filter(item => {
            return item.refValues[header.text].toLowerCase().includes(searchWord.toLowerCase());
          }, searchWord);
        });
      })
      // Its time to run all created filters.
      // Will be executed in the order thay were defined.
      return cf.runFilters();
    },
    customSort(items, index, isDescending) {
      if (index === null) return items;
      return items.sort(function(a, b) {
        var sortA = a.refValues[index];
        var sortB = b.refValues[index];
        if (isDescending) {
          var _ref = [sortB, sortA];
          sortA = _ref[0];
          sortB = _ref[1];
        }

        if (sortA > sortB) return 1;
        if (sortA < sortB) return -1;
        return 0;
      });
    },
    handleFileUpload(event) {
      /** */
      this.file = this.$refs.file.files[0];
      if (this.file != null) {
        this.$store.dispatch("uploadReference", {
          referenceName: this.referenceName,
          file: this.file
        });
        this.resetValidation();
      }
    },
    uploadReference() {
      if (file != null) {
        this.$store.dispatch("uploadReference", this.referenceName);
        this.resetValidation();
      }
    },
    resetValidation() {
      /** */
      this.file = null;
      this.$refs.file.resetValidation;
      this.$refs.file.value = null;
    },
    pickFile() {
      this.$refs.file.value = null;
      this.$refs.file.click();
    },
    getKey(item){
      this.referenceDescription.columns
    }
  },
  components: {}
};
</script>

<style scoped lang="scss">
.references > table > tr > td {
  border: solid 1px red;
}
h3 {
  margin: 40px 0 0;
}
ul {
  list-style-type: none;
  padding: 0;
}
li {
  display: inline-block;
  margin: 0 10px;
}
a {
  color: #42b983;
}
</style>
